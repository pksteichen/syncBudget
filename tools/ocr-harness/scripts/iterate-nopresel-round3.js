#!/usr/bin/env node
// Round 3: step-decomposition is the lever — explore MORE GRANULAR
// procedures, not rule tweaks on T7's 3 steps.
//
// Round 1 winner: T7 (Step A/B/C).  Round 2 winner: T7 control again
// (rule tweaks didn't stack).  User observation: step-by-step reasoning
// is what's helping.  Round 3: finer-grained procedures.
//
// Constraint reminder: NO real categories/products in prompts.

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
  for (const s of singles) {
    const cid = s.categoryAmounts?.[0]?.categoryId ?? s.categoryId;
    if (!byCat.has(cid)) byCat.set(cid, []);
    byCat.get(cid).push(s);
  }
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

const RULES_BASE = `
Category-picking constraints (global):
  - "Other" is reserved for items that no other category name plausibly describes.
  - Do NOT invent categoryIds not in the list.`;

// T7 control for reference baseline
function t7RoutingBase() {
  return `
Also return routing hints. Before picking a category, follow this procedure:
  Step A: identify the PRIMARY PRODUCT TYPE on the receipt in 1-3 generic nouns (e.g. "footwear", "pet food", "electronic device"). If items clearly span multiple product types, set multiCategoryLikely=true and stop.
  Step B: for each category in the list, check whether its NAME contains any noun matching or closely synonymous to the product type from Step A.
  Step C: the winning category is the one whose name most directly names the product type. Tie-break toward more specific names.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice.`;
}

