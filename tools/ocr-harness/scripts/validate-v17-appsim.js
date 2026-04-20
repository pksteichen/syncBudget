#!/usr/bin/env node
// V15: split-pipeline test. Image-reading (Call 1) is separate from
// categorization (Call 2, text-only). Hypothesis: decoupling these means
// JPEG encoder variance only affects which item names are extracted (a
// more robust OCR task), not which categories they get (a text-reasoning
// task over stable strings).
//
//   Call 1 (image): merchant, merchantLegalName?, date, amountCents,
//                   itemNames[].
//   Call 2 (text):  categorise each item name. items[] with scores,
//                   multiCategoryLikely, topChoice.
//
// Temperature 0 on text-only Call 2 should be deterministic across runs.

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
const client = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

function pickSubset(labels) {
  const nonVN = labels.filter(l => !l.file.startsWith("mcocr_"));
  const subset = [];
  const singles = nonVN.filter(l => l.categoryAmounts?.length === 1 || (l.categoryAmounts == null && l.categoryId));
  const byCat = new Map();
  for (const s of singles) { const cid = s.categoryAmounts?.[0]?.categoryId ?? s.categoryId; if (!byCat.has(cid)) byCat.set(cid, []); byCat.get(cid).push(s); }
  for (const [, files] of byCat) { files.sort((a,b) => a.file.localeCompare(b.file)); subset.push(...files.slice(0, 5)); }
  const multi = nonVN.filter(l => l.categoryAmounts?.length > 1).sort((a,b) => (b.categoryAmounts.length - a.categoryAmounts.length) || a.file.localeCompare(b.file)).slice(0, 5);
  subset.push(...multi);
  return subset;
}

function categoryList(cats) {
  return cats.map(c => c.tag ? `  - id=${c.id} name="${c.name}" tag="${c.tag}"` : `  - id=${c.id} name="${c.name}"`).join("\n");
}

// ── Call 1: image → header + item names ──────────────────────────────

const C1_PROMPT = `Extract receipt data as JSON.

- merchant: the consumer brand (e.g. "McDonald's", "Target", "Costco"). Prefer the consumer brand over the legal operator entity. Preserve original language; don't translate.
- merchantLegalName: optional, only when the legal entity is clearly distinct from the consumer brand.
- date: YYYY-MM-DD ISO. Receipts often have multiple dates; prefer the transaction date over print/due dates. DD/MM locales: Malaysia, Singapore, Vietnam, most of Europe. MM/DD: US.
- amountCents: INTEGER number of cents for the final paid total (tax and tip included). Ignore subtotal, pre-tax lines, and separate GST/VAT summary tables. Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are thousand separators; return the integer đồng value.
- itemNames: array of strings — the actual PURCHASED PRODUCTS on the receipt.

  INCLUDE in itemNames:
    - Every product the shopper bought, by its printed name (e.g. "BOSCH BE964H Blue Ceramic Disc Brake", "QZIIW iPhone 17 Pro Max Charger", "2% MILK 1GAL", "Tzatziki Dip"). On online-order receipts the product is usually shown alongside a thumbnail image; that's the one you want.
    - Tax lines, one per tax line (e.g. "Sales Tax", "Estimated tax to be collected", "GST"). These belong in itemNames as their own entry so downstream price-attribution can handle them.

  EXCLUDE from itemNames (these are NOT products — they're totals, shipping, fees, payment, or header info):
    - Subtotals / totals / grand totals: "Subtotal", "Item(s) Subtotal", "Total", "Grand Total", "Total before tax", "Total after tax", "Order total".
    - Shipping, handling, delivery: "Shipping", "Shipping & Handling", "Delivery", "S&H".
    - Discounts, promos, coupons, rewards: "Discount", "Savings", "Promotion", "Coupon", "Member discount", "Target Circle".
    - Payment tenders: "Visa ending in 1234", "Cash", "Credit", "Change", "Amount tendered".
    - Order metadata: order numbers, tracking numbers, addresses, "Sold by" lines.
    - Action buttons from online receipts ("Track package", "Cancel items", "Buy again", etc.).

  If the receipt is from an online order (Amazon, Target pickup, DoorDash, etc.) and you see a dedicated "Order Summary" section near the bottom with Subtotal/Shipping/Total rows, that section is SUMMARY only — do NOT list those rows as items. The actual products are usually shown earlier on the receipt next to their images and quantities.`;

