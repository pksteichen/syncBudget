#!/usr/bin/env node
// Runs the V17 split-pipeline against the 51 single-cat new_bank receipts
// (the 53 app-sized DDG+wikimedia images minus 2 multi-cats). Reports
// per-field pass rates + new `items` Jaccard metric against labels with
// structured items arrays.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import "dotenv/config";
import { GoogleGenAI } from "@google/genai";
import { TEST_CATEGORIES } from "../src/categories.js";
import { gradeResult, summarize, pct } from "../src/grader.js";

const __filename = fileURLToPath(import.meta.url);
const ROOT = path.resolve(path.dirname(__filename), "..");
const DIR_IDX = process.argv.indexOf("--dir");
const IMG_DIR = DIR_IDX >= 0
  ? path.resolve(process.argv[DIR_IDX + 1])
  : path.join(ROOT, "test-data", "new_bank_app_sized");
const INCLUDE_MULTI = process.argv.includes("--include-multi");
const LABELS_PATH = path.join(ROOT, "test-data", "labels.json");
const LITE = "gemini-2.5-flash-lite";
const client = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

function categoryList(cats) {
  return cats.map(c => c.tag ? `  - id=${c.id} name="${c.name}" tag="${c.tag}"` : `  - id=${c.id} name="${c.name}"`).join("\n");
}

const C1_PROMPT = `Extract receipt data as JSON.

- merchant: the consumer brand (e.g. "McDonald's", "Target", "Costco"). Prefer the consumer brand over the legal operator entity. Preserve original language; don't translate. If the receipt contains "Sold by:" or "Seller" or "Order placed" or "Shipping & Handling", it's an online-marketplace (Amazon, eBay, Etsy, Walmart.com, Target.com). Set merchant to the platform.
- merchantLegalName: optional, only when the legal entity is clearly distinct from the consumer brand.
- date: YYYY-MM-DD ISO. Receipts often have multiple dates; prefer the transaction date over print/due dates. Default to US MM/DD/YYYY. Parse as DD/MM/YYYY only when the receipt shows a non-US signal: non-USD currency (€, £, ¥, HK$, SG$, RM, ₫, ₹), a non-US country or address format, non-English body text, or a known non-US merchant. A 5-digit US ZIP or US state abbreviation (CA, NY, TX, MN, …) anchors back to MM/DD. **If NO calendar date is visible anywhere on the receipt, return an empty string "" — do NOT invent or guess a date from context.** Partial dates without a year (e.g. a ship-tracker line showing only "Arriving tomorrow" or "Delivered Mon") are NOT dates — return empty in that case too.
- amountCents: INTEGER number of cents for the final paid total (tax and tip included). Ignore subtotal, pre-tax lines, and separate GST/VAT summary tables. Before finalizing, verify amountCents ≈ (subtotalCents + taxCents) within 2 cents — if not, re-examine the digits. Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are thousand separators; return the integer đồng value.
- itemNames: array of strings — the actual PURCHASED PRODUCTS on the receipt.

  INCLUDE in itemNames:
    - Every product the shopper bought, by its printed name.
    - Tax lines, one per tax line. These belong in itemNames as their own entry so downstream price-attribution can handle them.

  EXCLUDE from itemNames (these are NOT products — they're totals, shipping, fees, payment, or header info):
    - Subtotals / totals / grand totals.
    - Shipping, handling, delivery.
    - Discounts, promos, coupons, rewards.
    - Payment tenders.
    - Order metadata, action buttons.

  If the receipt is from an online order and you see a dedicated "Order Summary" section near the bottom with Subtotal/Shipping/Total rows, that section is SUMMARY only — do NOT list those rows as items.

- fullTranscript: array of strings — a FOCUSED transcript used by a downstream date/amount reconciliation step. Include ONLY these kinds of lines, in top-to-bottom order:
    - Any line containing a calendar date (transaction date, bill date, invoice date, due date, etc.)
    - Any line containing a monetary amount (item price, subtotal, tax, total, grand total, balance, amount paid, payment tender)
    - Lines labeled Subtotal / Total / Grand Total / Balance Due / Tax / GST / VAT
    - Merchant header lines (store name + city/country — first 2-3 lines of the receipt)
  SKIP these line types entirely (they add noise without helping reconciliation):
    - Barcodes, serial numbers, authorization codes, terminal IDs, cashier IDs
    - Signature lines, return policy / warranty boilerplate, rewards-program marketing
    - UPC numbers when they appear on their own line
    - Empty lines, separator dashes, ornamental footer text
  Keep the transcript tight — typically 10-30 lines is enough for reconciliation.`;

