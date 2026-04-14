---
name: OCR / AI Receipt Capture plan
description: Subscriber feature plan for snap-or-share receipt and screenshot capture using a multimodal LLM. Primary provider Firebase AI Logic → Gemini 2.5 Flash (Anthropic Claude Haiku 4.5 as alternate). Covers architecture, costs, Android share-intent integration, the user-decided privacy model (opt-in, per-receipt exception to E2E encryption, manual entry encouraged for sensitive transactions), help/privacy-policy copy, accuracy research, and dedupe concerns.
type: project
originSessionId: e62277a3-386c-4af8-8747-78a2f79a4bee
---
A subscriber-tier feature where users either snap a photo of a paper receipt or share a screenshot from any other app (DoorDash, Amazon, Uber Eats, bank notification email, etc.), and BudgeTrak extracts merchant, amount, date, and a category hint, then creates a transaction with the image attached as the receipt photo. Originated from the 2026-04-11 brainstorm session as the strongest single subscription-conversion candidate.

## Why this is the right approach

Screenshots are *easier* than physical receipts for OCR because they're crisp, well-lit, and machine-rendered with no skew or damage. The hard part isn't recognition; it's extracting structured data from wildly different layouts (every food delivery app, every shopping app, every bank notification has its own format). Two paths:

1. **Classic OCR + per-source regex parser.** Falls apart fast — every popular app needs its own parser, parsers break when those apps change layout, maintenance is forever. Skip.
2. **Multimodal LLM directly on the image.** Hand the image to a vision-capable model with a short prompt asking for `{merchant, amount, date, category_hint, confidence}` JSON. Robust across layouts the model has never seen. This is the right answer.

**Primary provider (decided 2026-04-13): Firebase AI Logic → Gemini 2.5 Flash.** Alternate: Anthropic Claude Haiku 4.5 (kept as fallback; see "Provider decision" section below).

Per-call cost on Gemini 2.5 Flash is roughly **$0.001** (input $0.30/1M, output $2.50/1M; ~1600 input tokens for image+category list, ~200 output tokens). A user doing 50 captures/month ≈ $0.05; 200/month ≈ $0.20. Flash-Lite is ~5× cheaper again at ~$0.0002/call but has documented accuracy concerns for receipts — see below. Anthropic Haiku 4.5 would be ~$0.002–0.005/call (3–5× more). All three are easily covered by a $3–5/month subscription.

## Provider decision (2026-04-13)

Default to **Firebase AI Logic with Gemini 2.5 Flash** for v1. Reasons:

1. **Firebase AI Logic is a managed proxy** — it replaces the Cloud Function proxy we were going to build. API key stays server-side by design, no proxy code to maintain.
2. **App Check and Firebase Auth are built in** — same enforcement model we already use for Firestore/RTDB/Storage. Free abuse protection.
3. **Native Kotlin SDK** — no raw HTTP plumbing.
4. **Already on Blaze**, which is a hard requirement: on the free Spark tier Google reserves the right to use prompts/responses for training. Blaze (paid Gemini Developer API) has **no training use** and only brief abuse-detection logging.
5. **Zero Data Retention available on request** for both the Gemini Developer API and Vertex AI paths — parity with Anthropic's ZDR tier. We should request ZDR once usage justifies it.
6. **Cost is ~3× cheaper than Haiku** on 2.5 Flash, ~15× cheaper on Flash-Lite.
7. **Eliminates ~1 day** off the one-week build estimate (no Cloud Function proxy to write, deploy, or maintain).

Keep `ReceiptExtractionService` behind an interface so the Anthropic path remains swappable. If the Gemini bake-off disappoints or we hit policy friction, we can switch providers without rewriting the app-side flow.

**Why Flash and not Flash-Lite for v1:** a user on the Google AI Developers forum reported a regression in 2.5 Flash-Lite vs 2.0 — character confusion ("e" → "ë"/"è") and worse instruction-following on tabular data. Anecdotal and unreproduced, but receipts are exactly where that bites (merchant names and line-item tables). Flash is still 3× cheaper than Haiku — not worth saving another $0.0008/call for a known accuracy risk on the one feature we're selling.

### Receipt OCR accuracy research (2026-04-13)

Light public data on Flash-class models specifically for receipts:

- **Gemini 2.5 Pro on Scanned Receipts: 87.46% accuracy** (arxiv 2509.04469 — the only published receipt-specific benchmark found). Flash is below Pro, so expect 80–85% range for receipts; Flash-Lite lower still.
- Gemini Flash-class on scanned documents generally: 88–94% across public benchmarks, matches or beats specialized OCR.
- Medium tutorials using `gemini-2.0-flash` for structured receipt extraction (Pydantic schema for merchant/amount/date/category) report qualitative success on MVP samples but no accuracy numbers.
- **No rigorous public benchmark for 2.5 Flash-Lite on receipts.**

**Bake-off is essential before shipping.** Assemble ~30 sample receipts across thermal paper, crumpled, faded, non-English, and screenshot sources. Run through 2.5 Flash and Haiku 4.5 side-by-side. Measure field-level accuracy (merchant exact-match lenient, amount ±$0.01 exact, date exact, category top-1). Ship whichever wins; keep the loser wired as the swap-in alternate.

## Privacy model (decided 2026-04-11)

User's framing, which we should follow:

