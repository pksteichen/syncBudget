#!/usr/bin/env node
// Validate the 2-call V10 architecture on the 33-receipt subset.
//
// Pipeline:
//   Call 1 (V10 scoring): per-item scores + multi flag + topChoice
//   Single-cat (multi=false): short-circuit to topChoice — DONE in 1 call.
//   Multi-cat (multi=true): use Call 1's items[] directly (top-1 cat per item),
//     then Call 3 for prices. No Call 2. Reconcile to Call 1's amountCents.
//
// Compare against V10's saved 3-call results (round 4) for regression check.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import "dotenv/config";
import { GoogleGenAI } from "@google/genai";

import { TEST_CATEGORIES } from "../src/categories.js";
import { gradeResult } from "../src/grader.js";

const __filename = fileURLToPath(import.meta.url);
const ROOT = path.resolve(path.dirname(__filename), "..");
const LITE = "gemini-2.5-flash-lite";
const PRICING = { [LITE]: { in: 0.10, out: 0.40 } };
const client = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

function pickSubset(labels) {
  const nonVN = labels.filter(l => !l.file.startsWith("mcocr_"));
  const subset = [];
  const singles = nonVN.filter(l => l.categoryAmounts?.length === 1 || (l.categoryAmounts == null && l.categoryId));
  const byCat = new Map();
  for (const s of singles) { const cid = s.categoryAmounts?.[0]?.categoryId ?? s.categoryId; if (!byCat.has(cid)) byCat.set(cid, []); byCat.get(cid).push(s); }
  for (const [, files] of byCat) { files.sort((a, b) => a.file.localeCompare(b.file)); subset.push(...files.slice(0, 5)); }
  const multi = nonVN.filter(l => l.categoryAmounts?.length > 1).sort((a, b) => (b.categoryAmounts.length - a.categoryAmounts.length) || a.file.localeCompare(b.file)).slice(0, 5);
  subset.push(...multi);
  return subset;
}

function categoryList(cats) {
  return cats.map(c => c.tag ? `  - id=${c.id} name="${c.name}" tag="${c.tag}"` : `  - id=${c.id} name="${c.name}"`).join("\n");
}

const HEADER_BASE = `Extract receipt header data as JSON.

- merchant: the consumer brand (e.g. "McDonald's", "Target", "Costco"). Prefer the consumer brand over the legal operator entity. Preserve original language; don't translate.
- merchantLegalName: optional, only when the legal entity is clearly distinct from the consumer brand.
- date: YYYY-MM-DD ISO. Receipts often have multiple dates; prefer the transaction date over print/due dates. DD/MM locales: Malaysia, Singapore, Vietnam, most of Europe. MM/DD: US.
- amountCents: INTEGER number of cents for the final paid total (tax and tip included). Ignore subtotal, pre-tax lines, and separate GST/VAT summary tables. Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are thousand separators; return the integer đồng value.`;

const COMMON = `
Category-picking constraints (global):
  - "Other" is reserved for items that no other category name plausibly describes.
  - Do NOT invent categoryIds not in the list.`;

function v10Call1Prompt(cats) {
  const intro = `
For each PURCHASED item on the receipt, follow these 5 steps:
  Step 1 (ITEM): name the literal thing (e.g. "rubber disc", "leather collar").
  Step 2 (FUNCTION): describe what it's used for (e.g. "creates friction on a car wheel"; "worn by a pet").
  Step 3 (DOMAIN): name the real-world domain in 1-3 nouns (e.g. "vehicle repair part"; "pet accessory").
  Step 4 (SCAN): for each category, evaluate whether its NAME contains a noun matching the Step 3 domain directly OR via a close synonym.
  Step 5 (SCORE): score up to 3 categories 0-100.
    - A category whose name DIRECTLY contains a word naming the item's domain (e.g. "Transportation" for a car part, "Pet" for a pet accessory, "Phone" for a phone charger) = 80-100.
    - A category whose name doesn't contain the domain word but is a close synonym = 50-75.
    - A weak or tangential fit = 20-50.
    - IMPORTANT: do NOT score a broad-sounding category (e.g. name contains "Supplies", "Goods", "Items", "General") ABOVE a category whose name directly names the domain. A directly-named category always wins over a broader-sounding one when both plausibly fit. A category is not a "catch-all" just because its name is generic — interpret it narrowly to the words it actually contains.

Return items[] with { description, scores: [{categoryId, score, reason}] }. Skip discounts/promos/subtotals; include "Sales Tax".
Also return multiCategoryLikely (true if items' top-1 domains differ) and topChoice (when false).`;
  return HEADER_BASE + intro + COMMON + `\n\nCategories:\n${categoryList(cats)}`;
}