const C1_SCHEMA = {
  type: "object",
  properties: {
    merchant: { type: "string" }, merchantLegalName: { type: "string" },
    date: { type: "string" }, amountCents: { type: "integer" },
    itemNames: { type: "array", items: { type: "string" } },
    fullTranscript: { type: "array", items: { type: "string" } },
  },
  required: ["merchant", "date", "amountCents", "itemNames", "fullTranscript"],
};

// ── Call 1.5: text-only reconciliation of date + amount against transcript ──

function c1rPrompt(c1, localeHint = null) {
  const lines = (c1.fullTranscript || []).map((l, i) => `  ${i + 1}. ${l}`).join("\n");
  const localeLine = localeHint ? `Caller locale hint: "${localeHint}".` : "Default date locale: US MM/DD/YYYY unless the transcript shows a non-US signal (non-USD currency, non-US address/country, non-English body).";
  return `A receipt image was read in a first pass. You now have the first-pass merchant + date + amount AND the raw text transcript of the same receipt. Cross-check them and return reconciled values.

First-pass values:
  merchant = "${c1.merchant}"
  date = "${c1.date}"
  amountCents = ${c1.amountCents}

Raw transcript (line-numbered):
${lines}

Task:
  1. Verify the merchant. If the transcript contains "Sold by:" or "Seller" or "Order placed" or "Shipping & Handling", it's an online-marketplace order (Amazon, eBay, Etsy, Walmart.com, Target.com) — the merchant should be the PLATFORM, NOT a product brand (e.g. "QZIIW iPhone Charger") or the third-party seller named after "Sold by:" (e.g. "Meigo"). If the first-pass merchant is one of those product brands or third-party sellers and the transcript contains those marketplace signals, correct the merchant to the platform inferred from the receipt layout (Amazon orders show "Order placed" + "Item(s) Subtotal" + "Grand Total"; eBay shows order-number patterns; etc.). Otherwise keep the first-pass merchant.
  2. Find the transaction date line in the transcript (a line containing a calendar date). Prefer a purchase/transaction date over print timestamps or due dates. ${localeLine} **If NO calendar date appears anywhere in the transcript, return empty string "" — NEVER invent a date from training data or outside context. Ship-tracker phrases like "Arriving tomorrow" or "Delivered Mon" are NOT dates.**
  3. Find the final total amount line in the transcript (the line that represents what the shopper actually paid — typically labeled "TOTAL", "GRAND TOTAL", "BALANCE DUE", "AMOUNT PAID", "Total Sale"). Ignore "SUBTOTAL", "TAX", and any GST/VAT summary rows.
  4. Self-check: verify that amount ≈ subtotal + tax (±2 cents). If the first-pass amount fails that check but a different total line in the transcript passes it, use the transcript-supported value.
  5. If the transcript and first-pass agree, return the first-pass values unchanged. If they disagree, return the transcript-supported values and explain briefly in notes.

Return {merchant, date, amountCents, notes}.`;
}

const C1R_SCHEMA = {
  type: "object",
  properties: {
    merchant: { type: "string" },
    date: { type: "string" },
    amountCents: { type: "integer" },
    notes: { type: "string" },
  },
  required: ["merchant", "date", "amountCents"],
};