const C1_SCHEMA = {
  type: "object",
  properties: {
    merchant: { type: "string" }, merchantLegalName: { type: "string" },
    date: { type: "string" }, amountCents: { type: "integer" },
    itemNames: { type: "array", items: { type: "string" } },
  },
  required: ["merchant", "date", "amountCents", "itemNames"],
};

// ── Call 2: text-only → per-item category scoring ────────────────────

function c2Prompt(itemNames, cats) {
  const listed = itemNames.map((n, i) => `  ${i + 1}. ${n}`).join("\n");
  return `For each of the following purchased items (extracted from a receipt), determine its best-fit BudgeTrak category. Return items[] with {description, scores:[{categoryId, score, reason}]} in the SAME ORDER as the input, plus multiCategoryLikely and topChoice.

Items:
${listed}

For each item, follow these 5 steps:
  Step 1 (ITEM): identify the literal thing the name refers to.
  Step 2 (FUNCTION): describe what it's used for or who uses it.
  Step 3 (DOMAIN): name the real-world domain in 1-3 nouns (e.g. "vehicle repair part", "pet accessory", "prepared meal").
  Step 4 (SCAN): for each category, check whether its NAME contains a noun matching the Step 3 domain directly OR via a close synonym.
  Step 5 (SCORE): score up to 3 categories 0-100.
    - A category whose name DIRECTLY contains a word naming the item's domain (e.g. "Transportation" for a car part, "Pet" for a pet accessory, "Phone" for a phone charger) = 80-100.
    - A category whose name doesn't contain the domain word but is a close synonym = 50-75.
    - A weak or tangential fit = 20-50.
    - Tie-break: when a category whose name contains a generic word ("Supplies", "Goods", "Items", "General", "Miscellaneous") competes with a category whose name directly names the domain, the directly-named category scores higher.

Global rules:
  - "Other" is reserved for items that no other category name plausibly describes.
  - Tax lines go into the category that receives the most non-tax weight from the rest of the receipt (you won't know amounts yet — just pick the dominant category from the other items).
  - Do NOT invent categoryIds not in the list.

Set multiCategoryLikely = true when the items' top-1 categories span 2+ distinct real-world domains. Set topChoice (required when false) = the single best-fit for the whole receipt.

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

// ── Pipeline ──────────────────────────────────────────────────────────

function mimeFor(f) { const e = path.extname(f).toLowerCase(); if (e === ".png") return "image/png"; if (e === ".webp") return "image/webp"; return "image/jpeg"; }

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
  // Call 1: image → header + item names
  const c1Res = await client.models.generateContent({
    model: LITE,
    contents: [{ role: "user", parts: [
      { text: C1_PROMPT },
      { inlineData: { mimeType, data: imgBytes.toString("base64") } },
    ] }],
    config: { responseMimeType: "application/json", responseSchema: C1_SCHEMA, temperature: 0 },
  });
  const c1 = JSON.parse(c1Res.text);
  const itemNames = c1.itemNames || [];

  // Call 2: text → per-item categorization
  let c2 = { items: [], topChoice: null, multiCategoryLikely: false };
  if (itemNames.length > 0) {
    const c2Res = await client.models.generateContent({
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
    categoryAmounts = [...byCat.entries()].map(([cid]) => ({ categoryId: cid, amount: (c1.amountCents || 0) / 100 / byCat.size }));
  } else {
    const cid = c2.topChoice ?? 30426;
    categoryAmounts = [{ categoryId: cid, amount: (c1.amountCents || 0) / 100 }];
  }

  return {
    parsed: { merchant: c1.merchant, merchantLegalName: c1.merchantLegalName, date: c1.date, amount: (c1.amountCents || 0) / 100, categoryAmounts },
    c1, c2, multi,
  };
}

(async () => {
  const labels = JSON.parse(fs.readFileSync(path.join(ROOT, "test-data", "labels.json"), "utf8"));
  const subset = pickSubset(labels);
  const cats = TEST_CATEGORIES;
  const nm = Object.fromEntries(cats.map(c => [c.id, c.name]));

  console.log(`V15 split-pipeline on ${subset.length} receipts\n`);

  const rows = [];
  for (const label of subset) {
    const imgPath = path.join(ROOT, "test-data", "app_sim", label.file);
    if (!fs.existsSync(imgPath)) continue;
    const img = fs.readFileSync(imgPath);
    const mime = mimeFor(label.file);
    process.stdout.write(`▶ ${label.file.slice(0, 44).padEnd(44)}  `);
    try {
      const res = await runSplit(img, mime, cats);
      const grade = gradeResult(label, res.parsed);
      rows.push({ file: label.file, label, ...res, grade });
      const exp = label.categoryAmounts?.length === 1 ? label.categoryAmounts[0].categoryId : "multi";
      const got = res.parsed.categoryAmounts.map(c => c.categoryId).join(",");
      const cset = grade.categoryAmounts.setMatch ? "✓" : "✗";
      console.log(`${res.multi ? "multi " : "single"} cset${cset}  exp=${exp} got=${got}`);
    } catch (e) {
      console.log(`FAIL: ${(e.message || String(e)).slice(0, 60)}`);
      rows.push({ file: label.file, label, err: e.message });
    }
  }

  console.log("\n═══ V15 SUMMARY ═══");
  const ok = rows.filter(r => r.parsed);
  const singlesOk = ok.filter(r => r.label.categoryAmounts?.length === 1 || (r.label.categoryAmounts == null && r.label.categoryId));
  const multiOk = ok.filter(r => r.label.categoryAmounts?.length > 1);
  const singlesCorrect = singlesOk.filter(r => { const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId; return r.parsed.categoryAmounts?.[0]?.categoryId === exp; }).length;
  const multiRouted = multiOk.filter(r => r.multi).length;
  const multiCset = multiOk.filter(r => r.grade.categoryAmounts.setMatch).length;
  const amazonCorrect = ok.filter(r => r.file.startsWith("amazon_")).filter(r => { const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId; return r.parsed.categoryAmounts?.[0]?.categoryId === exp; }).length;

  console.log(`Singles:      ${singlesCorrect}/${singlesOk.length}`);
  console.log(`Multi routed: ${multiRouted}/${multiOk.length}`);
  console.log(`Multi cset:   ${multiCset}/${multiOk.length}`);
  console.log(`Amazon:       ${amazonCorrect}/3`);
  console.log(`Combined:     ${singlesCorrect + multiRouted}`);

  console.log(`\nPer-category (single-cat):`);
  const perCat = new Map();
  for (const r of singlesOk) {
    const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId;
    const got = r.parsed.categoryAmounts?.[0]?.categoryId;
    if (!perCat.has(exp)) perCat.set(exp, { correct: 0, n: 0 });
    perCat.get(exp).n++;
    if (got === exp) perCat.get(exp).correct++;
  }
  for (const [cid, s] of perCat) console.log(`  ${(nm[cid] || cid).padEnd(32)} ${s.correct}/${s.n}`);

  // Amazon receipts detail
  console.log(`\nAmazon receipts detail:`);
  for (const r of ok.filter(row => row.file.startsWith("amazon_"))) {
    const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId;
    const got = r.parsed.categoryAmounts?.[0]?.categoryId;
    const mark = got === exp ? "✓" : "✗";
    console.log(`  ${r.file.padEnd(32)} ${mark}  exp=${nm[exp]}(${exp}) got=${nm[got] || "?"}(${got})`);
    console.log(`    c1.itemNames = ${JSON.stringify(r.c1.itemNames)}`);
    const c2top = r.c2.items?.[0];
    const topScore = [...(c2top?.scores || [])].sort((a,b)=>(b.score||0)-(a.score||0))[0];
    if (topScore) console.log(`    c2 first-item top: ${nm[topScore.categoryId]}(${topScore.score}) — "${topScore.reason?.slice(0,80)}"`);
  }

  const outPath = path.join(ROOT, "results", `validate-v15-split-${new Date().toISOString().replace(/[:.]/g, "-")}.json`);
  fs.writeFileSync(outPath, JSON.stringify(rows, null, 2));
  console.log(`\nSaved → ${path.relative(ROOT, outPath)}`);
})();