- **Opt-in only.** A clear toggle in Settings (default off) and a first-run consent dialog the very first time the user tries to use the feature. The dialog must explicitly say: "The image you're about to capture will leave your device and be sent to a third-party AI service for processing. Your other transaction data is unaffected and remains end-to-end encrypted."
- **Per-receipt exception, not a blanket reduction.** This is the key framing. BudgeTrak's overall privacy story is unchanged — SYNC is still end-to-end encrypted, the cloud server still cannot read your transaction data, your bank balances and merchant names are still encrypted at rest and in transit. The OCR feature is a deliberate, scoped exception: *individual images you choose to send* go to the AI service. Nothing else changes.
- **Encourage manual entry for sensitive transactions.** The consent dialog and the Settings page should both gently suggest: "For purchases you'd rather not send to a third party — medical, legal, personal — just enter them manually as you do today. The OCR feature is here to save you time on everyday receipts, not to handle every transaction."
- **The paid Gemini Developer API (Blaze plan) does not train on customer data** and logs prompts only briefly for abuse detection — this is the right thing to put in the privacy policy. Zero Data Retention can be requested once usage justifies it. If we ever swap to the Anthropic alternate, equivalent guarantees apply (no training by default; ZDR tier available). We should still be explicit that the image is processed by a third-party AI and not stored long-term by us beyond the local receipt photo attachment.
- The image itself stays attached locally (and optionally syncs via the existing encrypted receipt-photo pipeline). Only the *processing call* leaves the device — the persistent photo attachment is still on the encrypted side of the line.

## Android implementation outline

**Share intent integration (the screenshot half).**
- Add an `<intent-filter>` to `MainActivity` in `AndroidManifest.xml` for `ACTION_SEND` with mime types `image/*`, `image/png`, `image/jpeg`. (Could also add `ACTION_SEND_MULTIPLE` later if we want to handle multi-image shares, but v1 is single image only.)
- BudgeTrak will then appear in the system share sheet whenever any app shares an image — screenshots, gallery, browser image-save, third-party apps, etc.
- `MainActivity.onCreate` and `onNewIntent` need a handler that detects the SEND intent, reads the image URI from `Intent.EXTRA_STREAM`, and routes it into the OCR flow. Set a transient `vm.pendingOcrImageUri` so the existing dataLoaded gate still applies — the OCR flow can't run before the ViewModel finishes loading.

**Capture flow (the photo half).**
- Add an "OCR Capture" entry point alongside the existing receipt-photo button. Today the receipt-photo button takes a photo and attaches it to a manually-entered transaction; the new entry point takes the same photo and runs it through extraction first, then opens TransactionDialog with the fields prefilled.
- Both entry points (camera and share-intent) converge on the same `vm.startOcrExtraction(uri)` call.

**Extraction pipeline (`ReceiptExtractionService`).**
- Persist the image to a new `ReceiptManager` slot first: `val receiptId = ReceiptManager.generateReceiptId(); val file = ReceiptManager.getReceiptFile(context, receiptId); copy URI bytes to file`. This means the photo is preserved on disk regardless of whether the API call succeeds — if the user cancels mid-flow, the orphan-receipt cleanup will sweep it later.
- Strip EXIF metadata before sending (no GPS, no device info, no timestamps). Re-encode as JPEG at quality 85 if the source is much larger than ~1.5MB to keep API costs and latency down.
- Call the model via the **Firebase AI Logic Kotlin SDK** (`com.google.firebase:firebase-ai`). Firebase AI Logic is itself a managed proxy: the API key stays on Google's side, App Check is enforced at the edge, and Firebase Auth identity is attached automatically. No self-hosted Cloud Function needed for the call itself.
- **Quota enforcement stays ours.** Firebase AI Logic does not enforce app-level per-user monthly caps. We implement the counter in Firestore (`users/{uid}/ocrUsage/{YYYYMM}` or on the group doc for SYNC) with an atomic increment *before* the model call; decrement on API failure. For solo users (who currently have no Firebase Auth session), we gate OCR behind anonymous Auth + App Check — the SDK requires Auth anyway.
- **Alternate provider path (Anthropic Claude Haiku 4.5)**: if the bake-off favors Anthropic, route through a thin Cloud Function proxy instead. `ReceiptExtractionService` should be an interface with `GeminiExtractor` and `AnthropicExtractor` implementations so the swap is a one-line change at the injection site.
- Parse the structured JSON response. Sanity checks: amount must be positive; date must be within ±60 days of today; if `categoryAmounts` is returned, the sum must equal `amount` within $0.01 tolerance.
- On parse success, build a `Transaction` object with `receiptId1 = receiptId`, the extracted fields populated, `isUserCategorized = false` (so it shows as unverified until the user confirms), and call into the existing transaction-add pipeline.

**Where it plugs into existing code.**

The existing transaction-add pipeline has a clean three-step entry that OCR can call into directly. Confirmed entry points:

| Step | Function | Behavior |
|---|---|---|
| 1. Top of pipeline | `vm.runMatchingChain(txn)` (`MainViewModel.kt:1069`) | Runs duplicate detection. If dupes found, shows dupe dialog. Otherwise → step 2. |
| 2. Linking | `vm.runLinkingChain(txn)` (`MainViewModel.kt:1017`) | Checks RE / amortization / income source matches. If matches found, shows match dialog. Otherwise → step 3. |
| 3. Final commit | `vm.addTransactionWithBudgetEffect(txn)` (`MainViewModel.kt:989`) | Applies savings goal deductions, adds to `transactions` list, calls `saveTransactions(listOf(stamped))`, recomputes cash, triggers archive check if over threshold. |

OCR calls **`runMatchingChain(txn)`** at the top of the chain — never the lower-level functions directly. This means OCR transactions go through the same dedupe and matching as manually-entered ones for free, including the duplicate dialog if the user is also using bank import. Sync push, encryption, period ledger, recompute cash — all happen automatically downstream because they're all called from `addTransactionWithBudgetEffect` and `saveTransactions`.