function c2Prompt(itemNames, cats) {
  const listed = itemNames.map((n, i) => `  ${i + 1}. ${n}`).join("\n");
  return `For each purchased item below, determine its best-fit BudgeTrak category. Return items[] with {description, scores:[{categoryId, score, reason}]} in the SAME ORDER as the input, plus multiCategoryLikely and topChoice.

Items:
${listed}

For each item:
  Step 1 (ITEM): identify the literal thing.
  Step 2 (FUNCTION): describe what it's used for.
  Step 3 (DOMAIN): name the real-world domain in 1-3 nouns.
  Step 4 (SCAN): for each category, check whether its NAME contains a noun matching the Step 3 domain directly or via a close synonym.
  Step 5 (SCORE): score up to 3 categories 0-100.
    - Direct-match domain word = 80-100.
    - Close synonym = 50-75.
    - Weak or tangential = 20-50.
    - Tie-break: directly-named domain beats generic ("Supplies", "Goods", "Items").

Global rules:
  - "Other" is reserved for items no other category name plausibly describes.
  - Tax lines go into the dominant non-tax category.
  - Do NOT invent categoryIds.

Set multiCategoryLikely = true when items' top-1 categories span 2+ distinct domains. Set topChoice (required when false) = the single best-fit for the whole receipt.

Categories:
${categoryList(cats)}`;
}

const C2_SCHEMA = {
  type: "object",
  properties: {
    items: { type: "array", items: { type: "object", properties: {
      description: { type: "string" },
      scores: { type: "array", items: { type: "object", properties: { categoryId: { type: "integer" }, score: { type: "number" }, reason: { type: "string" } }, required: ["categoryId", "score"] } },
    }, required: ["description", "scores"] } },
    multiCategoryLikely: { type: "boolean" },
    topChoice: { type: "integer" },
  },
  required: ["items"],
};

function mimeFor(f) { const e = path.extname(f).toLowerCase(); if (e === ".png") return "image/png"; if (e === ".webp") return "image/webp"; return "image/jpeg"; }

// Mirror the app's ReceiptOcrService retry: up to 4 attempts with
// exponential backoff (0.5s / 1s / 2s) on transient Gemini errors. This
// matches the production client's behavior so harness results reflect
// what users actually see (users don't see raw 503s — they see retries).
const TRANSIENT_RE = /503|UNAVAILABLE|overloaded|429|RESOURCE_EXHAUSTED|deadline|fetch failed|network|ECONNRESET|ETIMEDOUT|socket/i;
async function generateWithRetry(req) {
  const maxAttempts = 4;
  let lastErr;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await client.models.generateContent(req);
    } catch (e) {
      lastErr = e;
      const msg = e?.message || String(e);
      if (!TRANSIENT_RE.test(msg) || attempt === maxAttempts) throw e;
      const backoffMs = 500 * (1 << (attempt - 1));
      process.stderr.write(`  (retry ${attempt}/${maxAttempts - 1} after ${backoffMs}ms: ${msg.slice(0, 60)})\n`);
      await new Promise(r => setTimeout(r, backoffMs));
    }
  }
  throw lastErr;
}

const isTax = d => typeof d === "string" && /\btax\b/i.test(d);

