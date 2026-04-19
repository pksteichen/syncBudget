#!/usr/bin/env node
// Round 4: per-item category SCORING.
//
// User hypothesis: having the model score each candidate category per line
// item, then collapsing to single/multi based on whether items' top picks
// agree, avoids the "snap to broadest bucket" failure mode AND makes
// multi-vs-single a consequence of the per-item scores instead of a
// separate judgment the model botches.
//
// Call 1 schema returns items[].scores[]. Code derives multiCategoryLikely
// and topChoice from those scores; the model's own flags are used only
// when code derivation is ambiguous.
//
// Pipeline kept 3-call for apples-to-apples with rounds 1-3: single-cat
// short-circuits to Call 1 alone; multi routes to Call 2 + Call 3.
//
// Constraint: no real categories/products in prompt examples.

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

const BASIC_SCORING_INTRO = `
Also, categorise each PURCHASED item on the receipt. For each line item, score up to 3 best-fit categories from the list. Return items[] where each entry has:
  - description: the item text as printed on the receipt (skip promos/coupons/discounts/tenders/subtotals; include "Sales Tax" as one item).
  - scores: an array of up to 3 entries, each { categoryId, score (0-100), reason (≤15 words) }. Score = how strongly the category's NAME describes what that item IS. Listed in descending score. Omit categories you consider unlikely.

Also return:
  - multiCategoryLikely: true if different items have different top-1 categories that belong to DIFFERENT real-world domains.
  - topChoice: when multiCategoryLikely is false, the single best-fit categoryId for the whole receipt (usually the top-1 shared by all items).`;

const SCORING_RULES = `
How to score:
  - Read each category's NAME as a description of what belongs there.
  - Match by what the item IS (its function or product type), not by where it was sold.
  - A name like "X/Y/Z" covers X items, Y items, AND Z items.
  - Specific-named categories beat generic/catch-all names. Prefer the name that most directly names the item's type.`;

