---
name: OCR pipeline decisions & prompt knowledge
description: Final shipping config for BudgeTrak receipt OCR plus alternatives and prompt-iteration findings to reference if switching models or tuning further
type: project
originSessionId: a00b436a-3ced-4e78-a40e-780a8f5acff8
---
# Shipping config (V10 2-call, locked 2026-04-19)

**Wired into the Android app as of 2026-04-19**. The 2-call V10 pipeline lives in
`app/src/main/java/.../data/ocr/ReceiptOcrService.kt`. Single entry point:
`ReceiptOcrService.extractFromReceipt(context, receiptId, categories, preSelectedCategoryIds)`.
Call 2 was removed entirely; its work is now done by Call 1's per-item scoring.

**Model:** Gemini 2.5 Flash-Lite (`gemini-2.5-flash-lite`). No Flash or Pro fallback — Lite is reliable.

**Category-agnostic design:** the prompt never names any user category by id or
name except the generic concept of "Other". "Other" is resolved at runtime via
`categories.find { it.tag == "other" }?.id`, never hardcoded. Works for any
custom category list (rename, delete, reorder, localize — all safe).

**2-call pipeline:**
1. **Call 1** — merchant, merchantLegalName, date (YYYY-MM-DD), `amountCents`
   (integer cents), plus `items[{description, scores:[{categoryId, score 0-100,
   reason}]}]` (up to 3 scored cats per item), plus `multiCategoryLikely` and
   `topChoice`. Prompt is the V10 5-step procedure per line item:
     Step 1 (ITEM) → Step 2 (FUNCTION) → Step 3 (DOMAIN) → Step 4 (SCAN category
     names) → Step 5 (SCORE up to 3 cats 0-100, direct match 80-100, synonym
     50-75, weak 20-50).
   Only skipped when `preSelectedCategoryIds.size == 1` (header-only prompt).
2. **Call 3** — per-item `priceCents` (integer cents). Unchanged from the old
   3-call pipeline. Considers quantity multipliers, line-level coupons, rebates,
   weight-priced lines. Input: item descriptions collapsed from Call 1.

**Route decision (code-side, in `runPipeline`):**
- `preSelect.size == 1` → trust the single preselected cat (1 API call).
- `preSelect.size >= 2` → multi path always (2 calls).
- `preSelect.isEmpty()` → `deriveMulti()` checks per-item top-1 cats (excluding
  tax lines via `\btax\b` regex). ≥2 distinct top-1 cats → multi (2 calls).
  0 or 1 distinct → single short-circuit using model's `topChoice` (1 call).

Cost: single-cat = 1 API call. Multi-cat = 2 calls. Down from 3 calls in the
pre-2026-04-19 architecture.

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

**V10 2-call on 33-receipt no-preselect subset (2026-04-19)** — 28 singles (≤5 per cat) + 5 multi-cat:

| Metric | V10 2-call (shipped) | Baseline 3-call (prev shipped) |
|---|---|---|
| Combined score | 25/33 | 25/33 |
| Singles correct | 20/28 | 20/28 |
| Multi routed | 5/5 | 5/5 |
| Multi cset | 3/5 | ~3/5 |
| **Amazon receipts** | **3/3** | **0/3** |
| API calls (single) | 1 | 1 |
| API calls (multi) | **2** | 3 |
| Category-agnostic | **yes** | no (hardcoded IDs) |

**V10 2-call wins over baseline on Amazon receipts** (the stubborn user failures —
phone charger, brake pads, tie rod boots — all now correct). Matches baseline on
overall combined score. Drops one API call on the multi path. Works for any user
category list, not just the BudgeTrak defaults.

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
- "Transcribe first, then categorize" two-step split (step 2 + step 2.5) — loses section-heading spatial context; single call with integrated categorization wins
- `PickMultipleVisualMedia` schema-enum with integer IDs — Gemini wants string enums; even when fixed, enum didn't improve recall (model still picked valid-but-wrong IDs)
- Aggressive seasonal rules (walmart_1 Easter candy) — model still tends to classify generic "Cadbury Creme" as Groc even with explicit seasonal guidance; this failure mode is hard to fix prompt-only

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

# V10 iteration (how we got here, 2026-04-19)

Five rounds of 10-variant iteration on a 33-receipt no-preselect subset:

- **Round 1 (10 variants of T-prefix)** — T7 (3-step procedure: identify product
  type → scan names → pick) won. Step decomposition was the load-bearing lever.
- **Round 2 (10 variants seeded from T7)** — T7 control tied all perturbations.
  Rule-tweaks don't stack on the 3-step structure.
- **Round 3 (10 variants with richer step decomposition)** — U10 (5-step +
  fictional + synonyms) and U2 (5-step alone) both hit combined 23, beating the
  T7 control's 22. Finer decomposition = real gain.
- **Round 4 (10 scoring-based variants)** — V10 (per-item scoring 0-100 + the
  5-step procedure) hit combined 24. Scoring alone (V2) matched T7 at 22 but via
  a different mechanism: scoring fixes multi-routing (5/5) at a small cost to
  singles. V10 combines both wins.
- **Call 1 vs Call 2 comparison** — 82% agreement on items. When they disagree,
  Call 1 is often right (e.g. "TZATZIKI DIP" → Groceries vs Call 2 → Restaurants).
  Call 2 adds no value on top of V10's per-item scoring.
- **`\btax\b` regex widening** — fixed Amazon single-item receipts being
  over-routed to multi because "Estimated tax to be collected" slipped past the
  old `/sales\s*tax/i`. All 3 Amazon receipts then short-circuit correctly.

**Category-agnostic rule (from prompt-design feedback 2026-04-19):** the prompt
must not name any specific user category (by id or by name) in its examples.
Only "Other" can be referenced — it's the single guaranteed category. Any
illustrative examples in the prompt must use fictional category names (Pet Care,
Travel/Lodging, Books/Reading) and fictional products (leash, hotel, novel).
Otherwise the prompt overfits to the BudgeTrak default list and breaks for users
with customised categories.

# Where to find things

- **Production impl:** `app/src/main/java/com/techadvantage/budgetrak/data/ocr/ReceiptOcrService.kt`
  - Public API: `extractFromReceipt(context, receiptId, categories, preSelectedCategoryIds)`
  - Internals: `runPipeline` orchestrates Calls 1 & 3; `buildCall1Prompt` / `buildCall3Prompt` build prompts; `deriveMulti` + `collapseItemsToLineItems` + `reconcilePrices` + `aggregateCategoryAmounts` post-process. No Call 2.
- **Caller:** `MainViewModel.runOcrOnSlot1(receiptId, preSelectedCategoryIds)` — routes the UI's pre-selected cat set into the pipeline. (Function name is historical; as of 2026-04-18 the receiptId can be any slot the user highlighted, not just slot 1.)
- **Harness reference (kept in sync):** `tools/ocr-harness/scripts/validate-v10-2call.js` — mirrors the Kotlin pipeline exactly.
- **V10 iteration scripts:** `tools/ocr-harness/scripts/iterate-nopresel-round{1,2,3,4}.js` (10 variants each) + `compare-c1-vs-c2.js` (ablation that killed Call 2).
- **Test data:** single-cat subset picked ≤5 per cat from `test-data/labels.json`, plus the 5 most-cats multi-cat receipts, plus 3 Amazon edge cases (`amazon_charger.jpg`, `amazon_brakepads.jpg`, `amazon_tierodboots.jpg`).
- **Historical iteration results:** `results/iterate-nopresel-r{1,2,3,4}-*.json`, `results/validate-v10-2call-*.json`, `results/c1-vs-c2-*.json`.