function deriveMulti(items, cats, modelFlag) {
  const validSet = new Set(cats.map(c => c.id));
  const cids = new Set();
  for (const it of (items || [])) {
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

async function runSplit(imgBytes, mimeType, cats) {
  const c1Res = await generateWithRetry({
    model: LITE,
    contents: [{ role: "user", parts: [
      { text: C1_PROMPT },
      { inlineData: { mimeType, data: imgBytes.toString("base64") } },
    ] }],
    config: { responseMimeType: "application/json", responseSchema: C1_SCHEMA, temperature: 0 },
  });
  const c1 = JSON.parse(c1Res.text);
  const itemNames = c1.itemNames || [];

  // Call 1.5: text-only reconciliation. Cheap (~2k tokens, no image)
  // but forces a second, independent pass over the receipt text.
  let reconciled = { merchant: c1.merchant, date: c1.date, amountCents: c1.amountCents, notes: null };
  if ((c1.fullTranscript || []).length > 0) {
    try {
      const c1rRes = await generateWithRetry({
        model: LITE,
        contents: [{ role: "user", parts: [{ text: c1rPrompt(c1) }] }],
        config: { responseMimeType: "application/json", responseSchema: C1R_SCHEMA, temperature: 0 },
      });
      reconciled = JSON.parse(c1rRes.text);
    } catch (e) {
      // If reconciliation fails, fall back to C1 values silently.
      reconciled = { merchant: c1.merchant, date: c1.date, amountCents: c1.amountCents, notes: `c1r-error: ${e.message?.slice(0, 60)}` };
    }
  }

  let c2 = { items: [], topChoice: null, multiCategoryLikely: false };
  if (itemNames.length > 0) {
    const c2Res = await generateWithRetry({
      model: LITE,
      contents: [{ role: "user", parts: [
        { text: c2Prompt(itemNames, cats) },
        { inlineData: { mimeType, data: imgBytes.toString("base64") } },
      ] }],
      config: { responseMimeType: "application/json", responseSchema: C2_SCHEMA, temperature: 0 },
    });
    c2 = JSON.parse(c2Res.text);
  }

  const multi = deriveMulti(c2.items, cats, c2.multiCategoryLikely);
  const validSet = new Set(cats.map(c => c.id));

  // Build per-line lineItems for the new grader.items metric: one entry
  // per c2 item, price split evenly (we don't have prices at this stage).
  const lineItems = (c2.items || []).map((it, idx) => {
    const scores = (it.scores || []).filter(s => validSet.has(s.categoryId));
    scores.sort((a,b) => (b.score||0) - (a.score||0));
    const cid = scores[0]?.categoryId;
    return { name: itemNames[idx] ?? it.description, price: 0, ...(cid != null ? { categoryId: cid } : {}) };
  });

  let categoryAmounts;
  if (multi) {
    const byCat = new Map();
    for (const it of (c2.items || [])) {
      if (isTax(it.description)) continue;
      const scores = (it.scores || []).filter(s => validSet.has(s.categoryId));
      if (scores.length === 0) continue;
      scores.sort((a,b) => (b.score||0) - (a.score||0));
      byCat.set(scores[0].categoryId, (byCat.get(scores[0].categoryId) || 0) + 1);
    }
    categoryAmounts = [...byCat.entries()].map(([cid]) => ({ categoryId: cid, amount: (reconciled.amountCents || 0) / 100 / byCat.size }));
  } else {
    const cid = c2.topChoice ?? 30426;
    categoryAmounts = [{ categoryId: cid, amount: (reconciled.amountCents || 0) / 100 }];
  }

  return {
    parsed: { merchant: reconciled.merchant || c1.merchant, merchantLegalName: c1.merchantLegalName, date: reconciled.date, amount: (reconciled.amountCents || 0) / 100, categoryAmounts, lineItems },
    c1, c2, multi, reconciled,
  };
}

(async () => {
  const allLabels = JSON.parse(fs.readFileSync(LABELS_PATH, "utf8"));
  const newBankFiles = new Set(fs.readdirSync(IMG_DIR));

  // --only "file1,file2,file3" limits the run to those exact label.file values.
  const onlyIdx = process.argv.indexOf("--only");
  const onlyList = onlyIdx >= 0 ? new Set(process.argv[onlyIdx + 1].split(",").map(s => s.trim())) : null;

  const subset = allLabels.filter(l =>
    newBankFiles.has(l.file) &&
    (INCLUDE_MULTI || !((l.categoryAmounts || []).length > 1)) &&
    (!onlyList || onlyList.has(l.file))
  );
  const multiCount = subset.filter(l => (l.categoryAmounts || []).length > 1).length;
  console.log(`Running on ${subset.length} receipts from ${path.relative(ROOT, IMG_DIR)} (${subset.length - multiCount} single-cat + ${multiCount} multi-cat)\n`);

  const cats = TEST_CATEGORIES;
  const nm = Object.fromEntries(cats.map(c => [c.id, c.name]));
  const rows = [];
  let i = 0;

  for (const label of subset) {
    i++;
    const imgPath = path.join(IMG_DIR, label.file);
    const img = fs.readFileSync(imgPath);
    const mime = mimeFor(label.file);
    process.stdout.write(`[${String(i).padStart(2)}/${subset.length}] ${label.file.slice(0, 40).padEnd(40)}  `);
    const t0 = Date.now();
    try {
      const res = await runSplit(img, mime, cats);
      const elapsedMs = Date.now() - t0;
      const grade = gradeResult(label, res.parsed);
      rows.push({ file: label.file, label, ...res, grade, elapsedMs });
      const exp = label.categoryAmounts?.[0]?.categoryId ?? label.categoryId;
      const got = res.parsed.categoryAmounts?.[0]?.categoryId;
      const marks = [
        grade.merchant.pass ? "M" : "m",
        grade.date.skipped ? "·" : (grade.date.pass ? "D" : "d"),
        grade.amount.pass ? "A" : "a",
        grade.category.pass ? "C" : "c",
      ].join("");
      const c1m = res.c1?.merchant, c1d = res.c1?.date, c1a = res.c1?.amountCents;
      const rm = res.reconciled?.merchant, rd = res.reconciled?.date, ra = res.reconciled?.amountCents;
      const recChanged = (c1m !== rm) || (c1d !== rd) || (c1a !== ra);
      const recMark = recChanged ? ` [R: m='${c1m}'→'${rm}', d=${c1d}→${rd}, a=${c1a}→${ra}]` : "";
      console.log(`${marks}  exp=${nm[exp] || exp} got=${nm[got] || got}  (${elapsedMs}ms)${recMark}`);
    } catch (e) {
      const elapsedMs = Date.now() - t0;
      console.log(`FAIL: ${(e.message || String(e)).slice(0, 60)} (${elapsedMs}ms)`);
      rows.push({ file: label.file, label, err: e.message, elapsedMs });
    }
  }

  const summary = summarize(rows.map(r => ({ grade: r.grade, elapsedMs: r.elapsedMs })).filter(r => r.grade));
  console.log(`\n═══ SUMMARY (${rows.filter(r => r.parsed).length} ok / ${rows.filter(r => r.err).length} fail) ═══`);
  console.log(`  merchant  ${pct(summary.merchant.pass, summary.merchant.total)}  (${summary.merchant.pass}/${summary.merchant.total})`);
  console.log(`  date      ${pct(summary.date.pass, summary.date.total)}  (${summary.date.pass}/${summary.date.total})`);
  console.log(`  amount    ${pct(summary.amount.pass, summary.amount.total)}  (${summary.amount.pass}/${summary.amount.total})`);
  console.log(`  category  ${pct(summary.category.pass, summary.category.total)}  (${summary.category.pass}/${summary.category.total})`);
  if (summary.multiCategorySet.total > 0) {
    console.log(`  multi-cat set  ${pct(summary.multiCategorySet.pass, summary.multiCategorySet.total)}  (${summary.multiCategorySet.pass}/${summary.multiCategorySet.total})`);
  }
  const avgMs = summary.tests ? Math.round(summary.elapsedMsTotal / summary.tests) : 0;
  console.log(`  avg latency  ${avgMs}ms`);

  // Per-category breakdown
  const perCat = new Map();
  for (const r of rows.filter(r => r.parsed)) {
    const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId;
    if (!perCat.has(exp)) perCat.set(exp, { correct: 0, n: 0, files: [] });
    const got = r.parsed.categoryAmounts?.[0]?.categoryId;
    const bucket = perCat.get(exp);
    bucket.n++;
    if (got === exp) bucket.correct++;
    else bucket.files.push(`${r.file} → got=${nm[got] || got}`);
  }
  console.log(`\nPer-expected-category:`);
  for (const [cid, s] of [...perCat].sort((a,b) => (b[1].n - a[1].n))) {
    console.log(`  ${(nm[cid] || String(cid)).padEnd(30)} ${s.correct}/${s.n}`);
    for (const f of s.files.slice(0, 3)) console.log(`      miss: ${f}`);
  }

  const outPath = path.join(ROOT, "results", `new-bank-v17-${new Date().toISOString().replace(/[:.]/g, "-")}.json`);
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, JSON.stringify(rows, null, 2));
  console.log(`\nSaved → ${path.relative(ROOT, outPath)}`);
})();