function call3Prompt(items) {
  const listed = items.map((it, i) => `  ${i + 1}. ${it.description}`).join("\n");
  return `For each item, determine the ACTUAL PAID PRICE in integer cents.

Clues: base printed price; quantity multipliers; line-level coupons reduce; weight-priced use printed total; "Sales Tax" → printed tax in cents.

Return JSON {prices: [{description, priceCents}]}, preserving order.

Items:
${listed}`;
}

const CALL1_SCHEMA = {
  type: "object",
  properties: {
    merchant: { type: "string" }, merchantLegalName: { type: "string" },
    date: { type: "string" }, amountCents: { type: "integer" },
    items: { type: "array", items: { type: "object", properties: { description: { type: "string" }, scores: { type: "array", items: { type: "object", properties: { categoryId: { type: "integer" }, score: { type: "number" }, reason: { type: "string" } }, required: ["categoryId", "score"] } } }, required: ["description", "scores"] } },
    multiCategoryLikely: { type: "boolean" }, topChoice: { type: "integer" }, notes: { type: "string" },
  },
  required: ["merchant", "date", "amountCents"],
};
const CALL3_SCHEMA = { type: "object", properties: { prices: { type: "array", items: { type: "object", properties: { description: { type: "string" }, priceCents: { type: "integer" } }, required: ["description", "priceCents"] } } }, required: ["prices"] };

function mimeFor(f) { const e = path.extname(f).toLowerCase(); if (e === ".png") return "image/png"; if (e === ".webp") return "image/webp"; return "image/jpeg"; }

async function apiCall(parts, schema) {
  let lastErr;
  for (let attempt = 1; attempt <= 4; attempt++) {
    try {
      const res = await client.models.generateContent({ model: LITE, contents: [{ role: "user", parts }], config: { responseMimeType: "application/json", responseSchema: schema, temperature: 0 } });
      return { parsed: JSON.parse(res.text), tokens: res.usageMetadata };
    } catch (e) { lastErr = e; if (!/503|UNAVAILABLE|overloaded|429|RESOURCE_EXHAUSTED|deadline|fetch failed|network|ECONNRESET|ETIMEDOUT|socket/i.test(String(e.message || e)) || attempt === 4) throw e; await new Promise(r => setTimeout(r, 500 * Math.pow(2, attempt - 1))); }
  }
  throw lastErr;
}

const isTax = d => typeof d === "string" && /\btax\b/i.test(d);

// Collapse Call 1's per-item scores to items[{description, categoryId}].
function c1ItemsToLineItems(c1Items, cats) {
  const validSet = new Set(cats.map(c => c.id));
  const fallback = validSet.has(30426) ? 30426 : cats[0]?.id;
  return (c1Items || []).map(it => {
    const scores = (it.scores || []).filter(s => validSet.has(s.categoryId));
    if (scores.length === 0) return { description: it.description || "", categoryId: fallback };
    scores.sort((a, b) => (b.score || 0) - (a.score || 0));
    return { description: it.description || "", categoryId: scores[0].categoryId };
  });
}

// Derive multiCategoryLikely from per-item top-1 cats (ignoring Sales Tax).
function deriveMulti(c1Items, cats, modelFlag) {
  const validSet = new Set(cats.map(c => c.id));
  const cids = new Set();
  for (const it of (c1Items || [])) {
    if (isTax(it.description)) continue;
    const scores = (it.scores || []).filter(s => validSet.has(s.categoryId));
    if (scores.length === 0) continue;
    scores.sort((a, b) => (b.score || 0) - (a.score || 0));
    cids.add(scores[0].categoryId);
  }
  if (cids.size >= 2) return true;
  if (cids.size === 1) return false;
  return modelFlag === true;
}

function reconcilePrices(prices, totalCents) {
  const rows = prices.map(p => ({ ...p, priceCents: p.priceCents || 0 }));
  const taxIdx = rows.findIndex(r => isTax(r.description));
  const taxCents = taxIdx >= 0 ? rows[taxIdx].priceCents : 0;
  const targetNonTax = totalCents - taxCents;
  const rawNonTaxSum = rows.reduce((s, r, i) => s + (i === taxIdx ? 0 : r.priceCents), 0);
  if (rawNonTaxSum <= 0) return rows;
  const scale = targetNonTax / rawNonTaxSum;
  const reconciled = rows.map((r, i) => i === taxIdx ? r : { ...r, priceCents: Math.round(r.priceCents * scale) });
  const actualSum = reconciled.reduce((s, r) => s + r.priceCents, 0);
  const residual = totalCents - actualSum;
  if (residual !== 0) {
    let largestIdx = -1, largestVal = -1;
    for (let i = 0; i < reconciled.length; i++) { if (i === taxIdx) continue; if (reconciled[i].priceCents > largestVal) { largestVal = reconciled[i].priceCents; largestIdx = i; } }
    if (largestIdx >= 0) reconciled[largestIdx].priceCents += residual;
  }
  return reconciled;
}

