// Per-field grading. The rules match what we'd treat as "user accepts
// without editing" in the app — lenient on merchant spelling, strict on
// amount and date.

const normalizeMerchant = s =>
  (s || "").toLowerCase().replace(/[^a-z0-9]/g, "");

export function gradeResult(expected, actual) {
  const result = {
    merchant: { expected: expected.merchant, actual: actual?.merchant, pass: false },
    date:     { expected: expected.date,     actual: actual?.date,     pass: false, skipped: !expected.date || expected.type === "bill" },
    amount:   { expected: expected.amount,   actual: actual?.amount,   pass: false },
    category: { expected: expected.categoryId, actual: null,           pass: false, skipped: false },
  };

  // Merchant: substring match on alphanumeric-only lowercased strings.
  // "Chipotle Mexican Grill #1234" normalized is "chipotlemexicangrill1234";
  // "Chipotle" normalized is "chipotle"; substring match → pass.
  if (expected.merchant && actual?.merchant) {
    const e = normalizeMerchant(expected.merchant);
    const a = normalizeMerchant(actual.merchant);
    result.merchant.pass = e.length > 0 && a.length > 0 && (a.includes(e) || e.includes(a));
  }

  // Date: exact YYYY-MM-DD match.
  result.date.pass = !!(expected.date && actual?.date && expected.date === actual.date);

  // Amount: ±$0.01 tolerance.
  if (typeof expected.amount === "number" && typeof actual?.amount === "number") {
    result.amount.pass = Math.abs(expected.amount - actual.amount) <= 0.01;
  }

  // Category: accept if ANY entry in a multi-category split matches the expected id.
  // Rationale: when Gemini legitimately splits a mixed-purchase receipt (Target:
  // some grocery, some home) it's more accurate than a single-bucket label, so we
  // shouldn't mark it wrong. We do report which ids were returned so a reviewer
  // can spot the difference.
  if (expected.categoryId == null) {
    result.category.skipped = true;
  } else {
    const ids = (actual?.categoryAmounts ?? []).map(c => c.categoryId);
    result.category.actual = ids.length === 1 ? ids[0] : ids;
    result.category.pass = ids.includes(expected.categoryId);
  }

  // ── Per-item category grading (new) ────────────────────────────────────
  // When expected.categoryAmounts is populated (per-line-item ground truth),
  // we evaluate two additional metrics:
  //   - setMatch: every expected categoryId appears in extracted with ≥$0.01
  //   - shareMatch: for every expected category, extracted amount within
  //       max($2.00, 15% of expected). Requires setMatch first.
  //
  // Single-category expected labels trivially trigger setMatch when the
  // extracted array includes that one id. Multi-category labels are where
  // this gets interesting — Target, Sam's Club, etc.
  result.categoryAmounts = {
    expected: expected.categoryAmounts,
    actual: actual?.categoryAmounts,
    setMatch: false,
    shareMatch: false,
    skipped: !expected.categoryAmounts,
  };

  if (expected.categoryAmounts && actual?.categoryAmounts) {
    // SUM per-categoryId — handle models that return one entry per line item
    // rather than consolidating (e.g., Lite returns 37 entries for Sam's Club).
    const actualById = new Map();
    for (const c of actual.categoryAmounts) {
      if (typeof c.categoryId !== "number" || typeof c.amount !== "number") continue;
      actualById.set(c.categoryId, (actualById.get(c.categoryId) || 0) + c.amount);
    }
    // Drop any categories with total < $0.01 (protects against tiny rounding residuals).
    for (const [id, amt] of [...actualById]) if (Math.abs(amt) < 0.01) actualById.delete(id);

    const expectedIds = expected.categoryAmounts.map(c => c.categoryId);
    const allPresent = expectedIds.every(id => actualById.has(id));
    result.categoryAmounts.setMatch = allPresent;

    if (allPresent) {
      const allWithinTolerance = expected.categoryAmounts.every(exp => {
        const act = actualById.get(exp.categoryId);
        const tolerance = Math.max(2.0, Math.abs(exp.amount) * 0.15);
        return Math.abs(exp.amount - act) <= tolerance;
      });
      result.categoryAmounts.shareMatch = allWithinTolerance;
    }
  }

  // ── Per-line-item grading (new) ────────────────────────────────────────
  // When expected.items is populated, compare the multiset of per-item
  // categoryIds between expected and extracted. This complements the
  // aggregate categoryAmounts check: two models can both produce the right
  // top-line category totals while differing wildly on which *items* they
  // put in each bucket. The per-item metric catches that.
  //
  // Metric: Jaccard similarity of the category-id multisets, with non-item
  // lines (null categoryId) excluded from both sides.
  //   items.jaccard      — 0.0–1.0 similarity score
  //   items.itemsPass    — binary gate at jaccard ≥ 0.80
  //   items.expectedCount/actualCount — raw counts, for report context
  result.items = {
    expectedCount: 0,
    actualCount: 0,
    jaccard: 0,
    itemsPass: false,
    skipped: !expected.items,
  };

  if (expected.items) {
    const expIds = expected.items
      .filter(i => typeof i?.categoryId === "number")
      .map(i => i.categoryId);
    const actIds = (Array.isArray(actual?.lineItems) ? actual.lineItems : [])
      .filter(i => i && typeof i === "object" && typeof i.categoryId === "number")
      .map(i => i.categoryId);
    result.items.expectedCount = expIds.length;
    result.items.actualCount = actIds.length;

    if (expIds.length > 0) {
      const expCounts = new Map();
      for (const id of expIds) expCounts.set(id, (expCounts.get(id) || 0) + 1);
      const actCounts = new Map();
      for (const id of actIds) actCounts.set(id, (actCounts.get(id) || 0) + 1);
      let intersection = 0, unionSum = 0;
      const allIds = new Set([...expCounts.keys(), ...actCounts.keys()]);
      for (const id of allIds) {
        intersection += Math.min(expCounts.get(id) || 0, actCounts.get(id) || 0);
        unionSum += Math.max(expCounts.get(id) || 0, actCounts.get(id) || 0);
      }
      result.items.jaccard = unionSum === 0 ? 0 : intersection / unionSum;
      result.items.itemsPass = result.items.jaccard >= 0.8;
    }
  }

  return result;
}

