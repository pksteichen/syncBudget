#!/usr/bin/env node
// No-preselect Lite pipeline harness. Mirrors ReceiptOcrService.kt verbatim:
//
//   Call 1 always runs. When no cats are pre-selected, it also returns
//     multiCategoryLikely (bool) + singleCategoryId (int?).
//   If multiCategoryLikely === false, short-circuit to single-cat result
//     (1 API call total — the common case).
//   If multiCategoryLikely === true, continue to Call 2 (items+cats) and
//     Call 3 (prices), then reconcile to Call 1's total (3 API calls).
//
// Usage:
//   PROMPT_VERSION=baseline node scripts/test-lite-nopresel.js
//   PROMPT_VERSION=v2       node scripts/test-lite-nopresel.js
//
// Subset (33 receipts):
//   - 25 single-cat (≤5 per category, alphabetical within cat)
//   - 3 new Amazon receipts (charger, brake pads, tie rod boots)
//   - 5 multi-cat with the most categories (top 5 by .categoryAmounts.length)

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
const PROMPT_VERSION = process.env.PROMPT_VERSION || "baseline";

const client = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

// ─── Subset selection ────────────────────────────────────────────────

function pickSubset(labels) {
  const nonVN = labels.filter(l => !l.file.startsWith("mcocr_"));
  const subset = [];

  // Singles — up to 5 per category, alphabetical within cat
  const singles = nonVN.filter(l => l.categoryAmounts?.length === 1 || (l.categoryAmounts == null && l.categoryId));
  const byCat = new Map();
  for (const s of singles) {
    const cid = s.categoryAmounts?.[0]?.categoryId ?? s.categoryId;
    if (!byCat.has(cid)) byCat.set(cid, []);
    byCat.get(cid).push(s);
  }
  for (const [, files] of byCat) {
    files.sort((a, b) => a.file.localeCompare(b.file));
    const picks = files.slice(0, 5);
    subset.push(...picks);
  }

  // Top 5 multi-cat by cats count
  const multi = nonVN.filter(l => l.categoryAmounts?.length > 1)
    .sort((a, b) => (b.categoryAmounts.length - a.categoryAmounts.length) || a.file.localeCompare(b.file))
    .slice(0, 5);
  subset.push(...multi);

  return subset;
}

// ─── Prompts ─────────────────────────────────────────────────────────

function categoryList(cats) {
  return cats.map(c => c.tag ? `  - id=${c.id} name="${c.name}" tag="${c.tag}"` : `  - id=${c.id} name="${c.name}"`).join("\n");
}