async function runTwoCall(imageBytes, mimeType, cats) {
  const t0 = Date.now();
  const c1 = await apiCall([{ text: v10Call1Prompt(cats) }, { inlineData: { mimeType, data: imageBytes.toString("base64") } }], CALL1_SCHEMA);

  const multi = deriveMulti(c1.parsed.items, cats, c1.parsed.multiCategoryLikely);
  const validSet = new Set(cats.map(c => c.id));
  let categoryAmounts, route;
  let c3 = null;

  if (multi) {
    route = "multi-2call";
    const items = c1ItemsToLineItems(c1.parsed.items, cats);

    c3 = await apiCall([{ text: call3Prompt(items) }, { inlineData: { mimeType, data: imageBytes.toString("base64") } }], CALL3_SCHEMA);
    const prices = c3.parsed.prices || [];
    // Align lengths
    const alignedPrices = items.map((_, i) => prices[i] || { priceCents: 0 });
    const reconciled = reconcilePrices(alignedPrices, c1.parsed.amountCents || 0);

    const byCat = new Map();
    for (let i = 0; i < items.length; i++) {
      if (isTax(items[i].description)) continue;
      byCat.set(items[i].categoryId, (byCat.get(items[i].categoryId) || 0) + (reconciled[i]?.priceCents || 0));
    }
    const taxIdx = items.findIndex(it => isTax(it.description));
    if (taxIdx >= 0) {
      const taxCents = reconciled[taxIdx]?.priceCents || 0;
      let bestCat = null, bestCents = -1;
      for (const [cid, cents] of byCat) if (cents > bestCents) { bestCat = cid; bestCents = cents; }
      if (bestCat != null) byCat.set(bestCat, byCat.get(bestCat) + taxCents);
    }
    categoryAmounts = [...byCat.entries()].map(([categoryId, cents]) => ({ categoryId, amount: cents / 100 }));
  } else {
    route = "single-shortcircuit";
    let cid = c1.parsed.topChoice;
    if (!validSet.has(cid)) cid = validSet.has(30426) ? 30426 : cats[0]?.id;
    categoryAmounts = cid != null ? [{ categoryId: cid, amount: (c1.parsed.amountCents || 0) / 100 }] : [];
  }

  return {
    parsed: {
      merchant: c1.parsed.merchant,
      merchantLegalName: c1.parsed.merchantLegalName,
      date: c1.parsed.date,
      amount: (c1.parsed.amountCents || 0) / 100,
      categoryAmounts,
    },
    c1: c1.parsed,
    route,
    elapsedMs: Date.now() - t0,
    tokens: {
      c1: c1.tokens, c3: c3?.tokens,
      totalIn: (c1.tokens?.promptTokenCount || 0) + (c3?.tokens?.promptTokenCount || 0),
      totalOut: (c1.tokens?.candidatesTokenCount || 0) + (c3?.tokens?.candidatesTokenCount || 0),
    },
  };
}

function costOf(tokens) { const p = PRICING[LITE]; return (tokens.totalIn * p.in + tokens.totalOut * p.out) / 1_000_000; }