export function summarize(perTestResults) {
  const totals = {
    tests: perTestResults.length,
    merchant: { pass: 0, total: 0 },
    date:     { pass: 0, total: 0 },
    amount:   { pass: 0, total: 0 },
    category: { pass: 0, total: 0 },
    categorySet:   { pass: 0, total: 0 },
    categoryShare: { pass: 0, total: 0 },
    multiCategorySet:   { pass: 0, total: 0 },
    multiCategoryShare: { pass: 0, total: 0 },
    items:              { pass: 0, total: 0, jaccardSum: 0 },
    elapsedMsTotal: 0,
  };
  for (const t of perTestResults) {
    if (t.elapsedMs) totals.elapsedMsTotal += t.elapsedMs;
    if (!t.grade) continue;
    for (const field of ["merchant", "date", "amount"]) {
      if (t.grade[field].skipped) continue;
      totals[field].total++;
      if (t.grade[field].pass) totals[field].pass++;
    }
    if (!t.grade.category.skipped) {
      totals.category.total++;
      if (t.grade.category.pass) totals.category.pass++;
    }
    const ca = t.grade.categoryAmounts;
    if (ca && !ca.skipped) {
      totals.categorySet.total++;
      if (ca.setMatch) totals.categorySet.pass++;
      totals.categoryShare.total++;
      if (ca.shareMatch) totals.categoryShare.pass++;
      // Multi-category-only sub-segment (receipts with >1 expected category).
      if (ca.expected && ca.expected.length > 1) {
        totals.multiCategorySet.total++;
        if (ca.setMatch) totals.multiCategorySet.pass++;
        totals.multiCategoryShare.total++;
        if (ca.shareMatch) totals.multiCategoryShare.pass++;
      }
    }
    const it = t.grade.items;
    if (it && !it.skipped && it.expectedCount > 0) {
      totals.items.total++;
      totals.items.jaccardSum += it.jaccard;
      if (it.itemsPass) totals.items.pass++;
    }
  }
  return totals;
}

export function pct(pass, total) {
  return total === 0 ? "—" : `${((pass / total) * 100).toFixed(1)}%`;
}
