#!/usr/bin/env node
// Round 1: iterate 10 Call 1 prompt variants derived from v3d.
//
// v3d's strengths (keep): candidates[{categoryId,reason}]+topChoice schema
//   forces visible enumeration; correctly picked Phone/Internet/Computer for
//   the iPhone charger and Transportation/Gas for tie rod boots.
// v3d's weaknesses (target):
//   - Still picked Home Supplies for brake pads ("it's a car part, so home supply")
//   - 2 of 5 multi-cat receipts wrongly short-circuited
//   - Never placed work boots in Employment Expenses (user-preference driven)
//   - Never placed stationery in Other (user accepts losing these)
//
// Hard constraint: NO references to user's actual categories (Phone/Internet,
// Transportation/Gas, Home Supplies, Groceries, Restaurants, Clothes, etc.) or
// receipts in our test bank (iPhone, brake pads, stationery, work boots, etc.).
// Examples use fictional categories + fictional products.
//
// All variants use v3d's schema. Call 2 + Call 3 unchanged.
//
// Usage: node scripts/iterate-nopresel-round1.js

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

// ─── Subset selection (identical to test-lite-nopresel.js) ────────────

function pickSubset(labels) {
  const nonVN = labels.filter(l => !l.file.startsWith("mcocr_"));
  const subset = [];
  const singles = nonVN.filter(l => l.categoryAmounts?.length === 1 || (l.categoryAmounts == null && l.categoryId));
  const byCat = new Map();
  for (const s of singles) {
    const cid = s.categoryAmounts?.[0]?.categoryId ?? s.categoryId;
    if (!byCat.has(cid)) byCat.set(cid, []);
    byCat.get(cid).push(s);
  }
  for (const [, files] of byCat) {
    files.sort((a, b) => a.file.localeCompare(b.file));
    subset.push(...files.slice(0, 5));
  }
  const multi = nonVN.filter(l => l.categoryAmounts?.length > 1)
    .sort((a, b) => (b.categoryAmounts.length - a.categoryAmounts.length) || a.file.localeCompare(b.file))
    .slice(0, 5);
  subset.push(...multi);
  return subset;
}

function categoryList(cats) {
  return cats.map(c => c.tag ? `  - id=${c.id} name="${c.name}" tag="${c.tag}"` : `  - id=${c.id} name="${c.name}"`).join("\n");
}

// ─── Call 1 variants ─────────────────────────────────────────────────

const HEADER_BASE = `Extract receipt header data as JSON.

- merchant: the consumer brand (e.g. "McDonald's", "Target", "Costco"). Prefer the consumer brand over the legal operator entity. Preserve original language; don't translate.
- merchantLegalName: optional, only when the legal entity is clearly distinct from the consumer brand.
- date: YYYY-MM-DD ISO. Receipts often have multiple dates; prefer the transaction date over print/due dates. DD/MM locales: Malaysia, Singapore, Vietnam, most of Europe. MM/DD: US.
- amountCents: INTEGER number of cents for the final paid total (tax and tip included). Ignore subtotal, pre-tax lines, and separate GST/VAT summary tables. Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are thousand separators; return the integer đồng value.`;

const ROUTING_INTRO = `
Also return two routing hints:
- multiCategoryLikely: true if the items clearly span 2+ of the user's categories (e.g. a mixed retail trip). Set false when nearly all items fit one category (single-item online order, fast-food order, drugstore visit).
- When multiCategoryLikely is false, return candidates: an array of 1-3 best-fit category ids for the whole receipt, each with a brief reason (≤15 words). List in descending order of fit. Also return topChoice: the single best-fit category id (must match candidates[0].categoryId).`;

const RULES_BASE = `
How to pick a category:
  1. Read each category's NAME as a description of what belongs there. The name is your primary signal.
  2. Match by what the item IS (its function or product type), not by where it was purchased. The same store can sell items that belong in many different categories.
  3. When a category name lists multiple things (e.g. "X/Y/Z"), treat ALL of them as in scope — the name is telling you that category covers X, Y, AND Z.
  4. Prefer a narrow/specialty category over a broad catch-all when an item plausibly fits both.
  5. "Other" is reserved for items that no other category name plausibly describes.
  6. Do NOT invent categoryIds not in the list.`;

