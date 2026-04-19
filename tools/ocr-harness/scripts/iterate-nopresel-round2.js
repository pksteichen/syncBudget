#!/usr/bin/env node
// Round 2: perturb T7_item_first (round 1 winner).
//
// T7's structure (keep): Step A (identify primary product type) → Step B
// (scan category names) → Step C (pick most direct match). Return
// candidates + topChoice unless multi.
//
// Same constraint as round 1: NO references to user's real categories or
// receipts in our test bank. All fictional.

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

const HEADER_BASE = `Extract receipt header data as JSON.

- merchant: the consumer brand (e.g. "McDonald's", "Target", "Costco"). Prefer the consumer brand over the legal operator entity. Preserve original language; don't translate.
- merchantLegalName: optional, only when the legal entity is clearly distinct from the consumer brand.
- date: YYYY-MM-DD ISO. Receipts often have multiple dates; prefer the transaction date over print/due dates. DD/MM locales: Malaysia, Singapore, Vietnam, most of Europe. MM/DD: US.
- amountCents: INTEGER number of cents for the final paid total (tax and tip included). Ignore subtotal, pre-tax lines, and separate GST/VAT summary tables. Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are thousand separators; return the integer đồng value.`;

// Rule set kept simple since T7 embeds the procedure inside the routing block.
const RULES_BASE = `
Category-picking constraints (global):
  - "Other" is reserved for items that no other category name plausibly describes.
  - Do NOT invent categoryIds not in the list.`;

// T7 base procedure
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
    id: "T7_0_control",
    note: "T7 verbatim (re-run as control)",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      return HEADER_BASE + t7RoutingBase() + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T7a_domain_not_supplies",
    note: "Step A must name a DOMAIN, not generic 'supplies/goods'",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Before picking a category, follow this procedure:
  Step A: identify the PRIMARY DOMAIN of the purchase as 1-3 concrete nouns. Good domains: "footwear", "veterinary care", "vehicle repair part", "prepared meal", "medicine", "electronic device", "toy", "cosmetic", "book". AVOID abstract domain words like "supplies", "goods", "items", "products", "stuff" — those carry no signal. If items clearly span multiple distinct domains, set multiCategoryLikely=true and stop.
  Step B: for each category in the list, check whether its NAME contains any noun matching or closely synonymous to the domain from Step A.
  Step C: the winning category is the one whose name most directly names the domain.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice.`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T7b_usecase_over_provenance",
    note: "tie-break: name describing use-case wins over name describing where sold",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const routing = t7RoutingBase() + `
Tie-break rule for Step C: when two category names both plausibly fit the product type, pick the name that describes the product's USE-CASE or DOMAIN over a name that just describes where it might be SOLD. E.g. for a fictional "yoga mat" with fictional categories "Sports/Fitness" and "Retail Store Goods", pick "Sports/Fitness" — its name names the use-case; the other just names the provenance.`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T7c_multi_when_A_lists_multiple",
    note: "force multi=true when Step A finds 2+ distinct nouns",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Follow this procedure:
  Step A: identify the PRIMARY PRODUCT TYPE as 1-3 concrete nouns (e.g. "footwear", "vehicle repair part", "prepared meal"). If the receipt contains items whose types would require 2+ DIFFERENT such nouns that don't share a common parent (e.g. shoes + food), set multiCategoryLikely=true and stop.
  Step B: for each category in the list, check whether its NAME contains any noun matching or closely synonymous to the product type from Step A.
  Step C: the winning category is the one whose name most directly names the product type.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice.`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T7d_fictional_worked",
    note: "fictional A/B/C worked example with a non-obvious product",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const extra = `

Fictional worked example (NOT your real data):
  Imagine the receipt is a single line: "LEATHER COLLAR". Imagine the category list contains "Pet Care/Veterinary", "Apparel/Accessories", "Generic Goods", and "Other".
  Step A: product type = "pet accessory" (a collar in this context attaches to a pet).
  Step B: scan names. "Pet Care/Veterinary" has "Pet" — pet accessory fits. "Apparel/Accessories" has "Accessories" — could fit a human accessory; weaker match for a pet item. "Generic Goods" matches nothing specific.
  Step C: "Pet Care/Veterinary" wins (name names the domain directly). reason: "Matches 'Pet' in the name — a leather collar is a pet accessory."
Do the same three steps on YOUR receipt and YOUR categories.`;
      return HEADER_BASE + t7RoutingBase() + RULES_BASE + extra + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T7e_anti_catchall",
    note: "Step C: never pick a generic-sounding name when a specific one also fits",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const routing = t7RoutingBase() + `
