---
name: OCR pipeline decisions & prompt knowledge
description: Final shipping config for BudgeTrak receipt OCR plus alternatives and prompt-iteration findings to reference if switching models or tuning further
type: project
originSessionId: a00b436a-3ced-4e78-a40e-780a8f5acff8
---
# Shipping config (V18 split-pipeline + Call 1.5 reconciliation, 2026-04-20)

**Wired into the Android app as of 2026-04-20**. The split pipeline lives in
`app/src/main/java/.../data/ocr/ReceiptOcrService.kt`. Single entry point:
`ReceiptOcrService.extractFromReceipt(context, receiptId, categories, preSelectedCategoryIds)`.

**Model:** Gemini 2.5 Flash-Lite (`gemini-2.5-flash-lite`). No Flash or Pro fallback — Lite is reliable.

**Category-agnostic design:** the prompt never names any user category by id or
name except the generic concept of "Other". "Other" is resolved at runtime via
`categories.find { it.tag == "other" }?.id`, never hardcoded. Works for any
custom category list (rename, delete, reorder, localize — all safe).

**Split pipeline (1 / 3 / 4 calls):**
1. **Call 1 (image → extract)**: merchant, merchantLegalName, date (YYYY-MM-DD),
   `amountCents` (integer cents), `itemNames[]` (actual purchased products),
   `fullTranscript[]` (focused transcript: lines with dates, dollar amounts,
   subtotal/total/tax labels, merchant header — 10-30 lines typical; skips
   barcodes, auth codes, policy boilerplate). Prompt has explicit EXCLUDE list
   for summary rows ("Subtotal", "Grand Total", "Shipping & Handling", "Total
   before tax", payment tenders, order metadata). Without this list the model
   conflates Amazon-style Order Summary rows with actual products on some
   device encodings. Locale rule: default US MM/DD/YYYY; parse as DD/MM only on
   explicit non-US signal (non-USD currency, non-US address, non-English body).
   **Marketplace rule**: if the receipt contains "Sold by:" or "Seller" or
   "Order placed" or "Shipping & Handling", it's an online-marketplace order
   (Amazon, eBay, Etsy, Walmart.com, Target.com) — merchant = the platform,
   NOT a product brand (e.g. "QZIIW iPhone Charger") or the third-party seller
   after "Sold by:" (e.g. "Meigo"). **No-hallucinated-date rule**: if NO
   calendar date is visible anywhere, return empty string "" — never invent a
   date from training data or context (ship-tracker phrases like "Arriving
   tomorrow" are NOT dates). Parser accepts empty date; TransactionDialog's
   `runCatching { LocalDate.parse(...) }.getOrNull()?.let { selectedDate = it }`
   silently leaves `selectedDate` at today when date is empty.
2. **Call 1.5 (text → reconcile)**: text-only. Takes C1's merchant + date +
   amountCents + fullTranscript, returns reconciled {merchant, date,
   amountCents, notes}. Runs in PARALLEL with Call 2 (`coroutineScope {
   async(c1.5) async(c2) }`) so the extra call adds no wall-clock latency on
   the hot path — Call 1.5 completes in ~1s while Call 2 is ~3-4s. Non-fatal:
   if all retries fail, falls back to C1's values silently. Measured on
   harness: +6pp on amount accuracy, +2pp on date accuracy. Catches
   digit-flip OCR errors, locale date swaps, AND marketplace-merchant
   confusion (safety net when C1's marketplace rule doesn't fire).
   Includes the same marketplace signal list ("Sold by:" / "Seller" /
   "Order placed" / "Shipping & Handling") so Amazon/eBay/Etsy orders get
   merchant = platform regardless of what C1 picked. Also carries the
   no-hallucinated-date rule forward — returns "" if the transcript lacks
   a calendar date rather than echoing C1's invented date.
3. **Call 2 (image + item names as text → categorise)**: returns `items[{description,
   scores:[{categoryId, score 0-100, reason}]}]`, `multiCategoryLikely`, `topChoice`.
   The item-name list from Call 1 is included as TEXT in the prompt so the model
   has a stable anchor for categorisation even when Call 1's image interpretation
   drifts slightly on a different JPEG encoder. The image is still sent so Call 2
   can disambiguate ambiguous item names (e.g. "Ceramic Disc" = brake rotor vs
   ceramic plate) using visual context.
   Step 1 (ITEM) → Step 2 (FUNCTION) → Step 3 (DOMAIN) → Step 4 (SCAN category
   names) → Step 5 (SCORE 0-100, direct match 80-100, synonym 50-75, weak 20-50).
4. **Call 3 (image → prices, multi-cat only)**: per-item `priceCents`. Considers
   quantity multipliers, line-level coupons, rebates, weight-priced lines.

**Route decision (code-side, in `runPipeline`):**
- `preSelect.size == 1` → trust the single preselected cat, skip Calls 1.5, 2, 3. 1 API call. (Call 1.5 intentionally skipped on this path — it's a quick-entry flow where the user is watching latency.)
- Empty `itemNames` (unreadable receipt) → put whole amount in Other. 1 API call. (Call 1.5 also skipped since there's nothing to reconcile against.)
- `preSelect.size >= 2` → multi path (4 calls: C1, C1.5‖C2, C3).
- `preSelect.isEmpty()` → run C1.5 and C2 in parallel; `deriveMulti()` on C2's items → multi (4 calls total) or single (3 calls).

Cost: 1-call path (preselect=1 or unreadable): unchanged. Single-cat: 3 calls
(C1 + C1.5 ‖ C2). Multi-cat: 4 calls (C1 + C1.5 ‖ C2 + C3). The extra C1.5 is
text-only so it's the cheapest call in the chain, and parallelization with C2
means zero added wall-clock latency on the hot path.

**Post-processing (pure code, no API call):**
- **`deriveMulti`** — per-item top-1 cats → unique cats set. ≥2 = multi.
- **`collapseItemsToLineItems`** — Call 1's `items[]` → `[{description,
  categoryId}]` using top-scoring valid cat per item. Falls back to "Other" (via
  tag lookup) when no valid score.
- **`isTaxLine` regex** — `\btax\b` (case-insensitive). Catches both "Sales Tax"
  and Amazon-style "Estimated tax to be collected". The narrower `/sales\s*tax/i`
  previously over-routed Amazon single-item receipts to multi-cat because the
  tax line got its own top cat different from the real item.
- **Proportional reconciliation (`reconcilePrices`)** — scale non-tax items so
  sum matches Call 1's `amountCents`. Sales Tax preserved exactly; rounding
  residual absorbed into largest item. Handles receipt-wide discounts.
- **`aggregateCategoryAmounts`** — sum reconciled line prices by categoryId;
  Sales Tax allocated to dominant non-tax category.

# Measured performance

**V18 (+ C1.5 reconciliation) on 51-receipt new_bank (2026-04-20):** English single-cat.
Prompt v0.6, default-US locale rule, amount reconciliation self-check.

| Metric | Before label fixes | v0.6 prompt only | v0.7 + C1.5 reconcile |
|---|---|---|---|
| merchant | 100% | 100% | **100%** |
| date | 86.7% (39/45) | 92.9% (39/42) | **95.2%** (40/42) |
| amount | 90.2% (46/51) | 88.2% (45/51) | **94.1%** (48/51) |
| category | 88.2% (45/51) | 92.2% (47/51) | 88.2% (variance) |
| avg latency | 5.7s | 8.2s | 9.4s (parallelized in app → ~8s) |

**C1.5 wins tracked in new_bank:** best_buy_01 (date 2021-08-06→2021-06-08 ✓ and amount $1019.57→$1012.51 ✓), old_navy_02 (amount $64.52→$54.52 ✓), toys_01 (date 2013-08-27→2013-08-25 ✓), walgreens_02 (amount $6.94→$6.48 ✓). Fixed cases where C1 and the transcript agreed on a misread stay wrong (lowes_03 $466.23 → both read $465.83; two independent reads don't help when both misread the same digit).

**Old-bank regression (168 receipts: 104 EN single-cat + 50 Vietnamese + 14 multi-cat):** EN single-cat amount went from ~90% baseline to **97.1%** post-C1.5 — reconciliation helps equally on the historical bank. Vietnamese amount **crashed to 0%** because Call 1.5 strips the VND warning and its "normalize to cents" bias divides VND totals by 10-100×. Acceptable for now since app is English-only; if Spanish launch lands in CLP/COP/PYG markets the same bug will hit. See `project_ocr_spanish_country_setting.md` for the country-setting fix.

**V17 split-pipeline on 33-receipt no-preselect subset (historical reference, 2026-04-20)** — 28 singles (≤5 per cat) + 5 multi-cat:

| Metric | V17 split (shipped) | V10 all-in-one (prev) | Baseline 3-call |
|---|---|---|---|
| Combined score | 24/33 | 25/33 | 25/33 |
| Singles correct | 19/28 | 20/28 | 20/28 |
| Multi routed | 5/5 | 5/5 | 5/5 |
| Multi cset | 3/5 | 3/5 | ~3/5 |
| **Amazon on device** | **3/3** | **0/3** | **0/3** |
| Amazon on harness | 3/3 | 3/3 | — |
| API calls (single) | 2 | 1 | 1 |
| API calls (multi) | 3 | 2 | 3 |
| Category-agnostic | yes | yes | no |

**V17 ties V10 on harness but WINS on device** — Amazon charger, brake pads, and
tie rod boots all now land in the correct category on the user's phone. V10's
single Call-1 was sensitive to JPEG-encoder variance between test harness and
Android Bitmap.compress; V17's split decouples categorisation (text-reasoning,
stable across encoders) from image extraction. The extra API call per receipt
buys deterministic on-device behaviour across any device/encoder combination.

**Prior 14 multi-cat bake-off (pre-V10, preselected cats only)** — still useful historical reference:

| Method | cset | cshr | cost/receipt | latency |
|---|---|---|---|---|
| Pro 2.5 single-call (R7-T10) | 9/14 | 8/14 | $0.00326 | 33s |
| Lite 3-call soft+niche (shipped pre-V10) | 12/14 | 4/14 | $0.00078 | 9.5s |

- Merchant / date / amount: 14/14 on both
- 13/14 receipts reconciled to exact penny; 1 drifted $0.05 (Call 1 amount misread)
- Cost estimate: ~$0.05/user/month at 100 receipts (25 multi-cat)

# Why Lite over Pro/Flash

- **Lite 4× cheaper, 3.5× faster** than Pro. cshr edge for Pro (8 vs 4) is within "±$2 tolerance per bucket is invisible to user" territory for a budget tracker where amounts are tap-editable.
- **Flash was not competitive** — Pro-grade prompts cost more on Flash than on Pro itself due to mid-tier thinking tokens, without commensurate accuracy.
- **Pro's cshr win** comes mostly from better amount-splits on ambiguous items; Lite reaches same items-per-bucket just with less precise dollar allocation. Reconciliation flattens this difference.

# Prompt iteration findings (40+ variants across 4 rounds on 3 long receipts)

**What won:**
- v30 base: ultra-minimal rules — "skip non-purchase lines" + "prefer concrete over Other" + "avoid rare cats unless unambiguously that type"
- Soft preselect nudge, not strict ("use ALL of them" force-fit causes wrong cat assignments when user mis-selects)
- Niche preference with concrete examples (Holidays/Kid's/Entertainment/Clothes/Health) beats vague "prefer specific buckets"
- Integer cents (amountCents) for Call 1 — avoids JS `0.1 + 0.2 = 0.30000000000000004` precision errors in downstream sums

**What backfired:**
- Long rule sheets (product-override paragraphs for pets/batteries/frozen/seasonal) added noise and regressions
- V14: "transcribe first, then categorize" WITHIN a single call — uses up output
  tokens, disperses the model's attention, hurts Amazon recall. (V17's split
  pipeline is different — separate API call for categorisation, item names as
  text prompt input, not as model-generated output.)
- `PickMultipleVisualMedia` schema-enum with integer IDs — Gemini wants string enums; even when fixed, enum didn't improve recall (model still picked valid-but-wrong IDs)
- Aggressive seasonal rules (walmart_1 Easter candy) — model still tends to classify generic "Cadbury Creme" as Groc even with explicit seasonal guidance; this failure mode is hard to fix prompt-only
- V11: strict anti-broad "IMPORTANT: do NOT score Supplies/Goods above..." —
  fixed Amazon brakes but broke target_long_1 (women's dresses → Kid's Stuff in
  2-cat race). Don't force specific-vs-specific reordering through the same
  rule that handles specific-vs-catch-all.

**Caching (not useful for Lite):**
- **Implicit prefix caching on Flash-Lite: did NOT fire** during 2232-token shared prefix tests across multi-call pipelines. Caching seems to be Pro/Flash-primary feature. Don't plan around caching savings on Lite.
- Explicit `cachedContents` API works but minimum 1024 token cache; savings on Lite's $0.10/M input tokens are ~$0.00004/receipt. Not worth implementation complexity.

**If switching models later:**
- Pro 2.5: use R7-T10 prompt (C3+C4+C6+MP from `tools/ocr-harness/scripts/test-pro-multicat.js`) with single call + category pre-selection. cshr sharpness worth $0.003/receipt premium only if per-bucket dollar accuracy becomes critical.
- Gemini 2.5 Flash: only if Lite's quality regresses after a model update. No tested advantage today.
- Claude family (Haiku/Sonnet/Opus): **do not retry** — 30 prompt variants × 3 models all failed cshr 0/10 on app-compressed receipts. Already captured in feedback_claude_receipt_ocr_unsuitable.md.

# Known weaknesses still present

- **Cryptic store-brand SKUs** misread: `GV BOW TIE` (pasta) → Clothes, `BARNUMS ANI` (crackers) → Kid's Stuff, `NEKOT VAN` (cookies) → Other. These require either image context or product database lookup, not prompt tuning.
- **Receipt-wide discount lines** (Target Circle 5%, Walmart DISCOUNT GIVEN) invisible to Call 3 — reconciliation handles them, but individual line prices shown in UI are scaled, not literal receipt-printed values. Users seeing "$4.17" in app vs "$4.39" on receipt may be confused.
- **Seasonal goods ambiguity** (Easter candy, Halloween items) — model splits inconsistently between Holidays and Groceries even with explicit seasonal rules.
- **Bookstore/stationery categorization** — sroie_0070 shows bookstore receipts ambiguous between Entertainment/Kid's/Other, varies by prompt.

# Iteration history (how we got to V17)

**Phase 1: 4 rounds of 10-variant iteration (2026-04-19), 33-receipt subset:**
- **Round 1 (T-prefix)** — T7 (3-step procedure) won. Step decomposition was the load-bearing lever.
- **Round 2** — rule tweaks on T7 didn't stack. Structure matters more than wording.
- **Round 3 (richer decomposition)** — U10 (5-step + fictional + synonyms) and U2 (5-step alone) beat T7.
- **Round 4 (scoring variants)** — V10 (per-item 0-100 scoring + 5-step) hit combined 24. Scoring fixes multi-routing (5/5) because multi-vs-single falls out of per-item scores instead of being a separate model judgment.
- Call 1 vs Call 2 comparison: 82% agreement; Call 2 added no value over V10's per-item scoring → dropped.
- `\btax\b` regex widening — "Estimated tax to be collected" was slipping past `/sales\s*tax/i`.

**Phase 2: device-variance discovery + split pipeline (2026-04-20):**
- User reports Amazon brakes → Home Supplies ON DEVICE despite V10 harness showing 3/3 correct.
- Investigation ruled out: caller cat list (matches), API endpoint (both v1beta), temperature (both 0), prompt text (byte-identical).
- **Root cause: Google GenerativeAI Android SDK's `content { image(bitmap) }` re-encodes every Bitmap at JPEG quality 80 before sending** — silently degrading the q=95+ bytes we store. Fixed with `content { blob("image/jpeg", rawBytes) }` which bypasses the re-encode. See `feedback_genai_sdk_bitmap_reencode.md`.
- **Secondary cause: Amazon "Order Summary" sections** (Item(s) Subtotal, Shipping, Grand Total) on tie-rod and charger screenshots were being read as line items at higher device quality — summary text is crisper at q=95+ so the model treats it as content. Fixed in V17 with an explicit EXCLUDE list in Call 1's prompt.
- **V11 through V16 were dead ends** — strict anti-broad rules, transcribe-first, narrow catch-all tiebreaks all either failed to fix brakes or regressed other receipts (target_long_1 clothes → Kid's Stuff). V17 split pipeline is what worked.
- **V17 split-pipeline insight**: Call 1 does image → item names only. Call 2 receives the item names AS TEXT in its prompt plus the image for disambiguation. Text anchor is stable across JPEG encoders → categorisation is deterministic on device.

**Category-agnostic rule (from prompt-design feedback 2026-04-19):** the prompt
must not name any specific user category (by id or by name) in its examples.
Only "Other" can be referenced — it's the single guaranteed category. Any
illustrative examples in the prompt must use fictional category names (Pet Care,
Travel/Lodging, Books/Reading) and fictional products (leash, hotel, novel).
Otherwise the prompt overfits to the BudgeTrak default list and breaks for users
with customised categories.

**Harness-device parity tooling (2026-04-20):**
`tools/ocr-harness/scripts/simulate-app-compression.js` ports the Kotlin
`ReceiptManager.processAndSavePhoto` compression algorithm to Node —
resize-to-1000px with 400px shortest-edge floor via bilinear (Triangle filter),
iterative quality starting q=92 targeting 256KB/MP ±10%, 4:4:4 subsampling at
q≥90 and 4:2:0 below. Run `--batch inDir outDir` to produce app-equivalent JPEGs
for any test corpus. Validation: V17 gives identical aggregate scores on
original images vs app-simulated images, including 3/3 Amazon correct — so the
harness now predicts device behaviour. Byte-identical output isn't achievable
(ImageMagick's libjpeg-turbo build vs Android's build produce slightly different
bytes at the same quality+subsampling) but algorithmic parity closes the ~70%
of the gap that mattered.

# Where to find things

- **Production impl:** `app/src/main/java/com/techadvantage/budgetrak/data/ocr/ReceiptOcrService.kt`
  - Public API: `extractFromReceipt(context, receiptId, categories, preSelectedCategoryIds)`.
  - Internals: `runPipeline` orchestrates Calls 1 / 2 / 3; `runCall1` extracts header + item names; `runCall2` scores categories; `runMultiCat` runs Call 3 + reconciles; `collapseItemsToLineItems` + `reconcilePrices` + `aggregateCategoryAmounts` post-process.
  - Debug logging (BuildConfig.DEBUG only) dumps full Call 1 + Call 2 JSON to logcat with tag `ReceiptOcrService` so on-device issues are diagnosable via the Dump button.
- **Caller:** `MainViewModel.runOcrOnSlot1(receiptId, preSelectedCategoryIds)` — routes the UI's pre-selected cat set into the pipeline. (Function name is historical; as of 2026-04-18 the receiptId can be any slot the user highlighted, not just slot 1.)
- **Harness reference (kept in sync):** `tools/ocr-harness/scripts/validate-v17.js` — mirrors the Kotlin V17 pipeline exactly on original test images. `validate-v17-appsim.js` runs it on app-simulated bytes (see below).
- **App-compression simulator:** `tools/ocr-harness/scripts/simulate-app-compression.js` — Node port of `ReceiptManager.processAndSavePhoto`. Produces JPEGs with matching dimensions, quality, subsampling, and target size. Use `--batch inDir outDir`.
- **Phase-1 iteration scripts (kept as history):** `tools/ocr-harness/scripts/iterate-nopresel-round{1,2,3,4}.js` (10 variants each) + `compare-c1-vs-c2.js` (ablation that killed Call 2 in V10).
- **Phase-2 iteration scripts:** `test-specificity-rule.js` (V11 anti-broad rule test), `test-v12.js` (transcribe + narrow rule), `test-v13.js` (strict + transcribe + clarifier), `test-v14.js` (V10 + transcribe only), `test-v15-split.js` (text-only Call 2), `test-v16-split-with-image.js` (winner — text + image Call 2), `test-v17-c1-prompt.js` (Call 1 EXCLUDE-list sweep).
- **Test data:** single-cat subset picked ≤5 per cat from `test-data/labels.json`, plus the 5 most-cats multi-cat receipts, plus 3 Amazon edge cases (`amazon_charger.jpg`, `amazon_brakepads.jpg`, `amazon_tierodboots.jpg`). App-simulated copies at `test-data/app_sim/`.
- **Historical iteration results:** `results/iterate-nopresel-r{1,2,3,4}-*.json`, `results/validate-v{10,11,12,13,14,15,16,17}-*.json`, `results/c1-vs-c2-*.json`.