const VARIANTS = [
  {
    id: "T1_control",
    note: "v3d verbatim (control)",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      return HEADER_BASE + ROUTING_INTRO + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T2_multi_safeguard",
    note: "explicit multi-cat safeguard tied to candidate diversity",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const extra = `
  7. BEFORE finalising, check your candidates. If your top 2 candidates cover visibly different real-world scopes (e.g. one covers edible things and another covers wearable things; one covers vehicles and another covers pets), that's a sign the receipt is multi-category. In that case, set multiCategoryLikely=true and OMIT candidates and topChoice.`;
      return HEADER_BASE + ROUTING_INTRO + RULES_BASE + extra + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T3_fictional_compound_example",
    note: "fictional example of compound-name → physical goods coverage",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const extra = `

Fictional illustrations of rule 3 (NOT your actual categories — your real list is below):
  - A hypothetical category "Travel/Lodging" covers airline tickets, hotel stays, AND a suitcase you bought for a trip. Any noun in the name (travel, lodging) brings related physical goods in scope.
  - A hypothetical category "Pet Care/Veterinary" covers dog food, a leash, AND vet visits.
  - A hypothetical category "Books/Reading" covers paperback novels, textbooks, AND e-readers.
The same principle applies to your real category names — read every noun in the name as a type-hint including for physical goods.`;
      return HEADER_BASE + ROUTING_INTRO + RULES_BASE + extra + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T4_cite_word",
    note: "reasoning must cite the exact word in the category name",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const routingStrict = `
Also return two routing hints:
- multiCategoryLikely: true if the items clearly span 2+ of the user's categories (e.g. a mixed retail trip). Set false when nearly all items fit one category (single-item online order, fast-food order, drugstore visit).
- When multiCategoryLikely is false, return candidates: an array of 1-3 best-fit category ids for the whole receipt. Each reason (≤15 words) MUST cite the exact word from the category name that matches the item (e.g. reason: "Matches word 'Lodging' in category name — it's a hotel."). If you cannot cite a matching word from any category name, use "Other". Also return topChoice (matches candidates[0].categoryId).`;
      return HEADER_BASE + routingStrict + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T5_anti_catchall",
    note: "explicit anti-catch-all bias",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const extra = `
  7. Anti-catch-all: if multiple categories fit an item, do NOT default to the one with the broadest or most generic name. Pick the category whose name most SPECIFICALLY describes the item. Broad-sounding categories should be picked only when no specific name fits.`;
      return HEADER_BASE + ROUTING_INTRO + RULES_BASE + extra + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T6_fictional_full_example",
    note: "fictional walk-through of the reasoning",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const extra = `

Fictional worked example (NOT your real data):
  Imagine the receipt is for a single item: "TENNIS RACQUET". Imagine the category list contains "Sports/Recreation", "Books/Reading", "Garden/Lawn", and "Other".
  Step 1: what IS the item? A tennis racquet — a piece of sports equipment.
  Step 2: scan category NAMES for matching words. "Sports" in "Sports/Recreation" matches directly.
  Step 3: reason: "The word 'Sports' in the name covers a tennis racquet."
  Step 4: topChoice = id of "Sports/Recreation". Do NOT pick "Other" just because sports equipment feels generic.
Apply the same three steps to your real receipt and your real category list.`;
      return HEADER_BASE + ROUTING_INTRO + RULES_BASE + extra + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T7_item_first",
    note: "pre-step: identify primary product type, then scan names",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const routingAlt = `
Also return routing hints. Before picking a category, follow this procedure:
  Step A: identify the PRIMARY PRODUCT TYPE on the receipt in 1-3 generic nouns (e.g. "footwear", "pet food", "electronic device"). If items clearly span multiple product types, set multiCategoryLikely=true and stop.
  Step B: for each category in the list, check whether its NAME contains any noun matching or closely synonymous to the product type from Step A.
  Step C: the winning category is the one whose name most directly names the product type. Tie-break toward more specific names.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice.`;
      return HEADER_BASE + routingAlt + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T8_two_cands",
    note: "reduce to 2 candidates (tighter competition)",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const routingTight = `
Also return two routing hints:
- multiCategoryLikely: true if the items clearly span 2+ of the user's categories. Set false when nearly all items fit one category.
- When multiCategoryLikely is false, return candidates: an array of EXACTLY 2 best-fit category ids for the whole receipt with reasons (≤15 words each). Also return topChoice (matches candidates[0].categoryId). The two candidates should represent genuine competition — if only one category plausibly fits, repeat the top choice in slot 2 with reason "no real competitor".`;
      return HEADER_BASE + routingTight + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T9_multi_threshold",
    note: "explicit multi threshold: 3+ items spanning 2+ scopes",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const extra = `
  7. multiCategoryLikely threshold: set true when the receipt has 3+ distinct purchased items AND those items span 2+ real-world scopes (edible, wearable, electronic, consumable-household, vehicle-related, medicinal, entertainment, etc.). A single product type — no matter how many line items — is single-category.`;
      return HEADER_BASE + ROUTING_INTRO + RULES_BASE + extra + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T10_combo",
    note: "T2 (multi safeguard) + T3 (fictional example) + T4 (cite word)",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const routingCombo = `
Also return two routing hints:
- multiCategoryLikely: true if the items clearly span 2+ of the user's categories. Set false when nearly all items fit one category.
- When multiCategoryLikely is false, return candidates: 1-3 best-fit category ids, descending by fit. Each reason (≤15 words) MUST cite the exact word from the category name that matches the item. Also return topChoice (matches candidates[0].categoryId).
- If your top 2 candidates cover visibly different real-world scopes (edible vs wearable vs electronic vs vehicle-related etc.), set multiCategoryLikely=true instead and OMIT candidates.`;
      const extra = `

Fictional illustration (NOT your actual categories):
  - "Travel/Lodging" covers airline tickets, hotel stays, AND a suitcase.
  - "Pet Care/Veterinary" covers dog food, a leash, AND vet visits.
  - "Books/Reading" covers paperback novels AND e-readers.
Read every noun in each category name as a type-hint including for physical goods.`;
      return HEADER_BASE + routingCombo + RULES_BASE + extra + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },
];