const VARIANTS = [
  {
    id: "V1_control_T7",
    note: "T7 (round 2 winner, non-scoring) as reference control",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const r = `
Also return routing hints. Before picking a category, follow this procedure:
  Step A: identify the PRIMARY PRODUCT TYPE on the receipt in 1-3 generic nouns (e.g. "footwear", "pet food", "electronic device"). If items clearly span multiple product types, set multiCategoryLikely=true and stop.
  Step B: for each category in the list, check whether its NAME contains any noun matching or closely synonymous to the product type from Step A.
  Step C: the winning category is the one whose name most directly names the product type. Tie-break toward more specific names.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice.`;
      return HEADER_BASE + r + COMMON + `\n\nCategories:\n${categoryList(cats)}`;
    },
    parseResult(c1, cats) { return parseLegacyControl(c1); },
  },

  {
    id: "V2_scoring_basic",
    note: "per-item scoring 0-100, top-3; derive multi/top from items[]",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      return HEADER_BASE + BASIC_SCORING_INTRO + SCORING_RULES + COMMON + `\n\nCategories:\n${categoryList(cats)}`;
    },
    parseResult(c1, cats) { return parseScoringByItems(c1, cats, { threshold: 0, gap: 0 }); },
  },

  {
    id: "V3_scoring_confidence_threshold",
    note: "V2 + items with top score <50 contribute no vote",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const note = `\n\nAn item whose top score is below 50 is LOW-CONFIDENCE. Include it in items[] but its score won't strongly anchor the topChoice.`;
      return HEADER_BASE + BASIC_SCORING_INTRO + SCORING_RULES + note + COMMON + `\n\nCategories:\n${categoryList(cats)}`;
    },
    parseResult(c1, cats) { return parseScoringByItems(c1, cats, { threshold: 50, gap: 0 }); },
  },

  {
    id: "V4_scoring_min_gap",
    note: "V2 + require ≥15-point gap between top-1 and top-2 for clear winner",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const note = `\n\nAn item's top-1 is "clear" only if its score exceeds top-2 by at least 15 points. Items without a clear winner count as ambiguous when deciding multiCategoryLikely.`;
      return HEADER_BASE + BASIC_SCORING_INTRO + SCORING_RULES + note + COMMON + `\n\nCategories:\n${categoryList(cats)}`;
    },
    parseResult(c1, cats) { return parseScoringByItems(c1, cats, { threshold: 0, gap: 15 }); },
  },

  {
    id: "V5_scoring_1_to_5",
    note: "coarser 1-5 scale instead of 0-100",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const intro = `
Also, categorise each PURCHASED item. For each line, score up to 3 best-fit categories on an INTEGER scale 1-5 (5 = name directly names this item type; 4 = strong fit via close synonym; 3 = plausible fit; 2 = weak fit; 1 = unlikely). Return items[] with { description, scores: [{categoryId, score, reason}] } in descending score. Skip discounts/promos/subtotals; include "Sales Tax" as one item.

Also return multiCategoryLikely (true if items' top-1 categories span different real-world domains) and topChoice (when false).`;
      return HEADER_BASE + intro + SCORING_RULES + COMMON + `\n\nCategories:\n${categoryList(cats)}`;
    },
    parseResult(c1, cats) { return parseScoringByItems(c1, cats, { threshold: 0, gap: 0, scale: 5 }); },
  },

  {
    id: "V6_scoring_synonyms",
    note: "V2 + explicit synonym expansion for name matching",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const note = `\n\nSynonym hint: when scoring, treat closely-synonymous words as matches. E.g. "vehicle" ≈ "auto" ≈ "car"; "food" ≈ "grocery" ≈ "meal"; "medicine" ≈ "pharmacy" ≈ "health"; "clothing" ≈ "apparel" ≈ "garment"; "pet" ≈ "animal". A synonym match scores lower than a direct match but higher than no match.`;
      return HEADER_BASE + BASIC_SCORING_INTRO + SCORING_RULES + note + COMMON + `\n\nCategories:\n${categoryList(cats)}`;
    },
    parseResult(c1, cats) { return parseScoringByItems(c1, cats, { threshold: 0, gap: 0 }); },
  },

  {
    id: "V7_scoring_anti_catchall",
    note: "V2 + catch-all-named categories start with a score penalty",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const note = `\n\nAnti-catch-all: categories whose name contains a generic word ("Supplies", "Goods", "Items", "General", "Other", "Miscellaneous") start with a -20 score penalty. They only win when no specifically-named category plausibly fits.`;
      return HEADER_BASE + BASIC_SCORING_INTRO + SCORING_RULES + note + COMMON + `\n\nCategories:\n${categoryList(cats)}`;
    },
    parseResult(c1, cats) { return parseScoringByItems(c1, cats, { threshold: 0, gap: 0 }); },
  },

  {
    id: "V8_scoring_whole_receipt",
    note: "score each plausible category ONCE for the whole receipt, no per-item",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const intro = `
Also, score candidate categories for the WHOLE receipt. Return categoryScores: an array of 1-5 entries, each { categoryId, score (0-100), reason (≤15 words), itemsCovered (short text like "all items", "drinks only", "produce + dairy") }.

Also return:
  - multiCategoryLikely: true if 2+ categories have score ≥ 40 AND their "itemsCovered" texts describe non-overlapping subsets of the receipt.
  - topChoice: when false, the single highest-scoring categoryId.`;
      return HEADER_BASE + intro + SCORING_RULES + COMMON + `\n\nCategories:\n${categoryList(cats)}`;
    },
    parseResult(c1, cats) { return parseScoringWholeReceipt(c1, cats); },
  },

  {
    id: "V9_scoring_no_procedure",
    note: "pure scoring, no procedural wrapping",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const intro = `
Also, for each PURCHASED item on the receipt, score up to 3 plausible categories 0-100. Return items[] with { description, scores: [{categoryId, score, reason}] } in descending score. Skip discounts; include "Sales Tax".

Set multiCategoryLikely=true if different items have different top-1 categories (from different real-world domains). Set topChoice only when multiCategoryLikely=false.`;
      return HEADER_BASE + intro + COMMON + `\n\nCategories:\n${categoryList(cats)}`;
    },
    parseResult(c1, cats) { return parseScoringByItems(c1, cats, { threshold: 0, gap: 0 }); },
  },

  {
    id: "V10_scoring_plus_5step",
    note: "V2 + 5-step procedure (item → function → domain → scan → score)",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const intro = `
For each PURCHASED item on the receipt, follow these 5 steps:
  Step 1 (ITEM): name the literal thing (e.g. "rubber disc", "leather collar").
  Step 2 (FUNCTION): describe what it's used for (e.g. "creates friction on a car wheel"; "worn by a pet").
  Step 3 (DOMAIN): name the real-world domain in 1-3 nouns (e.g. "vehicle repair part"; "pet accessory").
  Step 4 (SCAN): for each category, evaluate whether its NAME contains a noun matching the Step 3 domain directly OR via a close synonym.
  Step 5 (SCORE): score up to 3 categories 0-100 based on how directly the name names the domain. Direct name-match = 80-100. Synonym match = 50-75. Weak fit = 20-50.

Return items[] with { description, scores: [{categoryId, score, reason}] }. Skip discounts; include "Sales Tax".
Also return multiCategoryLikely (true if items' top-1 domains differ) and topChoice (when false).`;
      return HEADER_BASE + intro + COMMON + `\n\nCategories:\n${categoryList(cats)}`;
    },
    parseResult(c1, cats) { return parseScoringByItems(c1, cats, { threshold: 0, gap: 0 }); },
  },
];