const VARIANTS = [
  {
    id: "U1_T7_control",
    note: "T7 verbatim (round 2 winner, baseline reference)",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      return HEADER_BASE + t7RoutingBase() + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "U2_5step_fine",
    note: "5-step procedure: item → function → domain → scan names → pick",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Follow this FIVE-step procedure, filling each step before moving to the next:
  Step 1 (ITEM): name the literal thing being purchased in 1-3 words (e.g. "ceramic mug", "leather collar", "rubber disc", "paper notebook").
  Step 2 (FUNCTION): describe what it is USED FOR or DOES (e.g. "a ceramic mug is used for drinking"; "a leather collar is worn by a pet"; "a rubber disc fits on a car wheel to create friction").
  Step 3 (DOMAIN): name the real-world domain the function belongs to in 1-3 nouns (e.g. "drinkware", "pet accessory", "vehicle repair part"). If the receipt has items in 2+ clearly distinct domains, set multiCategoryLikely=true and stop.
  Step 4 (SCAN): for each category in the list, note whether its NAME contains a noun matching or closely synonymous to the Step 3 domain.
  Step 5 (PICK): winning category = the name that most directly names the Step 3 domain. Tie-break toward the narrower/more-specific name.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice. Each candidate.reason (≤15 words) should state the domain and which word in the category name matched.`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "U3_enumerate_items",
    note: "Step 1 enumerates EVERY item-type on the receipt before collapsing",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Follow this procedure:
  Step 1: list every DISTINCT item-type on the receipt, each in 1-3 generic nouns. Example lists: ["footwear"] for a shoe-only receipt; ["produce", "dairy", "bread"] for a grocery trip; ["phone accessory", "laptop case"] for an electronics order.
  Step 2: look at your list from Step 1. If it contains 2+ items belonging to clearly different real-world domains, set multiCategoryLikely=true and stop.
  Step 3: if the list collapses to a single domain, identify it (e.g. produce+dairy+bread = "food"; phone accessory+laptop case = "computer-and-phone gear").
  Step 4: scan each category's NAME for nouns matching or closely synonymous to the Step 3 domain.
  Step 5: winning category = the name that most directly names the domain. Narrower wins tie-breaks.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice.`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "U4_split_A_thing_and_domain",
    note: "Split T7's Step A into 'name the thing' + 'name its domain'",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Follow this FOUR-step procedure:
  Step A1 (THING): name what's being purchased in 1-3 literal-noun words (e.g. "rubber disc", "leather collar", "paper notebook", "electric cable").
  Step A2 (DOMAIN): name the domain that thing belongs to in 1-3 generic nouns — describing its USE-CASE, not its material (e.g. "rubber disc on a car" → "vehicle repair part"; "leather collar on a pet" → "pet accessory"; "paper notebook" → "stationery"). If the receipt spans 2+ distinct domains, set multiCategoryLikely=true and stop.
  Step B: for each category in the list, check whether its NAME contains any noun matching or closely synonymous to the Step A2 domain.
  Step C: winning category = the name that most directly names the Step A2 domain. Narrower wins tie-breaks.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice.`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "U5_multi_decision_first",
    note: "Dedicated multi/single decision step BEFORE identifying product type",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Follow this procedure:
  Step 0 (COUNT): count distinct item-types on the receipt. Are they:
    (a) variations of ONE product type (e.g. several kinds of food), OR
    (b) spanning 2+ DIFFERENT product types (e.g. shoes AND food)?
  If (b), set multiCategoryLikely=true and stop.
  Step 1: if (a), name the single product type in 1-3 generic nouns.
  Step 2: for each category in the list, check if its NAME contains any noun matching or closely synonymous to that product type.
  Step 3: winning category = the name that most directly names the product type. Narrower wins tie-breaks.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice.`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "U6_broadest_and_narrowest",
    note: "Step B returns BROADEST and NARROWEST matching names; Step C picks narrower",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Follow this procedure:
  Step A: identify the PRIMARY PRODUCT TYPE on the receipt in 1-3 generic nouns. If items span multiple product types, set multiCategoryLikely=true and stop.
  Step B1: scan every category NAME. Identify the BROADEST-sounding name that plausibly covers the product type (broader = longer name, more generic words).
  Step B2: scan again. Identify the NARROWEST-sounding name that plausibly covers the product type (narrower = name that specifically names the type).
  Step C: winner = Step B2's narrowest name, UNLESS Step B2 is noticeably worse a fit than Step B1 (in which case pick Step B1).

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice.`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "U7_fictional_walkthrough_multi",
    note: "fictional step-by-step walk-through of a multi-cat receipt",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const extra = `

Fictional walk-through (NOT your real data) — illustrates what multi-cat looks like:
  Imagine a receipt with 3 lines: "LEATHER LEASH", "CRACKERS", "LAPTOP BAG".
  Step A per line:
    - "LEATHER LEASH" → pet accessory
    - "CRACKERS" → food
    - "LAPTOP BAG" → computer accessory
  Those are 3 clearly different domains (pet, food, electronics).
  → Set multiCategoryLikely=true. Do NOT try to collapse to one category.

Now imagine a different receipt: "PAPERBACK NOVEL", "HARDCOVER NOVEL", "E-READER".
  Step A per line:
    - "PAPERBACK NOVEL" → book
    - "HARDCOVER NOVEL" → book
    - "E-READER" → reading device
  Those all share the "reading" domain.
  → Single-category. Scan names for "read" or "book" words.`;
      return HEADER_BASE + t7RoutingBase() + RULES_BASE + extra + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "U8_synonym_expansion",
    note: "Step B explicitly considers synonyms before deciding name doesn't match",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Follow this procedure:
  Step A: identify the PRIMARY PRODUCT TYPE on the receipt in 1-3 generic nouns. If items clearly span multiple product types, set multiCategoryLikely=true and stop.
  Step B: for each category, check whether its NAME contains any noun that matches the product type directly OR is a close synonym (e.g. "vehicle" ≈ "car" ≈ "auto"; "food" ≈ "groceries" ≈ "meal"; "medicine" ≈ "pharmacy" ≈ "health"; "garment" ≈ "clothing" ≈ "apparel"). Before concluding a category name doesn't match, expand through synonyms.
  Step C: winning category = the name that most directly names the product type (direct match > synonym match). Narrower wins tie-breaks.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice.`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "U9_reject_reasoning",
    note: "final step: name a runner-up and why it lost — forces comparison",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Follow this procedure:
  Step A: identify the PRIMARY PRODUCT TYPE on the receipt in 1-3 generic nouns. If items span multiple product types, set multiCategoryLikely=true and stop.
  Step B: for each category in the list, check whether its NAME contains any noun matching or closely synonymous to the product type.
  Step C: winning category = name that most directly names the product type. Narrower wins tie-breaks.
  Step D (COMPARE): identify a RUNNER-UP category (if one plausibly existed) and state briefly why the winner beat it. If the runner-up's name has a generic catch-all word ("Supplies", "Goods", "General", "Other") while the winner's name specifically names the domain, the winner must win.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice. Candidates[0] = winner; candidates[1] = runner-up; candidate[0].reason should reference the comparison.`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "U10_combined_fine",
    note: "U2's 5-step + U7's fictional walk-through + U8's synonyms",
    call1(cats, preSel) {
      if (preSel.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Follow this FIVE-step procedure:
  Step 1 (ITEM): name the literal thing being purchased in 1-3 words.
  Step 2 (FUNCTION): describe what it is USED FOR or DOES.
  Step 3 (DOMAIN): name the domain in 1-3 generic nouns. If the receipt has items in 2+ distinct domains, set multiCategoryLikely=true and stop.
  Step 4 (SCAN): for each category NAME, check for a noun matching the Step 3 domain directly OR via close synonyms (e.g. "vehicle" ≈ "auto" ≈ "car"; "food" ≈ "groceries"; "medicine" ≈ "pharmacy"; "garment" ≈ "clothing").
  Step 5 (PICK): winner = name that most directly names the domain. Narrower wins tie-breaks.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice.`;
      const extra = `

Fictional walk-through (NOT your real data):
  Receipt lines: "RUBBER DISC (for a car)", "WINDSHIELD WIPER".
    Step 1: "rubber disc" / "wiper blade"
    Step 2: "disc creates friction on a wheel" / "wiper clears glass"
    Step 3: BOTH = "vehicle repair part" — single domain.
    Step 4: scan names for "vehicle" / "auto" / "car".
    Step 5: pick the name with one of those words.`;
      return HEADER_BASE + routing + RULES_BASE + extra + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },
];