// ─── Call 2 + Call 3 (unchanged from v3d / v2) ───────────────────────

function call2Prompt(cats, preselected) {
  const preselectNudge = preselected
    ? `\n\n  - The categories below are pre-selected by the shopper for this receipt. Try to cover as many of them as reasonably fit — the shopper expects to see items in these specific buckets. But skip a category if no item on the receipt plausibly fits it; never force-fit an item into a bucket that clearly doesn't match (the shopper may have pre-selected a category by accident).`
    : "";
  return `List every PURCHASED item with a category. Return JSON {lineItems: [{description, categoryId}]}.

Rules:
  - Skip promos, coupons, discounts, tenders, subtotals.
  - Include "Sales Tax" as a line item.

How to pick each item's category:
  1. Read each category's NAME as a description of what belongs there. The name is your primary signal.
  2. Match by what the item IS (its function or product type), not by where it was purchased. The same store can sell items that belong in many different categories.
  3. When a category name lists multiple things (e.g. "X/Y/Z"), treat ALL of them as in scope — the name is telling you that category covers X, Y, AND Z.
  4. Prefer a narrow/specialty category over a broad catch-all when an item plausibly fits both.
  5. "Other" is reserved for items that no other category name plausibly describes.
  6. Do NOT invent categoryIds not in the list.${preselectNudge}

Categories:
${categoryList(cats)}`;
}

function call3Prompt(items) {
  const listed = items.map((it, i) => `  ${i + 1}. ${it.description}`).join("\n");
  return `You have a receipt image and a list of items the shopper purchased. For each item, find it on the receipt and determine the ACTUAL PAID PRICE — what contributed to the subtotal — in integer cents.

Apply these clues when reading each line:
  1. Base printed price for the item's line.
  2. Quantity multiplier: lines like "2 AT 1 FOR \$X.XX", "3 @ \$X.XX ea", or "4 FOR \$X.XX" mean the actual charge is the multiplier × unit price. Use the line's total, not the unit price.
  3. Line-level coupons or manufacturer rebates (a subsequent line with a negative amount, e.g. "COUPON -\$3.00") reduce that item's price.
  4. Weight-priced items: use the computed total already printed.
  5. For "Sales Tax", return the printed tax amount in cents.

Return JSON {prices: [{description, priceCents}]}, one entry per input item, preserving the order given. priceCents is a non-negative integer.

Items:
${listed}`;
}

// ─── Schemas ─────────────────────────────────────────────────────────

const CALL1_SCHEMA = {
  type: "object",
  properties: {
    merchant: { type: "string" },
    merchantLegalName: { type: "string" },
    date: { type: "string" },
    amountCents: { type: "integer" },
    multiCategoryLikely: { type: "boolean" },
    candidates: { type: "array", items: { type: "object", properties: { categoryId: { type: "integer" }, reason: { type: "string" } }, required: ["categoryId", "reason"] } },
    topChoice: { type: "integer" },
    notes: { type: "string" },
  },
  required: ["merchant", "date", "amountCents"],
};

const CALL2_SCHEMA = {
  type: "object",
  properties: {
    lineItems: { type: "array", items: { type: "object", properties: { description: { type: "string" }, categoryId: { type: "integer" } }, required: ["description", "categoryId"] } },
  },
  required: ["lineItems"],
};