// Baseline: verbatim port of ReceiptOcrService.kt buildCall1Prompt for the
// no-preselect path. Also the routing-probe block.
const PROMPTS = {
  baseline: {
    call1(cats, preSelected) {
      const base = `Extract receipt header data as JSON.

- merchant: the consumer brand (e.g. "McDonald's", "Target", "Costco"). Prefer the consumer brand over the legal operator entity. Preserve original language; don't translate.
- merchantLegalName: optional, only when the legal entity is clearly distinct from the consumer brand.
- date: YYYY-MM-DD ISO. Receipts often have multiple dates; prefer the transaction date over print/due dates. DD/MM locales: Malaysia, Singapore, Vietnam, most of Europe. MM/DD: US.
- amountCents: INTEGER number of cents for the final paid total (tax and tip included). Ignore subtotal, pre-tax lines, and separate GST/VAT summary tables. Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are thousand separators; return the integer đồng value.`;

      if (preSelected.size > 0) return base;

      return base + `

Also return two routing hints:
- multiCategoryLikely: true if the items on this receipt clearly span 2+ BudgeTrak consumer categories (e.g. a Target mixed trip with apparel + groceries + home). Set to false if nearly all items fit one category (gas fill-up, fast-food order, grocery-only trip, drugstore visit, single-item online order).
- singleCategoryId: only when multiCategoryLikely is false, the best-fit category id for the whole receipt. Pick from the list below. Stationery / office / pens / paper / bookstores → Other unless clearly kids' school supplies (→ Kid's Stuff). Hardware / electrical / plumbing / paint → Home Supplies.

Categories:
${categoryList(cats)}

Do not invent categoryIds not in the list.`;
    },
    call2(cats, preselected) {
      const preselectNudge = preselected
        ? `\n\n  - The categories below are pre-selected by the shopper for this receipt. Try to cover as many of them as reasonably fit — the shopper expects to see items in these specific buckets. But skip a category if no item on the receipt plausibly fits it; never force-fit an item into a bucket that clearly doesn't match (the shopper may have pre-selected a category by accident).\n  - When an item could plausibly fit either a niche/specialty category (e.g. Holidays/Birthdays, Kid's Stuff, Entertainment, Clothes, Health/Pharmacy) or a general catch-all (e.g. Groceries, Home Supplies, Other), prefer the niche category. Niche categories are under-filled by default; err toward the specific bucket when the item has a clear specialty signal.`
        : "";
      return `List every PURCHASED item with a category. Return JSON {lineItems: [{description, categoryId}]}.

Rules:
  - Skip promos, coupons, discounts, tenders, subtotals.
  - Prefer a concrete consumer category (Groceries, Home Supplies, Health/Pharmacy, Clothes, Entertainment, Holidays, Kid's Stuff) over "Other".
  - Avoid these unless the item is unambiguously that type: Mortgage/Insurance/PropTax (42007), Insurance (36973), Transportation/Gas (48281 — only fuel/parking/transit), Electric/Gas (17132 — only utility bills), Phone/Internet/Computer (62776 — only service bills), Business, Employment, Farm, Charity.${preselectNudge}

Include "Sales Tax" as a line item. Categories:
${categoryList(cats)}`;
    },
  },

  // v2: category-agnostic reasoning. The same 6 rules appear in Call 1's
  // singleCategoryId block and Call 2's per-line block. No category names
  // are hard-coded except "Other" (the only guaranteed-present category).
  v2: {
    call1(cats, preSelected) {
      const base = `Extract receipt header data as JSON.

- merchant: the consumer brand (e.g. "McDonald's", "Target", "Costco"). Prefer the consumer brand over the legal operator entity. Preserve original language; don't translate.
- merchantLegalName: optional, only when the legal entity is clearly distinct from the consumer brand.
- date: YYYY-MM-DD ISO. Receipts often have multiple dates; prefer the transaction date over print/due dates. DD/MM locales: Malaysia, Singapore, Vietnam, most of Europe. MM/DD: US.
- amountCents: INTEGER number of cents for the final paid total (tax and tip included). Ignore subtotal, pre-tax lines, and separate GST/VAT summary tables. Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are thousand separators; return the integer đồng value.`;

      if (preSelected.size > 0) return base;

      return base + `

Also return two routing hints:
- multiCategoryLikely: true if the items clearly span 2+ of the user's categories (e.g. a mixed retail trip with clothes + groceries + home). Set false when nearly all items fit one category (gas fill-up, fast-food order, grocery-only trip, drugstore visit, single-item online order).
- singleCategoryId: when multiCategoryLikely is false, the best-fit category id for the whole receipt.

How to pick a category:
  1. Read each category's NAME as a description of what belongs there. The name is your primary signal.
  2. Match by what the item IS (its function or product type), not by where it was purchased. The same store can sell items that belong in many different categories.
  3. When a category name lists multiple things (e.g. "X/Y/Z"), treat ALL of them as in scope — the name is telling you that category covers X, Y, AND Z.
  4. Prefer a narrow/specialty category over a broad catch-all when an item plausibly fits both.
  5. "Other" is reserved for items that no other category name plausibly describes.
  6. Do NOT invent categoryIds not in the list.

Categories:
${categoryList(cats)}`;
    },
    call2(cats, preselected) {
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
    },
  },

  // ─── v3a: v2 + compound-name example in rule 3 ────────────────────
  // Goal: force "Phone/Internet/Computer" and "Transportation/Gas" to be
  // read as unions of product words, not as service-only compounds.
  v3a: {
    call1(cats, preSelected) {
      const base = `Extract receipt header data as JSON.

- merchant: the consumer brand (e.g. "McDonald's", "Target", "Costco"). Prefer the consumer brand over the legal operator entity. Preserve original language; don't translate.
- merchantLegalName: optional, only when the legal entity is clearly distinct from the consumer brand.
- date: YYYY-MM-DD ISO. Receipts often have multiple dates; prefer the transaction date over print/due dates. DD/MM locales: Malaysia, Singapore, Vietnam, most of Europe. MM/DD: US.
- amountCents: INTEGER number of cents for the final paid total (tax and tip included). Ignore subtotal, pre-tax lines, and separate GST/VAT summary tables. Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are thousand separators; return the integer đồng value.`;

      if (preSelected.size > 0) return base;

      return base + `

Also return two routing hints:
- multiCategoryLikely: true if the items clearly span 2+ of the user's categories (e.g. a mixed retail trip with clothes + groceries + home). Set false when nearly all items fit one category (gas fill-up, fast-food order, grocery-only trip, drugstore visit, single-item online order).
- singleCategoryId: when multiCategoryLikely is false, the best-fit category id for the whole receipt.

How to pick a category:
  1. Read each category's NAME as a description of what belongs there. The name is your primary signal.
  2. Match by what the item IS (its function or product type), not by where it was purchased. The same store can sell items that belong in many different categories.
  3. A category name like "X/Y/Z" covers X items, Y items, AND Z items — including the PHYSICAL GOODS version, not only the service/bill version. If any word in the name is a product type (phone, computer, gas, food, clothes, tools, etc.), that product type fits that category.
  4. Prefer a narrow/specialty category over a broad catch-all when an item plausibly fits both.
  5. "Other" is reserved for items that no other category name plausibly describes.
  6. Do NOT invent categoryIds not in the list.

Categories:
${categoryList(cats)}`;
    },
    call2(cats, preselected) {
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
  3. A category name like "X/Y/Z" covers X items, Y items, AND Z items — including the PHYSICAL GOODS version, not only the service/bill version. If any word in the name is a product type (phone, computer, gas, food, clothes, tools, etc.), that product type fits that category.
  4. Prefer a narrow/specialty category over a broad catch-all when an item plausibly fits both.
  5. "Other" is reserved for items that no other category name plausibly describes.
  6. Do NOT invent categoryIds not in the list.${preselectNudge}

Categories:
${categoryList(cats)}`;
    },
  },

  // ─── v3d: v2 + top-3 candidates with reasoning, pick topChoice ────
  // Schema change: singleCategoryId replaced by candidates[{categoryId,
  // reason}] (1-3 entries) + topChoice. Forces visible enumeration &
  // justification. Same rules as v2 (no compound-name example).
  v3d: {
    call1(cats, preSelected) {
      const base = `Extract receipt header data as JSON.

- merchant: the consumer brand (e.g. "McDonald's", "Target", "Costco"). Prefer the consumer brand over the legal operator entity. Preserve original language; don't translate.
- merchantLegalName: optional, only when the legal entity is clearly distinct from the consumer brand.
- date: YYYY-MM-DD ISO. Receipts often have multiple dates; prefer the transaction date over print/due dates. DD/MM locales: Malaysia, Singapore, Vietnam, most of Europe. MM/DD: US.
- amountCents: INTEGER number of cents for the final paid total (tax and tip included). Ignore subtotal, pre-tax lines, and separate GST/VAT summary tables. Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are thousand separators; return the integer đồng value.`;

      if (preSelected.size > 0) return base;

      return base + `

Also return two routing hints:
- multiCategoryLikely: true if the items clearly span 2+ of the user's categories (e.g. a mixed retail trip with clothes + groceries + home). Set false when nearly all items fit one category (gas fill-up, fast-food order, grocery-only trip, drugstore visit, single-item online order).
- When multiCategoryLikely is false, return candidates: an array of 1-3 best-fit category ids for the whole receipt, each with a brief reason (≤15 words) citing which word(s) in the category name match. List in descending order of fit. Also return topChoice: the single best-fit category id (must match candidates[0].categoryId).

How to pick a category:
  1. Read each category's NAME as a description of what belongs there. The name is your primary signal.
  2. Match by what the item IS (its function or product type), not by where it was purchased. The same store can sell items that belong in many different categories.
  3. When a category name lists multiple things (e.g. "X/Y/Z"), treat ALL of them as in scope — the name is telling you that category covers X, Y, AND Z.
  4. Prefer a narrow/specialty category over a broad catch-all when an item plausibly fits both.
  5. "Other" is reserved for items that no other category name plausibly describes.
  6. Do NOT invent categoryIds not in the list.

Categories:
${categoryList(cats)}`;
    },
    call2(cats, preselected) {
      // Call 2 unchanged from v2 for this variant.
      return PROMPTS.v2.call2(cats, preselected);
    },
  },

  // ─── v3ad: v3a's rules + v3d's top-3 schema ───────────────────────
  v3ad: {
    call1(cats, preSelected) {
      const base = `Extract receipt header data as JSON.

- merchant: the consumer brand (e.g. "McDonald's", "Target", "Costco"). Prefer the consumer brand over the legal operator entity. Preserve original language; don't translate.
- merchantLegalName: optional, only when the legal entity is clearly distinct from the consumer brand.
- date: YYYY-MM-DD ISO. Receipts often have multiple dates; prefer the transaction date over print/due dates. DD/MM locales: Malaysia, Singapore, Vietnam, most of Europe. MM/DD: US.
- amountCents: INTEGER number of cents for the final paid total (tax and tip included). Ignore subtotal, pre-tax lines, and separate GST/VAT summary tables. Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are thousand separators; return the integer đồng value.`;

      if (preSelected.size > 0) return base;

      return base + `

Also return two routing hints:
- multiCategoryLikely: true if the items clearly span 2+ of the user's categories (e.g. a mixed retail trip with clothes + groceries + home). Set false when nearly all items fit one category (gas fill-up, fast-food order, grocery-only trip, drugstore visit, single-item online order).
- When multiCategoryLikely is false, return candidates: an array of 1-3 best-fit category ids for the whole receipt, each with a brief reason (≤15 words) citing which word(s) in the category name match. List in descending order of fit. Also return topChoice: the single best-fit category id (must match candidates[0].categoryId).

How to pick a category:
  1. Read each category's NAME as a description of what belongs there. The name is your primary signal.
  2. Match by what the item IS (its function or product type), not by where it was purchased. The same store can sell items that belong in many different categories.
  3. A category name like "X/Y/Z" covers X items, Y items, AND Z items — including the PHYSICAL GOODS version, not only the service/bill version. If any word in the name is a product type (phone, computer, gas, food, clothes, tools, etc.), that product type fits that category.
  4. Prefer a narrow/specialty category over a broad catch-all when an item plausibly fits both.
  5. "Other" is reserved for items that no other category name plausibly describes.
  6. Do NOT invent categoryIds not in the list.

Categories:
${categoryList(cats)}`;
    },
    call2(cats, preselected) {
      return PROMPTS.v3a.call2(cats, preselected);
    },
  },
};

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

// Baseline/v2/v3a schema: singleCategoryId only.
const CALL1_SCHEMA = {
  type: "object",
  properties: {
    merchant: { type: "string" },
    merchantLegalName: { type: "string" },
    date: { type: "string" },
    amountCents: { type: "integer" },
    multiCategoryLikely: { type: "boolean" },
    singleCategoryId: { type: "integer" },
    notes: { type: "string" },
  },
  required: ["merchant", "date", "amountCents"],
};

// v3d/v3ad schema: candidates[{categoryId, reason}] + topChoice.
const CALL1_SCHEMA_TOP3 = {
  type: "object",
  properties: {
    merchant: { type: "string" },
    merchantLegalName: { type: "string" },
    date: { type: "string" },
    amountCents: { type: "integer" },
    multiCategoryLikely: { type: "boolean" },
    candidates: {
      type: "array",
      items: {
        type: "object",
        properties: {
          categoryId: { type: "integer" },
          reason: { type: "string" },
        },
        required: ["categoryId", "reason"],
      },
    },
    topChoice: { type: "integer" },
    notes: { type: "string" },
  },
  required: ["merchant", "date", "amountCents"],
};

function schemaFor(version) {
  return (version === "v3d" || version === "v3ad") ? CALL1_SCHEMA_TOP3 : CALL1_SCHEMA;
}

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

// ─── Post-processing (mirrors ReceiptOcrService.kt) ──────────────────

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

async function runPipeline(imageBytes, mimeType, cats, promptSet, version) {
  const t0 = Date.now();
  const c1 = await apiCall([
    { text: promptSet.call1(cats, new Set()) },
    { inlineData: { mimeType, data: imageBytes.toString("base64") } },
  ], schemaFor(version));

  const multi = c1.parsed.multiCategoryLikely === true;
  // topChoice (v3d/v3ad) OR singleCategoryId (others).
  const singleCid = c1.parsed.topChoice ?? c1.parsed.singleCategoryId;
  const validSet = new Set(cats.map(c => c.id));

  let c2 = null, c3 = null, items = null, reconciled = null, categoryAmounts = null, route = "single-shortcircuit";

  if (multi) {
    route = "multi-full";
    c2 = await apiCall([
      { text: promptSet.call2(cats, false) },
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
    // Short-circuit: use singleCategoryId (fall back to Other or first cat if invalid)
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
    route,
    c1: c1.parsed,
    items,
    reconciled,
    elapsedMs: Date.now() - t0,
    tokens: {
      c1: c1.tokens, c2: c2?.tokens, c3: c3?.tokens,
      totalIn: (c1.tokens?.promptTokenCount || 0) + (c2?.tokens?.promptTokenCount || 0) + (c3?.tokens?.promptTokenCount || 0),
      totalOut: (c1.tokens?.candidatesTokenCount || 0) + (c2?.tokens?.candidatesTokenCount || 0) + (c3?.tokens?.candidatesTokenCount || 0),
    },
  };
}

function costOf(tokens) {
  const p = PRICING[LITE];
  return (tokens.totalIn * p.in + tokens.totalOut * p.out) / 1_000_000;
}

// ─── Main ────────────────────────────────────────────────────────────

(async () => {
  const labels = JSON.parse(fs.readFileSync(path.join(ROOT, "test-data", "labels.json"), "utf8"));
  const subset = pickSubset(labels);
  const singles = subset.filter(l => l.categoryAmounts?.length === 1 || (l.categoryAmounts == null && l.categoryId));
  const multi = subset.filter(l => l.categoryAmounts?.length > 1);

  console.log(`No-preselect harness (PROMPT_VERSION=${PROMPT_VERSION}): ${subset.length} receipts = ${singles.length} singles + ${multi.length} multi`);
  console.log(`Using full TEST_CATEGORIES list (${TEST_CATEGORIES.length} cats)\n`);

  const cats = TEST_CATEGORIES;
  const promptSet = PROMPTS[PROMPT_VERSION];
  if (!promptSet) throw new Error(`Unknown PROMPT_VERSION=${PROMPT_VERSION}`);

  const rows = [];
  for (const label of subset) {
    const imgPath = path.join(ROOT, "test-data", "images", label.file);
    if (!fs.existsSync(imgPath)) { console.log(`  [skip] ${label.file} — image missing`); continue; }
    const img = fs.readFileSync(imgPath);
    const mime = mimeFor(label.file);

    process.stdout.write(`▶ ${label.file.slice(0, 46).padEnd(46)}  `);
    let res, err = null;
    try { res = await runPipeline(img, mime, cats, promptSet, PROMPT_VERSION); }
    catch (e) { err = e.message || String(e); }

    if (err) { console.log(`FAIL: ${err.slice(0, 80)}`); rows.push({ file: label.file, label, err }); continue; }

    const grade = gradeResult(label, res.parsed);
    const cost = costOf(res.tokens);
    rows.push({ file: label.file, label, result: res, grade, cost });

    const m = grade.merchant.pass ? "✓" : "✗";
    const d = grade.date.pass ? "✓" : "✗";
    const a = grade.amount.pass ? "✓" : "✗";
    const cset = grade.categoryAmounts.setMatch ? "✓" : "✗";
    const cshr = grade.categoryAmounts.shareMatch ? "✓" : "✗";
    const expected = label.categoryAmounts?.length === 1 ? label.categoryAmounts[0].categoryId : (label.categoryId ?? "multi");
    const got = res.parsed.categoryAmounts?.map(c => c.categoryId).join(",") || "-";
    console.log(`${res.route.padEnd(20)} m${m} d${d} a${a} cset${cset} cshr${cshr}  exp=${expected} got=${got}  $${cost.toFixed(5)} ${res.elapsedMs}ms`);
  }

  // ─── Aggregate ───────────────────────────────────────────────
  console.log("\n" + "═".repeat(96));
  console.log(`AGGREGATE — PROMPT_VERSION=${PROMPT_VERSION}`);
  console.log("═".repeat(96));

  const ok = rows.filter(r => r.result);
  const pass = k => ok.filter(r => r.grade[k].pass).length;
  const passCA = k => ok.filter(r => r.grade.categoryAmounts[k]).length;

  const singlesOk = ok.filter(r => r.label.categoryAmounts?.length === 1 || (r.label.categoryAmounts == null && r.label.categoryId));
  const multiOk = ok.filter(r => r.label.categoryAmounts?.length > 1);

  // Single-cat routing accuracy: did short-circuit pick the right cat?
  const singlesShort = singlesOk.filter(r => r.result.route === "single-shortcircuit");
  const singlesShortCorrect = singlesShort.filter(r => {
    const expected = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId;
    const got = r.result.parsed.categoryAmounts?.[0]?.categoryId;
    return got === expected;
  });
  const singlesFull = singlesOk.filter(r => r.result.route === "multi-full");

  // Per-cat single short-circuit breakdown
  const catName = Object.fromEntries(TEST_CATEGORIES.map(c => [c.id, c.name]));
  const perCat = new Map();
  for (const r of singlesOk) {
    const expected = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId;
    const got = r.result.parsed.categoryAmounts?.[0]?.categoryId;
    const correct = got === expected;
    if (!perCat.has(expected)) perCat.set(expected, { n: 0, correct: 0, route: [] });
    const e = perCat.get(expected);
    e.n++; if (correct) e.correct++;
    e.route.push(r.result.route);
  }

  console.log(`Total receipts:        n=${ok.length}`);
  console.log(`Merchant pass:         ${pass("merchant")}/${ok.length}`);
  console.log(`Date pass:             ${pass("date")}/${ok.length}`);
  console.log(`Amount pass:           ${pass("amount")}/${ok.length}`);
  console.log(`cset pass:             ${passCA("setMatch")}/${ok.length}`);
  console.log(`cshr pass:             ${passCA("shareMatch")}/${ok.length}`);
  console.log();
  console.log(`Single-cat receipts:   n=${singlesOk.length} (${singlesShort.length} short-circuit, ${singlesFull.length} promoted to full pipeline)`);
  console.log(`  Short-circuit correct-cat:  ${singlesShortCorrect.length}/${singlesShort.length}`);
  console.log();
  console.log(`Multi-cat receipts:    n=${multiOk.length}`);
  const multiShort = multiOk.filter(r => r.result.route === "single-shortcircuit");
  console.log(`  Incorrectly short-circuited: ${multiShort.length}/${multiOk.length}`);

  console.log(`\nPer-category (single-cat ground truth):`);
  for (const [cid, stats] of [...perCat.entries()].sort()) {
    const shortCt = stats.route.filter(r => r === "single-shortcircuit").length;
    console.log(`  ${(catName[cid] || cid).padEnd(32)}  ${stats.correct}/${stats.n}  (${shortCt} short-circuit)`);
  }

  const totalCost = ok.reduce((s, r) => s + r.cost, 0);
  console.log(`\nTotal cost: $${totalCost.toFixed(4)}  avg $${(totalCost / ok.length).toFixed(5)}`);
  const totalMs = ok.reduce((s, r) => s + r.result.elapsedMs, 0);
  console.log(`Total time: ${(totalMs / 1000).toFixed(1)}s  avg ${Math.round(totalMs / ok.length)}ms`);

  const outPath = path.join(ROOT, "results", `nopresel-${PROMPT_VERSION}-${new Date().toISOString().replace(/[:.]/g, "-")}.json`);
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, JSON.stringify(rows, null, 2));
  console.log(`\nSaved → ${path.relative(ROOT, outPath)}`);
})();