// ─── Parsers (derive multi / topChoice from scoring) ──────────────────

const isTax = d => typeof d === "string" && /sales\s*tax/i.test(d);

function parseLegacyControl(c1) {
  // Non-scoring variant (V1 control). Uses existing topChoice + candidates shape.
  return {
    multiCategoryLikely: c1.multiCategoryLikely === true,
    topChoice: c1.topChoice ?? c1.singleCategoryId,
    items: null,
  };
}

function parseScoringByItems(c1, cats, opts) {
  const validSet = new Set(cats.map(c => c.id));
  const items = Array.isArray(c1.items) ? c1.items : [];
  // Each item's "vote": the topmost valid score entry meeting threshold+gap.
  const votes = [];
  const perItem = [];
  for (const it of items) {
    if (isTax(it.description)) { perItem.push({ description: it.description, top: null }); continue; }
    const scores = Array.isArray(it.scores) ? it.scores.filter(s => validSet.has(s.categoryId)) : [];
    if (scores.length === 0) { perItem.push({ description: it.description, top: null }); continue; }
    scores.sort((a, b) => (b.score || 0) - (a.score || 0));
    const top1 = scores[0];
    const top2 = scores[1];
    const passesThreshold = (top1.score || 0) >= (opts.threshold || 0);
    const passesGap = !top2 || ((top1.score || 0) - (top2.score || 0) >= (opts.gap || 0));
    if (passesThreshold && passesGap) {
      votes.push(top1.categoryId);
      perItem.push({ description: it.description, top: top1.categoryId, score: top1.score });
    } else {
      perItem.push({ description: it.description, top: null, score: top1.score });
    }
  }

  // Collapse votes
  let multi, topChoice;
  if (votes.length === 0) {
    // No confident votes — fall back to model's own fields.
    multi = c1.multiCategoryLikely === true;
    topChoice = c1.topChoice ?? c1.singleCategoryId;
  } else {
    const distinctVotes = new Set(votes);
    if (distinctVotes.size === 1) {
      multi = false;
      topChoice = votes[0];
    } else if (distinctVotes.size >= 2) {
      // Multi IF the top-2 vote-winners come from different "domains".
      // Without a domain lookup, use a simpler rule: ≥2 distinct votes = multi.
      multi = true;
      // Dominant vote as tiebreaker for topChoice (not used when multi=true)
      const counts = new Map();
      for (const v of votes) counts.set(v, (counts.get(v) || 0) + 1);
      topChoice = [...counts.entries()].sort((a, b) => b[1] - a[1])[0][0];
    }
  }

  // Final fallback: if topChoice is invalid, use Other / first cat
  if (!validSet.has(topChoice)) topChoice = validSet.has(30426) ? 30426 : cats[0]?.id;

  return { multiCategoryLikely: multi, topChoice, items: perItem };
}