// Call 2 + Call 3 unchanged
function call2Prompt(cats, preselected) {
  const preselectNudge = preselected
    ? `\n\n  - The categories below are pre-selected by the shopper for this receipt. Try to cover as many as reasonably fit. Don't force-fit.`
    : "";
  return `List every PURCHASED item with a category. Return JSON {lineItems: [{description, categoryId}]}.

Rules:
  - Skip promos, coupons, discounts, tenders, subtotals.
  - Include "Sales Tax" as a line item.

How to pick each item's category:
  1. Read each category's NAME as a description of what belongs there.
  2. Match by what the item IS (function or product type), not where it was bought.
  3. Category names like "X/Y/Z" cover X, Y, AND Z.
  4. Prefer narrow over broad when both fit.
  5. "Other" is last resort.
  6. Do NOT invent categoryIds not in the list.${preselectNudge}

Categories:
${categoryList(cats)}`;
}

function call3Prompt(items) {
  const listed = items.map((it, i) => `  ${i + 1}. ${it.description}`).join("\n");
  return `You have a receipt image and a list of items. For each item, determine the ACTUAL PAID PRICE in integer cents.

Clues:
  1. Base printed line price.
  2. Quantity multiplier: "2 AT 1 FOR \$X.XX" → multiplier × unit.
  3. Line-level coupons reduce that item's price.
  4. Weight-priced: use printed total.
  5. "Sales Tax": printed tax in cents.

Return JSON {prices: [{description, priceCents}]}, preserving order.

Items:
${listed}`;
}

const CALL1_SCHEMA = { type: "object", properties: { merchant: { type: "string" }, merchantLegalName: { type: "string" }, date: { type: "string" }, amountCents: { type: "integer" }, multiCategoryLikely: { type: "boolean" }, candidates: { type: "array", items: { type: "object", properties: { categoryId: { type: "integer" }, reason: { type: "string" } }, required: ["categoryId", "reason"] } }, topChoice: { type: "integer" }, notes: { type: "string" } }, required: ["merchant", "date", "amountCents"] };
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
    for (let i = 0; i < reconciled.length; i++) { if (i === taxIdx) continue; if (reconciled[i].priceCents > largestVal) { largestVal = reconciled[i].priceCents; largestIdx = i; } }
    if (largestIdx >= 0) reconciled[largestIdx].priceCents += residual;
  }
  return reconciled;
}