For the **confirmation UI**, the existing `TransactionDialog` (in `TransactionsScreen.kt`) already supports everything OCR needs:
- Single category transactions: just open the dialog with `editTransaction` set to the OCR-prefilled transaction.
- Multi-category transactions: the dialog already has a `showPieChart` toggle (line 3980) that switches between `PieChartEditor` (visual drag-to-resize across selected categories) and per-category text fields. We can pre-set the selected categories from `categoryAmounts` and let the user edit either way.
- Receipt photo: already has the `receiptId1..5` slots wired through. OCR just sets `receiptId1 = <the new UUID>` on the prefilled transaction and the dialog displays the photo with the existing photo-display widget.

The **category list** for the prompt comes straight from `vm.categories` (`MainViewModel.kt:189`), which is `mutableStateListOf<Category>()` and already filtered to active (non-deleted) categories elsewhere in the codebase. The `Category` data class is `{id: Int, name: String, iconName: String, tag: String, charted: Boolean, widgetVisible: Boolean, ...}`. Send `{id, name, tag}` to Haiku — `tag` is the user's optional disambiguation field (e.g., "kids" vs "adults" school supplies) and improves multi-category accuracy when present.

**New code required (estimate by file):**

| File | Type | Purpose |
|---|---|---|
| `data/sync/ReceiptExtractionService.kt` | New | API client, prompt builder, JSON parser, sanity checks, EXIF stripping, image re-encoding |
| `MainViewModel.kt` | Edit | Add `pendingOcrImageUri` state; add `startOcrExtraction(uri)` function that wires extraction → `runMatchingChain` |
| `MainActivity.kt` | Edit | Add SEND intent handler in `onCreate` and `onNewIntent`; route to `vm.startOcrExtraction()` after dataLoaded gate |
| `AndroidManifest.xml` | Edit | Add `<intent-filter>` for `ACTION_SEND` with `image/*` mime type |
| `ui/screens/SettingsScreen.kt` | Edit | Add OCR opt-in toggle; add free-tier counter display ("3 of 5 captures used this month") |
| `ui/screens/TransactionsScreen.kt` | Edit | Add an "OCR Capture" button next to the existing receipt-photo entry point in the add-transaction flow |
| First-run consent dialog | New | Composable shown the first time the user enables OCR; explains the privacy exception |
| `build.gradle` (app) | Edit | Add `com.google.firebase:firebase-ai` SDK dependency |
| Firestore rules | Edit | `users/{uid}/ocrUsage/{YYYYMM}` — user can read own counter, only callable update path (or Cloud Function) can increment |
| `functions/ocr-quota.js` (optional) | New | Only if we want server-enforced counter increments instead of rules-gated client writes. Required if we switch to the Anthropic alternate — that path still needs a full proxy (auth, quota, Anthropic API call, response forwarding) |
| `EnglishStrings.kt`, `SpanishStrings.kt`, `TranslationContext.kt`, `AppStrings.kt` | Edit | All the new user-facing strings (consent dialog, settings toggle, counter labels, error messages, paywall message) |

## Dedupe concerns

If a user is also using bank import or any future automated source, the same transaction will arrive twice: once from the screenshot capture and once from the bank statement a few days later. The existing duplicate detection should catch most of this since amount and date will match within tolerance, but:
- Dedupe window matters — bank statements often post a few days after the actual purchase, so the existing window may need to widen.
- The merchant string from OCR ("Chipotle") may not match the bank's posting ("CHIPOTLE 1234 AUSTIN TX"). The existing merchant normalization (`matchChars` setting, alphanumeric stripping) should mostly handle this.
- The duplicate-detection dialog should prefer the OCR'd entry (it has the receipt photo attached) and offer to merge the bank-import entry into it rather than keep both.

## Cost and effort estimates

- Per-call cost: ~$0.002–0.005 with Claude Haiku 4.5 vision.
- Per-user monthly cost at heavy use (200 captures): under $1.
- Engineering effort: roughly one week solo, broken down approximately as: half day for the share-intent manifest + receiving activity, half day for the API client and prompt design (or proxy Cloud Function), half day for the confirmation dialog wiring into TransactionDialog, half day for dedupe integration, one to two days for edge cases (low confidence, API errors, image too large, network failures, opt-in flow), and a day for the consent UI and Settings page changes.

## Free-tier teaser

Free and paid (non-subscriber) users get **5 OCR captures per month** as a teaser. This was decided 2026-04-11 — the Plaid playbook says teasers convert well, and 5/month is enough for a user to feel the magic of the feature without giving away the value entirely. Heavy users (the ones most likely to convert) will hit the limit fast and be prompted to subscribe.