function parseScoringWholeReceipt(c1, cats) {
  const validSet = new Set(cats.map(c => c.id));
  const scores = Array.isArray(c1.categoryScores) ? c1.categoryScores.filter(s => validSet.has(s.categoryId)) : [];
  scores.sort((a, b) => (b.score || 0) - (a.score || 0));
  const highScoring = scores.filter(s => (s.score || 0) >= 40);
  const multi = highScoring.length >= 2;
  const topChoice = scores[0]?.categoryId ?? (validSet.has(30426) ? 30426 : cats[0]?.id);
  return { multiCategoryLikely: multi, topChoice, items: null };
}

// ─── Call 2 + Call 3 unchanged ────────────────────────────────────────

function call2Prompt(cats) {
  return `List every PURCHASED item with a category. Return JSON {lineItems: [{description, categoryId}]}.

Rules:
  - Skip promos, coupons, discounts, tenders, subtotals.
  - Include "Sales Tax" as a line item.

How to pick each item's category:
  1. Read each category's NAME as a description of what belongs there.
  2. Match by what the item IS (function or product type).
  3. Category names like "X/Y/Z" cover X, Y, AND Z.
  4. Prefer narrow over broad.
  5. "Other" is last resort.
  6. Do NOT invent categoryIds not in the list.

Categories:
${categoryList(cats)}`;
}

function call3Prompt(items) {
  const listed = items.map((it, i) => `  ${i + 1}. ${it.description}`).join("\n");
  return `For each item, determine the ACTUAL PAID PRICE in integer cents.

Clues: base printed price; quantity multipliers; line-level coupons reduce; weight-priced use printed total; "Sales Tax" → printed tax in cents.

Return JSON {prices: [{description, priceCents}]}, preserving order.

Items:
${listed}`;
}

// ─── Schemas ──────────────────────────────────────────────────────────

const SCORE_ITEM = {
  type: "object",
  properties: {
    categoryId: { type: "integer" },
    score: { type: "number" },
    reason: { type: "string" },
  },
  required: ["categoryId", "score"],
};

const ITEMS_ARRAY = {
  type: "array",
  items: {
    type: "object",
    properties: {
      description: { type: "string" },
      scores: { type: "array", items: SCORE_ITEM },
    },
    required: ["description", "scores"],
  },
};

const CALL1_SCHEMA_SCORING = {
  type: "object",
  properties: {
    merchant: { type: "string" }, merchantLegalName: { type: "string" },
    date: { type: "string" }, amountCents: { type: "integer" },
    items: ITEMS_ARRAY,
    categoryScores: { type: "array", items: { type: "object", properties: { categoryId: { type: "integer" }, score: { type: "number" }, reason: { type: "string" }, itemsCovered: { type: "string" } }, required: ["categoryId", "score"] } },
    multiCategoryLikely: { type: "boolean" },
    topChoice: { type: "integer" },
    notes: { type: "string" },
  },
  required: ["merchant", "date", "amountCents"],
};

const CALL1_SCHEMA_CONTROL = {
  type: "object",
  properties: {
    merchant: { type: "string" }, merchantLegalName: { type: "string" },
    date: { type: "string" }, amountCents: { type: "integer" },
    multiCategoryLikely: { type: "boolean" },
    candidates: { type: "array", items: { type: "object", properties: { categoryId: { type: "integer" }, reason: { type: "string" } }, required: ["categoryId", "reason"] } },
    topChoice: { type: "integer" }, notes: { type: "string" },
  },
  required: ["merchant", "date", "amountCents"],
};

