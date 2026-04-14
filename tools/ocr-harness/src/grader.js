// Per-field grading. The rules match what we'd treat as "user accepts
// without editing" in the app — lenient on merchant spelling, strict on
// amount and date.

const normalizeMerchant = s =>
  (s || "").toLowerCase().replace(/[^a-z0-9]/g, "");

export function gradeResult(expected, actual) {
  const result = {
    merchant: { expected: expected.merchant, actual: actual?.merchant, pass: false },
    date:     { expected: expected.date,     actual: actual?.date,     pass: false },
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

  // Category: top-1 match against the first categoryAmounts entry.
  // If expected has no categoryId label, skip (don't count against accuracy).
  if (expected.categoryId == null) {
    result.category.skipped = true;
  } else {
    const firstId = actual?.categoryAmounts?.[0]?.categoryId;
    result.category.actual = firstId;
    result.category.pass = firstId === expected.categoryId;
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
    elapsedMsTotal: 0,
  };
  for (const t of perTestResults) {
    if (t.elapsedMs) totals.elapsedMsTotal += t.elapsedMs;
    if (!t.grade) continue;
    for (const field of ["merchant", "date", "amount"]) {
      totals[field].total++;
      if (t.grade[field].pass) totals[field].pass++;
    }
    if (!t.grade.category.skipped) {
      totals.category.total++;
      if (t.grade.category.pass) totals.category.pass++;
    }
  }
  return totals;
}

export function pct(pass, total) {
  return total === 0 ? "—" : `${((pass / total) * 100).toFixed(1)}%`;
}
