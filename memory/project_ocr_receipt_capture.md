---
name: OCR / AI Receipt Capture plan
description: Subscriber feature plan for snap-or-share receipt and screenshot capture using a multimodal LLM. Covers architecture, costs, Android share-intent integration, the user-decided privacy model (opt-in, per-receipt exception to E2E encryption, manual entry encouraged for sensitive transactions), and dedupe concerns.
type: project
originSessionId: e62277a3-386c-4af8-8747-78a2f79a4bee
---
A subscriber-tier feature where users either snap a photo of a paper receipt or share a screenshot from any other app (DoorDash, Amazon, Uber Eats, bank notification email, etc.), and BudgeTrak extracts merchant, amount, date, and a category hint, then creates a transaction with the image attached as the receipt photo. Originated from the 2026-04-11 brainstorm session as the strongest single subscription-conversion candidate.

## Why this is the right approach

Screenshots are *easier* than physical receipts for OCR because they're crisp, well-lit, and machine-rendered with no skew or damage. The hard part isn't recognition; it's extracting structured data from wildly different layouts (every food delivery app, every shopping app, every bank notification has its own format). Two paths:

1. **Classic OCR + per-source regex parser.** Falls apart fast — every popular app needs its own parser, parsers break when those apps change layout, maintenance is forever. Skip.
2. **Multimodal LLM directly on the image.** Hand the image to a vision-capable model with a short prompt asking for `{merchant, amount, date, category_hint, confidence}` JSON. Robust across layouts the model has never seen. This is the right answer.

Recommended model: Claude Haiku 4.5 vision (or whatever the cheapest current vision model is when implementation starts). Per-call cost is roughly $0.002–0.005 — image input is ~1000–2000 tokens depending on resolution, output is a couple hundred tokens. A user doing 50 captures/month costs ~$0.10–0.25; 200/month is still under $1. Easily covered by a $3–5/month subscription.

## Privacy model (decided 2026-04-11)

User's framing, which we should follow:

- **Opt-in only.** A clear toggle in Settings (default off) and a first-run consent dialog the very first time the user tries to use the feature. The dialog must explicitly say: "The image you're about to capture will leave your device and be sent to a third-party AI service for processing. Your other transaction data is unaffected and remains end-to-end encrypted."
- **Per-receipt exception, not a blanket reduction.** This is the key framing. BudgeTrak's overall privacy story is unchanged — SYNC is still end-to-end encrypted, the cloud server still cannot read your transaction data, your bank balances and merchant names are still encrypted at rest and in transit. The OCR feature is a deliberate, scoped exception: *individual images you choose to send* go to the AI service. Nothing else changes.
- **Encourage manual entry for sensitive transactions.** The consent dialog and the Settings page should both gently suggest: "For purchases you'd rather not send to a third party — medical, legal, personal — just enter them manually as you do today. The OCR feature is here to save you time on everyday receipts, not to handle every transaction."
- **Anthropic API does not train on customer data by default**, which is the right thing to put in the privacy policy. We should still be explicit that the image is processed by a third-party AI and not stored long-term by us beyond the local receipt photo attachment.
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
- Call the LLM API. **Strongly prefer routing through a thin Cloud Function proxy** over direct-to-Anthropic from the client: keeps the API key server-side, lets us enforce the per-user free-tier quota at a single chokepoint, gives us a place to add abuse rate limiting (e.g. 100/day hard cap per user), and means we can change models without an app update.
- The proxy authenticates the caller via Firebase Auth (we already require Auth for SYNC users — solo users would either get a separate App Check-only path or be ineligible for OCR). The proxy increments the user's monthly counter atomically before calling Anthropic, decrements on API failure.
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
| `functions/ocr-extract.js` (or similar) | New | Cloud Function proxy: auth check, quota check, Anthropic API call, response forwarding |
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

## Open questions to resolve before building

- Direct-to-Anthropic from the client, or proxy through a Cloud Function to keep the API key server-side? Proxy is safer and lets us add rate limiting per user, but adds latency and a server-side cost.
- Do we strip EXIF / GPS / device metadata from the image before sending? Probably yes — there's no reason to send location data along with a receipt.
- How do we surface errors gracefully? "AI couldn't read this receipt" needs to drop the user into the manual entry dialog with the photo still attached, not feel like a failure.
- Multi-language receipts: does Haiku handle them out of the box? (Almost certainly yes for major languages, but worth verifying with a test set.)