const CALL3_SCHEMA = {
  type: "object",
  properties: {
    prices: { type: "array", items: { type: "object", properties: { description: { type: "string" }, priceCents: { type: "integer" } }, required: ["description", "priceCents"] } },
  },
  required: ["prices"],
};

// ─── API helper ──────────────────────────────────────────────────────

function mimeFor(f) {
  const e = path.extname(f).toLowerCase();
  if (e === ".png") return "image/png";
  if (e === ".webp") return "image/webp";
  return "image/jpeg";
}

async function apiCall(parts, schema) {
  let lastErr;
  for (let attempt = 1; attempt <= 4; attempt++) {
    try {
      const res = await client.models.generateContent({
        model: LITE,
        contents: [{ role: "user", parts }],
        config: { responseMimeType: "application/json", responseSchema: schema, temperature: 0 },
      });
      return { parsed: JSON.parse(res.text), tokens: res.usageMetadata };
    } catch (e) {
      lastErr = e;
      const msg = String(e.message || e);
      const transient = /503|UNAVAILABLE|overloaded|429|RESOURCE_EXHAUSTED|deadline|fetch failed|network|ECONNRESET|ETIMEDOUT|socket/i.test(msg);
      if (!transient || attempt === 4) throw e;
      await new Promise(r => setTimeout(r, 500 * Math.pow(2, attempt - 1)));
    }
  }
  throw lastErr;
}

function remapInvalidCategoryIds(items, cats) {
  const validSet = new Set(cats.map(c => c.id));
  const invalidIdx = [];
  for (let i = 0; i < items.length; i++) if (!validSet.has(items[i].categoryId)) invalidIdx.push(i);
  if (invalidIdx.length === 0) return items;
  let fallback = null;
  if (validSet.has(30426)) fallback = 30426;
  else {
    const counts = new Map();
    for (const it of items) if (validSet.has(it.categoryId)) counts.set(it.categoryId, (counts.get(it.categoryId) || 0) + 1);
    let best = -1;
    for (const [cid, n] of counts) if (n > best) { fallback = cid; best = n; }
    if (fallback == null) fallback = cats[0]?.id;
  }
  return items.map((it, i) => invalidIdx.includes(i) ? { ...it, categoryId: fallback } : it);
}

function reconcilePrices(prices, totalCents) {
  const rows = prices.map(p => ({ ...p, priceCents: p.priceCents || 0 }));
  const isTax = d => typeof d === "string" && /sales\s*tax/i.test(d);
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
    for (let i = 0; i < reconciled.length; i++) {
      if (i === taxIdx) continue;
      if (reconciled[i].priceCents > largestVal) { largestVal = reconciled[i].priceCents; largestIdx = i; }
    }
    if (largestIdx >= 0) reconciled[largestIdx].priceCents += residual;
  }
  return reconciled;
}

// ─── Pipeline ────────────────────────────────────────────────────────