async function runPipeline(imageBytes, mimeType, cats, variant) {
  const c1 = await apiCall([{ text: variant.call1(cats, new Set()) }, { inlineData: { mimeType, data: imageBytes.toString("base64") } }], CALL1_SCHEMA);
  const multi = c1.parsed.multiCategoryLikely === true;
  const singleCid = c1.parsed.topChoice ?? c1.parsed.singleCategoryId;
  const validSet = new Set(cats.map(c => c.id));
  let categoryAmounts, route;

  if (multi) {
    route = "multi-full";
    const c2 = await apiCall([{ text: call2Prompt(cats, false) }, { inlineData: { mimeType, data: imageBytes.toString("base64") } }], CALL2_SCHEMA);
    const items = remapInvalidCategoryIds(c2.parsed.lineItems || [], cats);
    const c3 = await apiCall([{ text: call3Prompt(items) }, { inlineData: { mimeType, data: imageBytes.toString("base64") } }], CALL3_SCHEMA);
    const reconciled = reconcilePrices(c3.parsed.prices || [], c1.parsed.amountCents || 0);
    const byCat = new Map();
    const isTax = d => typeof d === "string" && /sales\s*tax/i.test(d);
    for (let i = 0; i < items.length; i++) { if (isTax(items[i].description)) continue; byCat.set(items[i].categoryId, (byCat.get(items[i].categoryId) || 0) + (reconciled[i]?.priceCents || 0)); }
    const taxIdx = items.findIndex(it => isTax(it.description));
    if (taxIdx >= 0) { const taxCents = reconciled[taxIdx]?.priceCents || 0; let bestCat = null, bestCents = -1; for (const [cid, cents] of byCat) if (cents > bestCents) { bestCat = cid; bestCents = cents; } if (bestCat != null) byCat.set(bestCat, byCat.get(bestCat) + taxCents); }
    categoryAmounts = [...byCat.entries()].map(([categoryId, cents]) => ({ categoryId, amount: cents / 100 }));
  } else {
    route = "single-shortcircuit";
    let cid = singleCid;
    if (!validSet.has(cid)) cid = validSet.has(30426) ? 30426 : cats[0]?.id;
    categoryAmounts = cid != null ? [{ categoryId: cid, amount: (c1.parsed.amountCents || 0) / 100 }] : [];
  }
  return { parsed: { merchant: c1.parsed.merchant, merchantLegalName: c1.parsed.merchantLegalName, date: c1.parsed.date, amount: (c1.parsed.amountCents || 0) / 100, categoryAmounts }, c1: c1.parsed, route };
}

(async () => {
  const labels = JSON.parse(fs.readFileSync(path.join(ROOT, "test-data", "labels.json"), "utf8"));
  const subset = pickSubset(labels);
  const cats = TEST_CATEGORIES;
  console.log(`Round 3: iterating ${VARIANTS.length} variants on ${subset.length} receipts\n`);

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
  console.log("ROUND 3 RANKING");
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

  console.log(`${"id".padEnd(30)} ${"singles".padEnd(8)} ${"multi-rt".padEnd(9)} ${"multi-c".padEnd(8)} ${"amzn".padEnd(5)} ${"combined"} ${"note"}`);
  for (const r of ranked) {
    console.log(`${r.id.padEnd(30)} ${(r.singlesCorrect + "/" + r.singlesTot).padEnd(8)} ${(r.multiRouted + "/" + r.multiTot).padEnd(9)} ${(r.multiCset + "/" + r.multiTot).padEnd(8)} ${(r.amazonCorrect + "/3").padEnd(5)} ${String(r.combined).padEnd(8)} ${r.note}`);
  }
  console.log(`\nWinner: ${ranked[0].id}`);

  const outPath = path.join(ROOT, "results", `iterate-nopresel-r3-${new Date().toISOString().replace(/[:.]/g, "-")}.json`);
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, JSON.stringify({ variants: VARIANTS.map(v => ({ id: v.id, note: v.note })), results, ranked }, null, 2));
  console.log(`Saved → ${path.relative(ROOT, outPath)}`);
})();