Implementation notes:
- Counter is per-user, per-calendar-month, stored in SharedPreferences for solo users and in `SharedSettings` for sync groups (so the limit is enforced across linked devices).
- Reset on the 1st of each month (UTC, or the user's group timezone if SYNC).
- When a free user runs out, the capture flow should show a clear paywall: "You've used your 5 free receipt captures this month. Subscribe to get unlimited, or wait until next month." Not a hostile dialog — make it feel like a fair tradeoff.
- Subscribers have no limit (within reason — we should still rate-limit at the API proxy level to catch bugs or abuse, maybe 100 captures/day hard cap).
- Counter increments on successful API response, not on the attempt — failed extractions don't burn a credit.

## Category-aware extraction

Pass the user's current category list into the Haiku prompt so it can pick from the actual categories the user has rather than guessing a generic label that we then map heuristically. Concrete approach:

- The prompt includes a JSON array of category names (and optionally a one-line description of each, if the user has set one). Typical user has 30–50 categories, which is 200–400 extra input tokens — negligible cost impact.
- Haiku is told: "Pick the single best category from the list. If none fit, return null and we'll fall back to AutoCategorizer."
- For sync groups, the category list is shared, so the same prompt works on every device.
- This replaces the fallback chain we had originally sketched (Haiku returns a generic `category_hint`, then `AutoCategorizer` translates it). With category-aware extraction, Haiku does the categorization in one shot and AutoCategorizer is only the safety net for the rare null case.

## Multi-category receipts (split transactions)

**This is already fully built into BudgeTrak** — `Transaction.categoryAmounts: List<CategoryAmount>` (where `CategoryAmount` is `{categoryId, amount}`) supports any number of category splits per transaction, and the entire stack already handles it: encryption (`enc_categoryAmounts` in `EncryptedDocSerializer`), sync (`SyncMergeProcessor`), the dedicated `PieChartEditor.kt` component (~470 lines, with multiple color palettes and gesture-based editing), the transaction list rendering (`TransactionsScreen.kt` has `hasMultipleCategories` flag handling and per-category row display), category filtering, the dashboard pie chart, CSV import, the auto-categorizer, the full-backup serializer, and the PDF expense report generator.

OCR's job is simply to populate `categoryAmounts` correctly when Haiku returns a multi-category breakdown. Everything downstream — display, edit, sync, budget math, charts, reports — already works. No data model change needed.

**Haiku prompt for multi-category:**

The prompt asks Haiku to return either a single `category` field OR a `categoryAmounts` array of `{categoryId, amount}` objects, depending on whether the receipt contains items from clearly different categories. We don't hardcode the heuristic — Haiku decides based on the line items it reads. Sample output for a Chipotle receipt: `{merchant: "Chipotle", date: "2026-04-11", amount: 24.50, categoryAmounts: [{categoryId: 12, amount: 24.50}]}` (single split). Sample output for a Target receipt: `{merchant: "Target", date: "2026-04-11", amount: 80.42, categoryAmounts: [{categoryId: 3, amount: 45.20}, {categoryId: 18, amount: 19.80}, {categoryId: 22, amount: 15.42}]}`. The sum of split amounts must equal the receipt total; we sanity-check this on parse and reject the response if it's off by more than a cent.

Because the user's actual category list (with categoryId values) is already passed into the prompt for the category-aware extraction step, Haiku has everything it needs to assign IDs directly — no post-processing translation step required.

The confirmation flow uses the existing transaction edit dialog and the existing `PieChartEditor` for multi-split cases — both are already in the codebase. OCR is just a new entry point into the existing UI.

Cost impact of multi-category: slightly larger output (an array instead of a single field), and possibly a slightly more detailed prompt. Per-call cost stays roughly $0.003–0.008. Still cheap, still well within subscription economics.

## Implementation status (2026-04-14)

### Harness benchmark — locked 2026-04-14 (v0.4)

Production model decision: **Gemini 2.5 Flash via Firebase AI Logic** with prompt v0.4. Benchmarked on 103 labeled receipts (3 internal screenshots + 50 SROIE English + 50 MC-OCR Vietnamese) in `tools/ocr-harness/`.

Flash v0.4 final scores (after label audit):

| Source | merchant | date | amount | category |
|---|---|---|---|---|
| English (53) | 98.1% | 100% | **100%** | 83.0% |
| Vietnamese (50) | 90.0% | 100% | **100%** | 94.0% |
| avg latency | — | — | 4.8s | — |

Flash-Lite v0.4 for comparison (not shipping):

| Source | merchant | date | amount | category |
|---|---|---|---|---|
| English (53) | 92.5% | 96.2% | **88.7%** | 79.2% |
| Vietnamese (50) | 44.0% | 98.0% | **80.0%** | 82.0% |
| avg latency | — | — | 1.4s | — |

Flash-Lite is **5× cheaper and 3× faster** but loses 11pt on English amount (the field we can least afford to miss on) due to smaller-model character confusion (`169.8` vs `169.78`, `5.15` vs `5.2`). On Vietnamese it additionally hallucinates on diacritic-heavy merchants (`Thức` → `TH\nuevoC`). Deferred as a post-launch cost optimization via smart-reask; see "Smart-reask in production" below.

Harness location: `tools/ocr-harness/`. Prompt v0.4 text in `src/prompt.js` is the canonical version — port directly to Kotlin. Schema in `src/schema.js` (merchant, merchantLegalName optional, date, amount required; categoryAmounts/lineItems/notes optional).

Session cost: ~$0.75 across 7 full 103-receipt runs + smoke tests. Full production-run budget is negligible — 100 prompt iterations = ~$10.

### Android side: v0 skeleton merged on `dev`

Share-intent routing + placeholder AI icon shipped 2026-04-14 (commits `03e10cd`/`53a8616`). No AI extractor wired yet; icon is cosmetic.

**Landed:**
- `AndroidManifest.xml` — ACTION_SEND `image/*` intent-filter on MainActivity.
- `MainActivity.onNewIntent` + cold-start extractor `extractSharedImageUri()`; gated `LaunchedEffect` consumes `pendingSharedImageUri` once `dataLoaded` is true.
- `MainViewModel.consumePendingSharedImage(canAttachPhotos)` — runs in `viewModelScope` (not the caller's LaunchedEffect, which would cancel on state write). Free users: dialog still opens, photo discarded, 5-second upgrade toast via `sharedPhotoBlockedToastPending` flag. Paid/Subscriber: `ReceiptManager.processAndSavePhoto` on IO, then `pendingSharedReceiptId = rid`, then `dashboardShowAddExpense = true` (order matters — see feedback memory).
- `TransactionDialog` signature gained `isSubscriber` and `initialReceiptId1`. `addModeReceiptId1` is seeded from the latter on first composition; the id is also added to `addedPhotoIds` so a dismiss-without-save cleans up the orphan.
- `AutoAwesome` placeholder icon in the dialog header, in a Row alongside the existing camera icon. Subscribers → full color + "coming soon" toast. Non-subscribers (Free AND Paid) → greyed + upgrade toast. Gated on `vm.isSubscriber` only.
- Strings added (EN/ES/AppStrings/TranslationContext): `aiOcrIconDesc`, `aiOcrComingSoon`, `upgradeForAiOcr`, `sharedImageProcessFailed`, `sharedPhotoNeedsUpgrade` (5-second free-user toast).

**Not yet built (next up):**
- `ReceiptExtractionService` (Firebase AI Logic → Gemini 2.5 Flash). Use the `com.google.firebase:firebase-ai` SDK; port prompt v0.4 text from the harness.
- `responseSchema` passed to the `GenerativeModel.generateContent` call. Schema shape matches `tools/ocr-harness/src/schema.js`.
- Prompt loaded from Firebase Remote Config (key pattern: `ocr_receipt_prompt_v{N}`). Stamp `promptVersion` into each transaction's local provenance for A/B traceability.
- Firestore quota counter `users/{uid}/ocrUsage/{YYYYMM}` with atomic increment before model call; decrement on failure.
- Subscriber path replaces the placeholder "coming soon" toast with `vm.startOcrExtraction(uri)` → `ReceiptExtractionService` → `runMatchingChain(txn)`.
- Consent dialog on first OCR use (copy draft in "Post-implementation copy" section).
- Smart-reask path for schema-gap and format-anomaly cases (see "Smart-reask in production" below). Start with schema-gap only; add format-anomaly after launch if needed.
- ~~Synthetic regression test set~~ **DONE** — 103 labeled receipts in `tools/ocr-harness/test-data/`.
- ~~Gemini ↔ Anthropic bake-off~~ **DEFERRED** — Flash is strong enough that the comparison isn't needed pre-launch. Anthropic kept as a swap-in alternate via the `ReceiptExtractionService` interface.

**Design decisions baked in (not to revisit without reason):**
- Icon scope: Transaction add/edit dialog only (4 callsites — Dashboard Add Income + Add Expense, Transactions screen Add Income + Add Expense, plus the Edit variant). Not added to Recurring Expense, Savings Goal, Amortization, or Category dialogs.
- Share-intent only lands in Add Expense (not Add Income). User confirmed 2026-04-13.
- Subscriber gate uses `isSubscriber` only (Paid tier sees the upgrade toast too). User confirmed 2026-04-13.
- Free users always see the Add Expense dialog on share, even though they can't attach the photo — keeps the flow consistent and gives them a path to record the transaction manually.
- **Vietnamese deferred to post-launch.** Flash already does 90/100/100/94 on Vietnamese but we're US-English first. Localization adds in parallel after launch is stable.
- **Flash-Lite deferred to post-launch cost optimization.** Smart-reask targeting small-digit numeric confusion may close the gap; revisit when OCR usage is high enough for the 5× cost savings to matter.

## Smart-reask in production (how it works without labels)

In the harness we detect mismatches by comparing to known labels. In production there are no labels — so smart-reask must work from signals available in the extraction response itself. Four tiers of signal, in decreasing reliability:

**Tier 1 — Schema gaps (most reliable).** Required fields that came back null/empty/missing. Objective, no interpretation needed.
- `merchant` is missing or empty string.
- `amount` is null, zero, negative, or non-finite.
- `date` is null or fails `YYYY-MM-DD` regex.

If any required field is missing, fire a focused re-ask: "Look at this receipt again and return ONLY the {field}. Here's what you already extracted successfully: {other fields}."

**Tier 2 — Rule violations (objective, model-independent).** The response is structurally valid but violates a sanity check.
- `date` is outside ±60 days of today.
- `categoryAmounts` present but sum ≠ `amount` within $0.01.
- `categoryAmounts` references a `categoryId` not in the user's list.
- `amount` has more than 2 decimal places when currency is USD/EUR/GBP (precision mismatch).

Re-ask with the specific rule violation called out: "Your last response had categoryAmounts summing to $45.20 but amount was $43.45. Check which is correct and return a consistent breakdown."

**Tier 3 — Format anomalies (locale-dependent heuristics).** The response looks suspicious given the receipt's apparent locale.
- Amount has 3+ decimal places AND merchant/language suggests VND → likely 1000× thousand-separator bug. Example detection: `amount.toString().split(".")[1]?.length >= 3 && isVietnameseContext`.
- Amount ≤ $1.00 for a receipt that should reasonably be larger (e.g., a supermarket receipt with multiple line items but amount = $0.10) → probably a digit miss.
- Merchant name contains ASCII-nonsense sequences (`nuevoC`, random Latin characters in a Vietnamese merchant) → hallucination.

Re-ask with the suspected issue highlighted: "You returned amount=11.616 on a Vietnamese receipt. Dots in VND are thousand separators, so this is likely 11,616 not 11.616. Re-read the total and return the integer đồng value."

**Tier 4 — Self-reported confidence (least reliable).** Ask the model to include an optional `confidence: {merchant, date, amount}` object with 0–1 values. Models are overconfident — a claim of `0.95` often means `0.7` actual. Use as a secondary signal only: trigger a re-ask only if confidence is below `0.6` AND no higher-tier signal fired. Skip entirely on the Anthropic alternate path since it's more expensive per call.

**Architecture:**
1. First extraction call (always).
2. Response hits anomaly detector — tiers 1 → 2 → 3 → 4 in order, stop at first match.
3. If any tier fires, make ONE focused re-ask (cap at one; no recursion).
4. Merge: keep the first call's fields for anything that wasn't flagged; take the re-ask's value for flagged fields only.
5. If the re-ask still has a tier-1 schema gap, fall back to manual entry — drop the user into `TransactionDialog` with whatever was extracted pre-filled and the photo attached.
6. Log `{triggered_tier, field, before, after}` to a local diagnostics file (and to Crashlytics in release if the user has analytics opted-in) so we can tune the heuristics.

**Why this matters for Flash-Lite later:** of Flash-Lite's 17 amount misses, 12 are the Vietnamese 1000× bug (Tier 3 detectable, single re-ask fixes). Of the remaining 5, two are tiny digit confusion (`169.78` vs `169.8` — borderline, probably not worth re-asking). So Tier 3 smart-reask could lift Flash-Lite English amount from 88.7% to ~97% and Vietnamese amount from 80% to ~94%. At 2× average cost (same re-ask rate as Flash), Flash-Lite+reask would cost ~$0.0004/call — still 2.5× cheaper than Flash.

**What NOT to smart-reask:**
- Merchant convention differences (`VinCommerce` vs `VinMart`) — these look wrong to a dumb comparator but are actually the prompt working correctly. Don't detect, don't re-ask.
- Diacritic variations that normalize to the same string — pass a lenient merchant match first before triggering a re-ask.
- Low-confidence on `categoryAmounts` alone — just fall back to a single-entry `[{categoryId: auto-matched-id, amount: total}]`. Re-asking on categorization is expensive for marginal gain; `AutoCategorizer` already exists as a safety net.

**Post-launch feedback loop (v1.5).** The opt-in "Help improve Receipt AI" toggle we designed will give us real ground truth from user edits. If a user consistently changes the amount from what Gemini returned, we learn which failure patterns actually matter in the wild and can tune the Tier 3 heuristics accordingly. That replaces synthetic label audits as the primary feedback mechanism once production traffic exists.

## Prompt iteration strategy (2026-04-14)

The prompt is the highest-leverage lever in this whole feature. Design so it can be iterated without app updates, measured against a real test set, and improved from user-observed failures.

### Required from v1

**1. Structured output via `responseSchema`.** Gemini (via Firebase AI Logic) supports constrained JSON output — pass a schema and the model is forced to match. Eliminates ~90% of parse failures (no missing fields, no stray markdown fences). Define the schema alongside the data class:

```
{
  merchant: string,
  date: string (YYYY-MM-DD),
  amount: number,
  categoryAmounts: [{categoryId: int, amount: number}],
  lineItems: [string]?,
  confidence: { merchant: number, date: number, amount: number, category: number },
  notes: string?
}
```

**2. Prompt lives in Firebase Remote Config, not hardcoded.** Key: `ocr_receipt_prompt_v{N}`. The app fetches on launch and caches. Pushing a new prompt is a console change, not an app release. Stamp `promptVersion` into every call and log it alongside outcomes so regressions are traceable.

**3. Category list passed per call.** Send `[{id, name, tag, description?}]` filtered to non-deleted categories. Gemini picks IDs directly — no post-processing translation. ~200–400 tokens, negligible cost.

**4. Provider stamp.** Each extraction result carries `{provider: "gemini-2.5-flash" | "haiku-4.5", promptVersion: "v3"}` so we can A/B across provider *and* prompt dimensions.

### Prompt content levers (iterate over time)

- **Per-layout directives** — "If this looks like a food-delivery receipt (DoorDash, Uber Eats, Grubhub), the merchant is the restaurant, not the delivery app; put the delivery app in `notes`."
- **Edge-case coaching** — "Receipts often print the total twice; use the final printed total, not the subtotal. Ignore tip suggestion lines unless a tip was written in."
- **Few-shot examples** for recurring problem categories — one each for a split-category Target run, a Costco warehouse receipt, a restaurant with tip line, a refund / return (negative amount).
- **Language hints** — "If the receipt is in Spanish, extract fields in their original form; do not translate merchant names."
- **Amount reconciliation** — "If `categoryAmounts` is populated, its sum must equal `amount` to the cent. If you can't make them balance, return a single category instead."

### Schema-gap re-ask (primary re-ask mechanism)

The most reliable re-ask trigger is **objective schema validation**, not self-reported confidence. Self-reported `confidence` numbers are unreliable — the model will happily say `0.95` on a hallucinated date. "Field came back null or failed a sanity check" is a signal we can trust.

**Schema design.** Mark only the fields we absolutely need as required — `merchant`, `date`, `amount`. Make everything else optional (`categoryAmounts`, `lineItems`, `notes`, `confidence`). Optional fields let the model *omit* rather than guess, which is exactly what we want.

**Gap detection after first call.** A field is considered "missing" if it is:
- Absent / null / empty string (merchant, date, notes).
- Zero, negative, or non-finite (amount).
- Date outside ±60 days of today.
- `categoryAmounts` present but sum ≠ `amount` within $0.01 (treat as gap on categoryAmounts only; keep the top-level amount).
- `categoryAmounts` references a `categoryId` not in the user's list.

**Re-ask with context.** Second call is focused and includes what was extracted successfully. Example:

> "Earlier you extracted from this image: merchant='Chipotle', amount=24.50, but you did not return a date. Look at the image again and return **only** the transaction date as YYYY-MM-DD. Dates on US receipts commonly appear at the top of the header or above the total line; formats include MM/DD/YYYY, MM-DD-YY, or 'Mar 14, 2026'. If the date is genuinely not visible, return null."

Second-call response is merged into the first-call result: fields from the focused response replace the missing fields only; fields from the first call are preserved. This avoids drift on the things we already got right.

**Caps.** One re-ask per extraction (two total calls). If the re-ask still leaves a required field missing, drop the user into TransactionDialog with whatever *was* extracted prefilled and the photo attached — fallback to manual entry with the photo as context. Never loop indefinitely.

**Cost.** On Gemini 2.5 Flash the re-ask is ~$0.0005 extra (smaller prompt than the first call), so a 15% re-ask rate adds ~$0.00008 to the average call — negligible. On the Anthropic alternate path it's ~$0.002 extra per re-ask, still acceptable but trim the re-ask frequency by making `confidence` optional and skipping the confidence-gate path (below).

### Confidence-gated re-ask (secondary, Gemini only)

As a belt-and-suspenders second signal, ask the model to include optional per-field `confidence` values in the schema. If the schema-gap check passes but any `confidence.*` is below 0.6, run the same focused re-ask path. Keep this path **off on the Anthropic alternate** to control costs. Skip entirely if we observe in the bake-off that confidence scores correlate poorly with actual accuracy.

### UPC codes and line items — honest framing

- Most US paper receipts don't print full UPCs — they print internal SKUs and truncated names ("GV MILK 1GAL"). That text is what Gemini actually reads.
- Where UPCs do appear (receipts with barcodes, product-page screenshots), Gemini reads the digits fine.
- **But Gemini has no live product database** — asking "categorize UPC 012000161155" yields trained-memory guesses and occasional confident hallucinations. Don't make UPCs a primary categorization signal.
- v1 directive: *"Use any visible line-item text — product names, category codes, SKU descriptions — to inform categorization. UPCs are fine to include in `lineItems` if legible but do not guess products from a UPC alone."*
- v2 option: external UPC → product lookup (Open Food Facts, UPCitemdb) as a Cloud Function post-process. Out of scope for launch.

### Measurement — synthetic test set (build day 1)

- Assemble 50–100 manually-labeled receipts covering: clean thermal, crumpled, faded, non-English, screenshots (DoorDash / Amazon / Uber Eats / bank app), multi-category big-box, refund, tip-line restaurant, gift card purchase.
- Ground truth for each: merchant, date, amount, correct category (mapped to our category list), split categories where applicable.
- Regression harness runs the current prompt + candidate prompt against the full set, reports per-field accuracy (merchant fuzzy-match, amount ±$0.01, date exact, category top-1). No prompt ships without passing the harness.
- This is also the bake-off mechanism for Gemini vs Anthropic.

### Test data sources

Don't build the full test set by hand — borrow established public receipt datasets for the scanned-paper half, and build the screenshot half ourselves.

**Public datasets (use for scanned-paper coverage):**

| Dataset | Size | Labels | License | Fit |
|---|---|---|---|---|
| [SROIE (ICDAR 2019)](https://huggingface.co/datasets/priyank-m/SROIE_2019_text_recognition) | ~1,000 English receipts | company, date, address, total | Research use — verify before shipping, fine for internal benchmark | **Primary.** Fields map cleanly to our merchant/date/amount. Standard OCR benchmark. |
| [CORD](https://github.com/clovaai/cord) | 11,000+ Indonesian receipts | bounding boxes + per-line-item semantic labels | CC BY 4.0 | **Best for multi-category testing** — line-item labels stress `categoryAmounts`. Language mismatch for v1 launch but valuable for future Spanish / multilingual. |
| [ReceiptSense (2024)](https://arxiv.org/html/2406.04493v2) | 20,000 receipts + 10,000 item-level annotations | item-level + QA pairs | Check paper | Newest/largest. Skim the paper to confirm annotation alignment before pulling. |
| [Voxel51/consolidated_receipt_dataset](https://huggingface.co/datasets/Voxel51/consolidated_receipt_dataset) | Aggregates multiple above | varies | Per-source | Easier single-download starting point if we want one blob. |

**Internal dataset (must build ourselves — ~30 items):** app screenshots. No public dataset covers DoorDash, Uber Eats, Amazon order pages, Instacart, bank-notification emails — exactly the "share a screenshot" half of BudgeTrak's pitch. Easy to collect on our own devices and quick to label since the source apps render the values clearly. Store in repo under `tools/ocr-test-data/screenshots/` with a `labels.json` sibling.

**Harness format:** normalize public + internal into one schema — `{image_path, merchant, date (YYYY-MM-DD), amount, categoryAmounts?, notes}` — so the regression runner treats them identically. Write a one-off converter per public source; check in the converted labels, not the original dataset (keep public data as a pinned HF dependency or download script).

**Handling rules:**
- Test data never leaves our controlled environment. Public datasets are already public; internal screenshots stay in the repo (or a private GCS bucket if we decide not to commit them).
- Never use real user receipts as test data, even with consent, unless the opt-in "Help improve Receipt AI" feedback loop is shipped and the user has explicitly agreed.
- Tag each test item with `{source: "SROIE" | "CORD" | "internal-screenshots", difficulty: "easy|medium|hard"}` so we can track per-segment accuracy separately — Flash may crush thermal paper but stumble on screenshots, or vice versa.

### Measurement — live feedback loop

- **Debug builds (day 1):** dump `{image, prompt, response, user-edited final transaction}` tuples to the existing diagnostics pipeline. Zero user-privacy surface. Use for our own dogfooding.
- **Release builds (v1.5):** opt-in "Help improve Receipt AI" toggle, off by default. When on, if the user edits an AI-prefilled transaction before saving, offer to send the image + diff (AI's answer vs user's correction) to us. Requires a second consent surface — explicit "this specific image + your correction will be sent to help us train prompts. The image is not sent to the AI provider again." Budget as a follow-up release, not launch.
- **Success metric:** per-field edit rate. If the user accepts merchant unedited 85% of the time, merchant prompt is good. If date edits spike after a prompt change, roll back in Remote Config.

### A/B testing via Remote Config

Remote Config experiments support variant assignment. Run prompt v3 vs v4 at 50/50, stamp `promptVersion` on each transaction, compare edit rates over a 7-day window. Low-risk, fast-feedback — the reason we put the prompt in Remote Config in the first place.

### What NOT to tune in the prompt

- Don't put privacy or legal language in the prompt — the model can ignore it and it's not a legal shield anyway. Privacy lives in the consent dialog and policy.
- Don't put the user's identity, email, or other transaction data into the prompt for "context." The whole privacy pitch is that we send only the image + category list.
- Don't ask the model to "be creative" or "make a best guess" on low-confidence fields in the primary prompt — that's what the re-ask path is for, with a targeted follow-up.

## Post-implementation copy (help file + privacy policy)

Draft language to drop into the help file and privacy policy once the feature ships. Written to be truthful, plain-English, and defensible — we can advertise encrypted transit (TLS 1.3) and no-retention processing without overclaiming E2E.

### Help file section — "How Receipt AI keeps your data private"

> **Receipt AI** lets you snap a photo of a receipt or share a screenshot from another app, and BudgeTrak fills in the merchant, amount, date, and category for you. Here's exactly what happens to that image:
>
> **On your phone, before sending.** BudgeTrak strips location data and device metadata (EXIF) from the image so none of it leaves your device. The image is then encrypted using the same standard your bank uses — TLS 1.3 with forward secrecy — before a single byte leaves your phone.
>
> **In transit.** Even on an open or public wifi network, no one on that network can read the image. They can only see that your phone contacted our server. The encryption happens on your device and is only unwrapped at the destination.
>
> **At the AI service.** The image is processed by Google's Gemini AI (via Firebase AI Logic) to extract the receipt details. Under Google's paid API terms, the image is **not used to train AI models** and is **only briefly logged for abuse detection** before being discarded. Google does not receive your identity, account name, or any other transaction data — only the single image and the list of your categories so it can pick the right one.
>
> **After processing.** The extracted details are sent back to your phone (again over encrypted TLS). The receipt photo itself stays on your device as an attachment to the transaction, protected by BudgeTrak's normal encrypted receipt pipeline if you use SYNC.
>
> **What's opt-in and what isn't.** Receipt AI is off by default. The rest of BudgeTrak SYNC is unchanged — your transactions, balances, and merchant names remain end-to-end encrypted and unreadable to us or anyone else. For purchases you'd rather not send to a third-party AI — medical, legal, or personal — just enter them manually the way you always have. Receipt AI is here to save you time, not to handle every transaction.

### Privacy policy section — "Receipt AI (optional feature)"

> **What it does.** If you enable Receipt AI (off by default), BudgeTrak can send a receipt image you capture or share to Google's Gemini AI, accessed through Firebase AI Logic, for the sole purpose of extracting merchant, amount, date, and category. You will be asked to confirm the first time you use the feature.
>
> **What we send.** The receipt image only, with EXIF metadata (GPS, device, timestamps) removed before sending, along with a list of your category names so the AI can assign the transaction correctly. We do **not** send your name, email, account ID, device identifiers, location, or any other transaction data.
>
> **How it's transmitted.** All traffic uses TLS 1.3 encryption with forward secrecy and certificate pinning. The image is encrypted on your device before leaving it and cannot be read by anyone intercepting network traffic, including on public wifi.
>
> **Who processes it.** Google LLC, via the Gemini API accessed through Firebase AI Logic. Because BudgeTrak uses Google's paid API tier, your image is **not used to train AI models** and is logged only briefly for abuse detection before being discarded. See Google's Gemini API terms at https://ai.google.dev/gemini-api/terms and Firebase AI Logic data governance at https://firebase.google.com/docs/ai-logic/data-governance for details.
>
> **What we retain.** We do not store the receipt image on our servers. The extracted text fields are returned to your device and stored as part of the transaction, subject to your existing SYNC encryption settings. If you use SYNC, the receipt photo itself is stored end-to-end encrypted; we cannot read it.
>
> **Your controls.** You can disable Receipt AI at any time in Settings → Privacy. Disabling it stops all future image sends but does not affect transactions you've already created.
>
> **Free-tier limits.** Free and paid (non-subscriber) users receive 5 Receipt AI captures per month as a trial. Subscribers have no monthly limit (subject to a daily anti-abuse cap). Counters reset on the first of each month.

### First-run consent dialog (short-form version)

> **Turn on Receipt AI?**
>
> To read the receipt, BudgeTrak will send this one image to Google's Gemini AI (via Firebase AI Logic). Here's what that means:
>
> - Encrypted in transit (TLS 1.3) — safe on public wifi
> - Location and device metadata stripped before sending
> - Not used to train AI, not retained by Google beyond brief abuse logging
> - Your identity and other transactions are never shared
> - Rest of BudgeTrak stays end-to-end encrypted
>
> For purchases you'd rather not send to an AI, keep entering them manually.
>
> [ Turn on Receipt AI ]  [ Not now ]

### Copy rules

- **Never say "end-to-end encrypted"** about the OCR path specifically — it isn't, because the AI service is an endpoint. Reserve that phrase for the rest of SYNC.
- **Always pair "encrypted in transit" with "not retained"** — the two together are the real story. One without the other invites the obvious follow-up.
- **Name the provider** ("Google Gemini via Firebase AI Logic", or "Anthropic Claude" if we ever swap). Vague "third-party AI service" reads as evasive; naming the provider reads as confident.
- **Lead with what *doesn't* leave the device** (identity, other transactions, location) before listing what does. Users remember the first item.
- **If we ever swap providers, update copy everywhere in the same release.** Help file, privacy policy, consent dialog, and Settings subtitle must all name the same provider.

## Open questions to resolve before building

- ~~Direct-to-Anthropic vs Cloud Function proxy~~ — **resolved 2026-04-13**: Firebase AI Logic *is* the proxy; no self-hosted Function needed on the Gemini path.
- **Run the bake-off** (≥30 diverse receipts) before the feature ships to confirm Gemini 2.5 Flash hits acceptable field-level accuracy. Swap to Anthropic Haiku 4.5 if it loses.
- **Request Zero Data Retention** from Google once we have a case to justify it (user count, compliance need). Not required for launch but strengthens the privacy pitch.
- Do we strip EXIF / GPS / device metadata from the image before sending? Probably yes — there's no reason to send location data along with a receipt.
- How do we surface errors gracefully? "AI couldn't read this receipt" needs to drop the user into the manual entry dialog with the photo still attached, not feel like a failure.
- Multi-language receipts: does Haiku handle them out of the box? (Almost certainly yes for major languages, but worth verifying with a test set.)