async function runPipeline(imageBytes, mimeType, cats, variant) {
  const c1 = await apiCall([
    { text: variant.call1(cats, new Set()) },
    { inlineData: { mimeType, data: imageBytes.toString("base64") } },
  ], CALL1_SCHEMA);

  const multi = c1.parsed.multiCategoryLikely === true;
  const singleCid = c1.parsed.topChoice ?? c1.parsed.singleCategoryId;
  const validSet = new Set(cats.map(c => c.id));

  let categoryAmounts, route;
  let c2 = null, c3 = null, items = null, reconciled = null;

  if (multi) {
    route = "multi-full";
    c2 = await apiCall([
      { text: call2Prompt(cats, false) },
      { inlineData: { mimeType, data: imageBytes.toString("base64") } },
    ], CALL2_SCHEMA);
    items = remapInvalidCategoryIds(c2.parsed.lineItems || [], cats);

    c3 = await apiCall([
      { text: call3Prompt(items) },
      { inlineData: { mimeType, data: imageBytes.toString("base64") } },
    ], CALL3_SCHEMA);
    const prices = c3.parsed.prices || [];
    reconciled = reconcilePrices(prices, c1.parsed.amountCents || 0);

    const byCat = new Map();
    const isTax = d => typeof d === "string" && /sales\s*tax/i.test(d);
    for (let i = 0; i < items.length; i++) {
      if (isTax(items[i].description)) continue;
      const cid = items[i].categoryId;
      const cents = reconciled[i]?.priceCents || 0;
      byCat.set(cid, (byCat.get(cid) || 0) + cents);
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
    let cid = singleCid;
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
    items,
  };
}

// ─── Main ────────────────────────────────────────────────────────────

(async () => {
  const labels = JSON.parse(fs.readFileSync(path.join(ROOT, "test-data", "labels.json"), "utf8"));
  const subset = pickSubset(labels);
  const cats = TEST_CATEGORIES;
  console.log(`Round 1: iterating ${VARIANTS.length} variants on ${subset.length} receipts (full ${cats.length}-cat list, no preselect)\n`);

  const results = {}; // { variantId: [rows] }
  for (const v of VARIANTS) {
    console.log(`─── ${v.id} ── ${v.note}`);
    const rows = [];
    for (const label of subset) {
      const imgPath = path.join(ROOT, "test-data", "images", label.file);
      if (!fs.existsSync(imgPath)) continue;
      const img = fs.readFileSync(imgPath);
      const mime = mimeFor(label.file);
      try {
        const res = await runPipeline(img, mime, cats, v);
        const grade = gradeResult(label, res.parsed);
        rows.push({ file: label.file, label, result: res, grade });
      } catch (e) {
        rows.push({ file: label.file, label, err: e.message || String(e) });
      }
    }
    results[v.id] = rows;

    // Quick per-variant summary
    const ok = rows.filter(r => r.result);
    const singlesOk = ok.filter(r => r.label.categoryAmounts?.length === 1 || (r.label.categoryAmounts == null && r.label.categoryId));
    const multiOk = ok.filter(r => r.label.categoryAmounts?.length > 1);
    const singlesCorrect = singlesOk.filter(r => {
      const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId;
      return r.result.parsed.categoryAmounts?.[0]?.categoryId === exp;
    }).length;
    const multiRouted = multiOk.filter(r => r.result.route === "multi-full").length;
    const multiCset = multiOk.filter(r => r.grade.categoryAmounts.setMatch).length;
    console.log(`   singles: ${singlesCorrect}/${singlesOk.length}  multi-routed: ${multiRouted}/${multiOk.length}  multi-cset: ${multiCset}/${multiOk.length}`);
  }

  // ─── Ranking table ──────────────────────────────────────────
  console.log("\n" + "═".repeat(108));
  console.log("ROUND 1 RANKING");
  console.log("═".repeat(108));
  const ranked = VARIANTS.map(v => {
    const rows = results[v.id].filter(r => r.result);
    const singlesOk = rows.filter(r => r.label.categoryAmounts?.length === 1 || (r.label.categoryAmounts == null && r.label.categoryId));
    const multiOk = rows.filter(r => r.label.categoryAmounts?.length > 1);
    const singlesCorrect = singlesOk.filter(r => {
      const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId;
      return r.result.parsed.categoryAmounts?.[0]?.categoryId === exp;
    }).length;
    const multiRouted = multiOk.filter(r => r.result.route === "multi-full").length;
    const multiCset = multiOk.filter(r => r.grade.categoryAmounts.setMatch).length;

    // Amazon sub-score (brakepads, tierodboots, charger)
    const amazon = rows.filter(r => r.file.startsWith("amazon_"));
    const amazonCorrect = amazon.filter(r => {
      const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId;
      return r.result.parsed.categoryAmounts?.[0]?.categoryId === exp;
    }).length;

    return { id: v.id, note: v.note, singlesCorrect, singlesTot: singlesOk.length, multiRouted, multiTot: multiOk.length, multiCset, amazonCorrect, combined: singlesCorrect + multiRouted };
  }).sort((a, b) => (b.combined - a.combined) || (b.amazonCorrect - a.amazonCorrect));

  console.log(`${"id".padEnd(24)} ${"singles".padEnd(8)} ${"multi-rt".padEnd(9)} ${"multi-c".padEnd(8)} ${"amzn".padEnd(5)} ${"combined"} ${"note"}`);
  for (const r of ranked) {
    console.log(`${r.id.padEnd(24)} ${(r.singlesCorrect + "/" + r.singlesTot).padEnd(8)} ${(r.multiRouted + "/" + r.multiTot).padEnd(9)} ${(r.multiCset + "/" + r.multiTot).padEnd(8)} ${(r.amazonCorrect + "/3").padEnd(5)} ${String(r.combined).padEnd(8)} ${r.note}`);
  }
  console.log(`\nWinner: ${ranked[0].id}`);

  const outPath = path.join(ROOT, "results", `iterate-nopresel-r1-${new Date().toISOString().replace(/[:.]/g, "-")}.json`);
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, JSON.stringify({ variants: VARIANTS.map(v => ({ id: v.id, note: v.note })), results, ranked }, null, 2));
  console.log(`Saved → ${path.relative(ROOT, outPath)}`);
})();