Anti-catch-all for Step C: if both (a) a category whose name contains a generic word like "Supplies", "Goods", "Items", "Miscellaneous", or "General" AND (b) a category whose name directly names the product type both plausibly fit, ALWAYS pick (b). Generic-sounding names are last-resort even if the receipt items could technically be described as "supplies".`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T7f_cite_word",
    note: "T7 + reason must cite the exact matching word from category name",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Follow this procedure:
  Step A: identify the PRIMARY PRODUCT TYPE on the receipt in 1-3 generic nouns. If items clearly span multiple product types, set multiCategoryLikely=true and stop.
  Step B: for each category in the list, check whether its NAME contains any noun matching or closely synonymous to the product type from Step A.
  Step C: the winning category is the one whose name most directly names the product type.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice. Each candidate.reason (≤15 words) MUST cite the exact word from the category name that matches the product type (e.g. reason: "Matches word 'Lodging' — a hotel is lodging."). If no category name contains a matching word, pick "Other".`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T7g_other_clarification",
    note: "Step B clarifies when Other is the right answer",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const routing = t7RoutingBase() + `
Clarification on "Other": Only pick "Other" when Step B finds NO category whose name contains a matching or closely-synonymous noun. If Step B finds even one weakly-matching name, prefer that over "Other". "Other" is NOT a tie-breaker or uncertainty outlet.`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T7h_function_not_packaging",
    note: "Step A must describe FUNCTION, not container/packaging/material",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Follow this procedure:
  Step A: identify the PRIMARY PRODUCT TYPE on the receipt in 1-3 generic nouns describing the item's FUNCTION or PURPOSE — what it is USED FOR — not its material or packaging. E.g. "ceramic mug with handle" is "drinkware"; "rubber disc" used on a car is "vehicle repair part"; "cotton cloth" used for cleaning is "cleaning cloth". If items clearly span multiple distinct functions, set multiCategoryLikely=true and stop.
  Step B: for each category in the list, check whether its NAME contains any noun matching or closely synonymous to the product type from Step A.
  Step C: the winning category is the one whose name most directly names the product type.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice.`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },

  {
    id: "T7i_combo_cite_and_anticatch",
    note: "T7 + cite-word + anti-catch-all (best of T4 + T7e)",
    call1(cats, preSelected) {
      if (preSelected.size > 0) return HEADER_BASE;
      const routing = `
Also return routing hints. Follow this procedure:
  Step A: identify the PRIMARY PRODUCT TYPE on the receipt in 1-3 generic nouns. If items clearly span multiple product types, set multiCategoryLikely=true and stop.
  Step B: for each category in the list, check whether its NAME contains any noun matching or closely synonymous to the product type from Step A.
  Step C: the winning category is the one whose name most directly names the product type. Anti-catch-all: if a category whose name contains a generic word like "Supplies", "Goods", "Items", or "General" AND a category whose name directly names the product type both plausibly fit, ALWAYS pick the one that names the type.

Return multiCategoryLikely, and when false, candidates (1-3, descending) + topChoice. Each reason (≤15 words) MUST cite the exact word from the category name that matches (e.g. "Matches 'Footwear' — shoes are footwear.").`;
      return HEADER_BASE + routing + RULES_BASE + `\n\nCategories:\n${categoryList(cats)}`;
    },
  },
];

// Call 2 + Call 3 same as round 1
function call2Prompt(cats, preselected) {
  const preselectNudge = preselected
    ? `\n\n  - The categories below are pre-selected by the shopper for this receipt. Try to cover as many of them as reasonably fit — the shopper expects to see items in these specific buckets. But skip a category if no item on the receipt plausibly fits it; never force-fit an item into a bucket that clearly doesn't match.`
    : "";
  return `List every PURCHASED item with a category. Return JSON {lineItems: [{description, categoryId}]}.

Rules:
  - Skip promos, coupons, discounts, tenders, subtotals.
  - Include "Sales Tax" as a line item.

How to pick each item's category:
  1. Read each category's NAME as a description of what belongs there. The name is your primary signal.
  2. Match by what the item IS (its function or product type), not by where it was purchased.
  3. When a category name lists multiple things (e.g. "X/Y/Z"), treat ALL of them as in scope.
  4. Prefer a narrow/specialty category over a broad catch-all when an item plausibly fits both.
  5. "Other" is reserved for items that no other category name plausibly describes.
  6. Do NOT invent categoryIds not in the list.${preselectNudge}

Categories:
${categoryList(cats)}`;
}

function call3Prompt(items) {
  const listed = items.map((it, i) => `  ${i + 1}. ${it.description}`).join("\n");
  return `You have a receipt image and a list of items the shopper purchased. For each item, find it on the receipt and determine the ACTUAL PAID PRICE in integer cents.

Clues:
  1. Base printed price for the item's line.
  2. Quantity multiplier: lines like "2 AT 1 FOR \$X.XX" mean multiplier × unit price.
  3. Line-level coupons reduce that item's price.
  4. Weight-priced items: use the computed total already printed.
  5. For "Sales Tax", return the printed tax amount in cents.

Return JSON {prices: [{description, priceCents}]}, preserving order.

Items:
${listed}`;
}

const CALL1_SCHEMA = {
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
      const res = await client.models.generateContent({ model: LITE, contents: [{ role: "user", parts }], config: { responseMimeType: "application/json", responseSchema: schema, temperature: 0 } });
      return { parsed: JSON.parse(res.text), tokens: res.usageMetadata };
    } catch (e) {
      lastErr = e;
      if (!/503|UNAVAILABLE|overloaded|429|RESOURCE_EXHAUSTED|deadline|fetch failed|network|ECONNRESET|ETIMEDOUT|socket/i.test(String(e.message || e)) || attempt === 4) throw e;
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
    for (let i = 0; i < reconciled.length; i++) {
      if (i === taxIdx) continue;
      if (reconciled[i].priceCents > largestVal) { largestVal = reconciled[i].priceCents; largestIdx = i; }
    }
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
  console.log(`Round 2: iterating ${VARIANTS.length} variants on ${subset.length} receipts (full ${cats.length}-cat list, no preselect)\n`);

  const results = {};
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
      } catch (e) { rows.push({ file: label.file, label, err: e.message || String(e) }); }
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
  console.log("ROUND 2 RANKING");
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

  const outPath = path.join(ROOT, "results", `iterate-nopresel-r2-${new Date().toISOString().replace(/[:.]/g, "-")}.json`);
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, JSON.stringify({ variants: VARIANTS.map(v => ({ id: v.id, note: v.note })), results, ranked }, null, 2));
  console.log(`Saved → ${path.relative(ROOT, outPath)}`);
})();