(async () => {
  const labels = JSON.parse(fs.readFileSync(path.join(ROOT, "test-data", "labels.json"), "utf8"));
  const subset = pickSubset(labels);
  const cats = TEST_CATEGORIES;
  const nm = Object.fromEntries(cats.map(c => [c.id, c.name]));
  console.log(`Validate 2-call V10: ${subset.length} receipts\n`);

  const rows = [];
  for (const label of subset) {
    const imgPath = path.join(ROOT, "test-data", "images", label.file);
    if (!fs.existsSync(imgPath)) continue;
    const img = fs.readFileSync(imgPath);
    const mime = mimeFor(label.file);
    process.stdout.write(`▶ ${label.file.slice(0, 44).padEnd(44)}  `);
    try {
      const res = await runTwoCall(img, mime, cats);
      const grade = gradeResult(label, res.parsed);
      const cost = costOf(res.tokens);
      rows.push({ file: label.file, label, result: res, grade, cost });
      const m = grade.merchant.pass ? "✓" : "✗";
      const d = grade.date.pass ? "✓" : "✗";
      const a = grade.amount.pass ? "✓" : "✗";
      const cset = grade.categoryAmounts.setMatch ? "✓" : "✗";
      const cshr = grade.categoryAmounts.shareMatch ? "✓" : "✗";
      const expected = label.categoryAmounts?.length === 1 ? label.categoryAmounts[0].categoryId : "multi";
      const got = res.parsed.categoryAmounts?.map(c => c.categoryId).join(",") || "-";
      console.log(`${res.route.padEnd(18)} m${m} d${d} a${a} cset${cset} cshr${cshr}  exp=${expected} got=${got}  $${cost.toFixed(5)} ${res.elapsedMs}ms`);
    } catch (e) {
      console.log(`FAIL: ${(e.message || String(e)).slice(0, 80)}`);
      rows.push({ file: label.file, label, err: e.message || String(e) });
    }
  }

  console.log("\n" + "═".repeat(96));
  console.log("V10 2-CALL VALIDATION");
  console.log("═".repeat(96));
  const ok = rows.filter(r => r.result);
  const pass = k => ok.filter(r => r.grade[k].pass).length;
  const passCA = k => ok.filter(r => r.grade.categoryAmounts[k]).length;
  const singlesOk = ok.filter(r => r.label.categoryAmounts?.length === 1 || (r.label.categoryAmounts == null && r.label.categoryId));
  const multiOk = ok.filter(r => r.label.categoryAmounts?.length > 1);
  const singlesCorrect = singlesOk.filter(r => { const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId; return r.result.parsed.categoryAmounts?.[0]?.categoryId === exp; }).length;
  const multiRouted = multiOk.filter(r => r.result.route === "multi-2call").length;
  const multiCset = multiOk.filter(r => r.grade.categoryAmounts.setMatch).length;
  const amazonCorrect = ok.filter(r => r.file.startsWith("amazon_")).filter(r => { const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId; return r.result.parsed.categoryAmounts?.[0]?.categoryId === exp; }).length;

  console.log(`Total receipts:     ${ok.length}/${rows.length}`);
  console.log(`Merchant pass:      ${pass("merchant")}/${ok.length}`);
  console.log(`Date pass:          ${pass("date")}/${ok.length}`);
  console.log(`Amount pass:        ${pass("amount")}/${ok.length}`);
  console.log(`cset pass:          ${passCA("setMatch")}/${ok.length}`);
  console.log(`cshr pass:          ${passCA("shareMatch")}/${ok.length}`);
  console.log(`Singles correct:    ${singlesCorrect}/${singlesOk.length}`);
  console.log(`Multi routed:       ${multiRouted}/${multiOk.length}`);
  console.log(`Multi cset correct: ${multiCset}/${multiOk.length}`);
  console.log(`Amazon correct:     ${amazonCorrect}/3`);
  console.log(`Combined:           ${singlesCorrect + multiRouted}`);

  const totalCost = ok.reduce((s, r) => s + r.cost, 0);
  const totalMs = ok.reduce((s, r) => s + r.result.elapsedMs, 0);
  console.log(`Total cost:         $${totalCost.toFixed(4)}  avg $${(totalCost / ok.length).toFixed(5)}`);
  console.log(`Total time:         ${(totalMs / 1000).toFixed(1)}s  avg ${Math.round(totalMs / ok.length)}ms`);

  // Per-cat breakdown
  console.log(`\nPer-category (single-cat):`);
  const perCat = new Map();
  for (const r of singlesOk) {
    const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId;
    const got = r.result.parsed.categoryAmounts?.[0]?.categoryId;
    if (!perCat.has(exp)) perCat.set(exp, { correct: 0, n: 0 });
    perCat.get(exp).n++;
    if (got === exp) perCat.get(exp).correct++;
  }
  for (const [cid, s] of perCat) console.log(`  ${(nm[cid] || cid).padEnd(32)} ${s.correct}/${s.n}`);

  console.log(`\n── Comparison to V10 3-call (round 4) ──`);
  console.log(`  V10 3-call:  singles 19/28, multi-routed 5/5, multi-cset 3/5, Amazon 1/3, combined 24`);
  console.log(`  V10 2-call:  singles ${singlesCorrect}/${singlesOk.length}, multi-routed ${multiRouted}/${multiOk.length}, multi-cset ${multiCset}/${multiOk.length}, Amazon ${amazonCorrect}/3, combined ${singlesCorrect+multiRouted}`);

  const outPath = path.join(ROOT, "results", `validate-v10-2call-${new Date().toISOString().replace(/[:.]/g, "-")}.json`);
  fs.writeFileSync(outPath, JSON.stringify(rows, null, 2));
  console.log(`\nSaved → ${path.relative(ROOT, outPath)}`);
})();
