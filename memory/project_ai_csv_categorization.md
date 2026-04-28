---
name: AI CSV Categorization Plan
description: Subscriber+Paid feature to send low-confidence CSV-imported transactions to Gemini 2.5 Flash-Lite for category assignment. Hybrid heuristic+AI pipeline, opt-in toggle, orange offline-only network hint, privacy-policy addendum, EN+ES strings.
type: project
---

# AI CSV Categorization — feature plan (2026-04-16)

Mirrors the receipt-OCR precedent (`project_ocr_receipt_capture.md`) for a second AI feature, targeted at improving CSV-import categorization.

## Scope & tiers
- Available to: **Paid** and **Subscriber** tiers. Free excluded (no CSV import on Free).
- Opt-in: default **off**. User enables in Settings.
- Feature active = `(isPaidUser || isSubscriber) && aiCsvCategorizeEnabled`.
- New SharedPref: `aiCsvCategorizeEnabled` in `app_prefs`, mirrored as `var` on MainViewModel.

## Cost model (2026-04-16 estimate)
- Model: **Gemini 2.5 Flash-Lite via Firebase AI Logic**. Text-only, clean bank data — Flash-Lite digit/diacritic issues from OCR decision **do not apply**.
- Pricing: $0.10/1M input, $0.40/1M output.
- Per-batch (~40 low-confidence txns): ~$0.0002.
- Per power-user/month (10 CSV loads, heuristic catches ~60% → ~16 low-conf txns/load on avg): **~$0.001/user/month**. Even 10k subs = ~$10/month COGS.
- Subscription ($4.99/mo) covers it 5000×+.

## Service layer
New package `data/ai/`, parallel to `data/ocr/`:
- `AiCategorizerService.kt` — Firebase AI Logic client. `suspend fun categorizeBatch(txns, categories): Result<Map<Int, String>>` returning index → category tag.
- `CategorizerPromptBuilder.kt` — builds prompt text + response schema.
- `CategorizerResult.kt` — wrapper.
- Timeout: 30s (text is fast; shorter than OCR's 90s).
- Chunk any batch >100 txns into sequential 100-chunk calls.

## Prompt design
Structured-output JSON via response schema:
- **System:** "Categorize transactions into the best-matching user category **tag**. Amount often disambiguates ($5 at gas station → food; $50 → fuel). Return `other` if no category clearly fits."
- **Input:** `{categories: [{tag, name, description?}], transactions: [{i, merchant, amount, date}]}`.
- **Output schema:** `{"results": [{"i": int, "tag": string}]}`.
- Output is **tag** (not categoryId) for robustness to per-user IDs and category renames.

## Hybrid heuristic+AI pipeline
Call site: `TransactionsScreen.kt:568` (right after `parsedTransactions.addAll(processed)`).

1. Run existing `autoCategorize` on all rows.
2. Classify each result:
   - **High confidence** = `≥5 matching historical txns AND ≥80% agreement on a single category` → keep heuristic result, skip AI.
   - **Low confidence** = fewer matches, thinner agreement, or fell back to "other" → mark for AI.
3. Send only the low-confidence subset to Gemini in one batch (chunking above 100).
4. Apply returned tag → look up user's category with that tag → assign. If no user category carries that tag, keep heuristic result.
5. On AI failure (timeout, network, parse): keep heuristic result silently, log non-fatal with reason.
6. All of this runs **after** `filterAlreadyLoadedDays` — never pay for already-imported data.

Partial failures (e.g. 38/40 returned): apply the 38, keep heuristic for the 2.

## UI — import dialog network hint
- Orange `Color(0xFFFF9800)` Text in import-format picker dialog.
- Shown only when `aiCsvActive == true` AND `!isNetworkAvailable()` at dialog open. Non-nagging.
- Copy: *"Connect to Wi‑Fi or mobile data before importing for best categorization."*
- Positioned below format radio buttons, above Continue.

## Settings toggle + help
Place in the "AI Features" section of Settings alongside the OCR toggle.
- Label: "AI CSV categorization"
- Subtitle: "Use AI to categorize imported bank transactions."
- Free-user tap → toast `upgradeForAiCsv` (mirrors `upgradeForAiOcr`).
- Help-page copy (mirrors receipt-photo help tone):
  > AI CSV categorization sends the merchant name, amount, and date of imported transactions to Google's Gemini AI to pick the best-matching category. Data is encrypted in transit (HTTPS) and deleted by Google when the request completes — nothing is stored. The AI provider never sees your account, your other transactions, or any receipt photos. Disable this toggle to use only BudgeTrak's on-device matching.

## Strings (EN + ES)
New keys in `ui/strings/AppStrings.kt` + `EnglishStrings.kt` + `SpanishStrings.kt` + `TranslationContext.kt`:
- `settings.aiCsvCategorizeLabel`
- `settings.aiCsvCategorizeSubtitle`
- `settings.aiCsvCategorizeHelp` (multi-paragraph)
- `settings.upgradeForAiCsv`
- `transactions.importAiNetworkHint`
- `transactions.importAiBusy` ("Smart categorizing transactions…")

Total string count becomes ~1,399 vals across 22 data classes after this feature.

## Privacy policy
Update `/storage/emulated/0/Download/Tech Advantage Pages/privacy.md`. New subsection **"AI CSV Categorization"** under existing "AI Receipt Scanning":
- Sent: merchant, amount, date of newly-imported bank txns.
- Not sent: balances, other transactions, identifiers, receipt photos.
- Provider: Google Gemini via Firebase AI Logic.
- Retention: deleted on completion; not used for training.
- Transport: HTTPS/TLS.
- Control: opt-in Settings toggle; disabled falls back to on-device matching.
- Eligibility: Paid and Subscriber.

Push to legal repo; privacy URL unchanged.

## Observability
- Log non-fatal to Crashlytics on API failure with reason (timeout/network/parse). No remote killswitch needed — tier+toggle gate is enough.

## Accuracy harness
Add `tools/ocr-harness/scripts/test-csv-categorize.js` (parallel to OCR harness):
- Run sample CSV against Flash-Lite, measure per-category accuracy vs. a labeled truth set.
- Include a Spanish-bank sample to rule out the diacritic/merchant issues Flash-Lite showed on Vietnamese OCR (shouldn't happen on ASCII bank text, but verify).
- Target: 85%+ top-1 accuracy before ship.

## Effort estimate
~½ day of focused work: 1 service + 1 prompt builder + Settings wiring + 6 strings × 2 langs + privacy-policy edit + harness script. Excludes privacy-policy review cycle.

## Status
**Shipped 2026-04-16.** Harness verified 96% accuracy on 50 labeled USA retailers (only misses were State Farm + Geico routed to Mortgage/Insurance/PropTax instead of Insurance — arguably reasonable given the user's overlapping category names; prompt line added to guide pure-insurance merchants toward Insurance). Integrated in `TransactionsScreen.kt` CSV-import path and `ImportParseErrorDialog` `onKeepParsed` fallback; `aiCsvCategorizeEnabled` pref default **off**; Settings row gated by `isPaidUser || isSubscriber`. Privacy policy updated with new Gemini row + AI-Assisted Features section (covers both OCR and CSV categorization).