const CALL2_SCHEMA = { type: "object", properties: { lineItems: { type: "array", items: { type: "object", properties: { description: { type: "string" }, categoryId: { type: "integer" } }, required: ["description", "categoryId"] } } }, required: ["lineItems"] };
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

function remapInvalidCategoryIds(items, cats) {
  const validSet = new Set(cats.map(c => c.id));
  const invalidIdx = [];
  for (let i = 0; i < items.length; i++) if (!validSet.has(items[i].categoryId)) invalidIdx.push(i);
  if (invalidIdx.length === 0) return items;
  let fallback = validSet.has(30426) ? 30426 : cats[0]?.id;
  return items.map((it, i) => invalidIdx.includes(i) ? { ...it, categoryId: fallback } : it);
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

async function runPipeline(imageBytes, mimeType, cats, variant) {
  const schema = variant.id === "V1_control_T7" ? CALL1_SCHEMA_CONTROL : CALL1_SCHEMA_SCORING;
  const c1 = await apiCall([{ text: variant.call1(cats, new Set()) }, { inlineData: { mimeType, data: imageBytes.toString("base64") } }], schema);
  const parsed = variant.parseResult(c1.parsed, cats);
  const multi = parsed.multiCategoryLikely === true;
  const validSet = new Set(cats.map(c => c.id));
  let categoryAmounts, route;

  if (multi) {
    route = "multi-full";
    const c2 = await apiCall([{ text: call2Prompt(cats) }, { inlineData: { mimeType, data: imageBytes.toString("base64") } }], CALL2_SCHEMA);
    const items = remapInvalidCategoryIds(c2.parsed.lineItems || [], cats);
    const c3 = await apiCall([{ text: call3Prompt(items) }, { inlineData: { mimeType, data: imageBytes.toString("base64") } }], CALL3_SCHEMA);
    const reconciled = reconcilePrices(c3.parsed.prices || [], c1.parsed.amountCents || 0);
    const byCat = new Map();
    for (let i = 0; i < items.length; i++) { if (isTax(items[i].description)) continue; byCat.set(items[i].categoryId, (byCat.get(items[i].categoryId) || 0) + (reconciled[i]?.priceCents || 0)); }
    const taxIdx = items.findIndex(it => isTax(it.description));
    if (taxIdx >= 0) { const taxCents = reconciled[taxIdx]?.priceCents || 0; let bestCat = null, bestCents = -1; for (const [cid, cents] of byCat) if (cents > bestCents) { bestCat = cid; bestCents = cents; } if (bestCat != null) byCat.set(bestCat, byCat.get(bestCat) + taxCents); }
    categoryAmounts = [...byCat.entries()].map(([categoryId, cents]) => ({ categoryId, amount: cents / 100 }));
  } else {
    route = "single-shortcircuit";
    let cid = parsed.topChoice;
    if (!validSet.has(cid)) cid = validSet.has(30426) ? 30426 : cats[0]?.id;
    categoryAmounts = cid != null ? [{ categoryId: cid, amount: (c1.parsed.amountCents || 0) / 100 }] : [];
  }
  return { parsed: { merchant: c1.parsed.merchant, merchantLegalName: c1.parsed.merchantLegalName, date: c1.parsed.date, amount: (c1.parsed.amountCents || 0) / 100, categoryAmounts }, c1: c1.parsed, route, derived: parsed };
}

(async () => {
  const labels = JSON.parse(fs.readFileSync(path.join(ROOT, "test-data", "labels.json"), "utf8"));
  const subset = pickSubset(labels);
  const cats = TEST_CATEGORIES;
  console.log(`Round 4: iterating ${VARIANTS.length} scoring variants on ${subset.length} receipts\n`);

  const results = {};
  for (const v of VARIANTS) {
    console.log(`─── ${v.id} ── ${v.note}`);
    const rows = [];
    for (const label of subset) {
      const imgPath = path.join(ROOT, "test-data", "images", label.file);
      if (!fs.existsSync(imgPath)) continue;
      const img = fs.readFileSync(imgPath);
      const mime = mimeFor(label.file);
      try { const res = await runPipeline(img, mime, cats, v); const grade = gradeResult(label, res.parsed); rows.push({ file: label.file, label, result: res, grade }); }
      catch (e) { rows.push({ file: label.file, label, err: e.message || String(e) }); }
    }
    results[v.id] = rows;
    const ok = rows.filter(r => r.result);
    const singlesOk = ok.filter(r => r.label.categoryAmounts?.length === 1 || (r.label.categoryAmounts == null && r.label.categoryId));
    const multiOk = ok.filter(r => r.label.categoryAmounts?.length > 1);
    const singlesCorrect = singlesOk.filter(r => { const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId; return r.result.parsed.categoryAmounts?.[0]?.categoryId === exp; }).length;
    const multiRouted = multiOk.filter(r => r.result.route === "multi-full").length;
    const multiCset = multiOk.filter(r => r.grade.categoryAmounts.setMatch).length;
    console.log(`   singles: ${singlesCorrect}/${singlesOk.length}  multi-routed: ${multiRouted}/${multiOk.length}  multi-cset: ${multiCset}/${multiOk.length}`);
  }

  console.log("\n" + "═".repeat(108));
  console.log("ROUND 4 RANKING");
  console.log("═".repeat(108));
  const ranked = VARIANTS.map(v => {
    const rows = results[v.id].filter(r => r.result);
    const singlesOk = rows.filter(r => r.label.categoryAmounts?.length === 1 || (r.label.categoryAmounts == null && r.label.categoryId));
    const multiOk = rows.filter(r => r.label.categoryAmounts?.length > 1);
    const singlesCorrect = singlesOk.filter(r => { const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId; return r.result.parsed.categoryAmounts?.[0]?.categoryId === exp; }).length;
    const multiRouted = multiOk.filter(r => r.result.route === "multi-full").length;
    const multiCset = multiOk.filter(r => r.grade.categoryAmounts.setMatch).length;
    const amazon = rows.filter(r => r.file.startsWith("amazon_"));
    const amazonCorrect = amazon.filter(r => { const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId; return r.result.parsed.categoryAmounts?.[0]?.categoryId === exp; }).length;
    return { id: v.id, note: v.note, singlesCorrect, singlesTot: singlesOk.length, multiRouted, multiTot: multiOk.length, multiCset, amazonCorrect, combined: singlesCorrect + multiRouted };
  }).sort((a, b) => (b.combined - a.combined) || (b.amazonCorrect - a.amazonCorrect) || (b.multiCset - a.multiCset));

  console.log(`${"id".padEnd(32)} ${"singles".padEnd(8)} ${"multi-rt".padEnd(9)} ${"multi-c".padEnd(8)} ${"amzn".padEnd(5)} ${"combined"} ${"note"}`);
  for (const r of ranked) {
    console.log(`${r.id.padEnd(32)} ${(r.singlesCorrect + "/" + r.singlesTot).padEnd(8)} ${(r.multiRouted + "/" + r.multiTot).padEnd(9)} ${(r.multiCset + "/" + r.multiTot).padEnd(8)} ${(r.amazonCorrect + "/3").padEnd(5)} ${String(r.combined).padEnd(8)} ${r.note}`);
  }
  console.log(`\nWinner: ${ranked[0].id}`);

  const outPath = path.join(ROOT, "results", `iterate-nopresel-r4-${new Date().toISOString().replace(/[:.]/g, "-")}.json`);
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, JSON.stringify({ variants: VARIANTS.map(v => ({ id: v.id, note: v.note })), results, ranked }, null, 2));
  console.log(`Saved → ${path.relative(ROOT, outPath)}`);
})();
