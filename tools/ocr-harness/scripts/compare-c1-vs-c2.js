#!/usr/bin/env node
// Compare V10's Call 1 per-item scoring against Call 2's lineItems
// categorisation, on the 5 multi-cat receipts. Goal: determine whether
// Call 2 is doing meaningful additional work or if Call 1's item[] top-1
// scores could replace it and cut one API call per multi receipt.
//
// Usage: node scripts/compare-c1-vs-c2.js

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

// V10 Call 1 prompt (scoring + 5-step) verbatim.
function v10Call1(cats) {
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
}

// Call 2 prompt (v2 / round 4 variant)
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
const CALL2_SCHEMA = { type: "object", properties: { lineItems: { type: "array", items: { type: "object", properties: { description: { type: "string" }, categoryId: { type: "integer" } }, required: ["description", "categoryId"] } } }, required: ["lineItems"] };

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

const isTax = d => typeof d === "string" && /sales\s*tax/i.test(d);

// Fuzzy-match Call 1 items to Call 2 items by description similarity.
// Receipts often render the same description identically in both calls since
// both are reading the same image. Position-based matching can drift if one
// call skipped an item — description match is safer.
function matchItems(c1items, c2items) {
  // Greedy: for each c2 item, find the best c1 match by normalised string
  // equality or substring. Track used c1 indexes to avoid double-matching.
  const norm = s => (s || "").toUpperCase().replace(/[^A-Z0-9]+/g, " ").trim();
  const used = new Set();
  const pairs = [];
  for (const c2 of c2items) {
    const c2n = norm(c2.description);
    let bestIdx = -1, bestScore = -1;
    for (let i = 0; i < c1items.length; i++) {
      if (used.has(i)) continue;
      const c1n = norm(c1items[i].description);
      if (!c1n) continue;
      let score = 0;
      if (c1n === c2n) score = 100;
      else if (c1n.includes(c2n) || c2n.includes(c1n)) score = 70;
      else {
        // token overlap
        const t1 = new Set(c1n.split(/\s+/));
        const t2 = c2n.split(/\s+/);
        const shared = t2.filter(t => t1.has(t)).length;
        if (shared > 0) score = Math.round(100 * shared / Math.max(t1.size, t2.length));
      }
      if (score > bestScore) { bestScore = score; bestIdx = i; }
    }
    if (bestIdx >= 0 && bestScore >= 40) { used.add(bestIdx); pairs.push({ c1: c1items[bestIdx], c2, matchScore: bestScore }); }
    else pairs.push({ c1: null, c2, matchScore: 0 });
  }
  return pairs;
}

(async () => {
  const labels = JSON.parse(fs.readFileSync(path.join(ROOT, "test-data", "labels.json"), "utf8"));
  // Same 5 multi-cat picks as the iterate runner.
  const multi = labels.filter(l => !l.file.startsWith("mcocr_") && l.categoryAmounts?.length > 1)
    .sort((a, b) => (b.categoryAmounts.length - a.categoryAmounts.length) || a.file.localeCompare(b.file))
    .slice(0, 5);
  const cats = TEST_CATEGORIES;
  const validSet = new Set(cats.map(c => c.id));
  const nm = Object.fromEntries(cats.map(c => [c.id, c.name]));

  console.log(`Comparing Call 1 (V10 scoring) vs Call 2 (lineItems) on ${multi.length} multi-cat receipts.\n`);

  const summary = [];

  for (const label of multi) {
    const imgPath = path.join(ROOT, "test-data", "images", label.file);
    if (!fs.existsSync(imgPath)) { console.log(`  [skip] ${label.file} — image missing`); continue; }
    const img = fs.readFileSync(imgPath);
    const mime = mimeFor(label.file);

    console.log(`─── ${label.file} ───`);
    const c1 = await apiCall([{ text: v10Call1(cats) }, { inlineData: { mimeType: mime, data: img.toString("base64") } }], CALL1_SCHEMA);
    const c2 = await apiCall([{ text: call2Prompt(cats) }, { inlineData: { mimeType: mime, data: img.toString("base64") } }], CALL2_SCHEMA);

    const c1items = (c1.parsed.items || []).map(it => {
      const top = [...(it.scores || [])].sort((a, b) => (b.score || 0) - (a.score || 0))[0];
      return { description: it.description, topCid: top?.categoryId, topScore: top?.score };
    });
    const c2items = (c2.parsed.lineItems || []).map(it => ({ description: it.description, cid: it.categoryId }));

    console.log(`  Call 1 items: ${c1items.length}    Call 2 items: ${c2items.length}`);

    const pairs = matchItems(c1items, c2items);
    let agree = 0, disagree = 0, unmatched = 0;
    const disagreements = [];
    for (const p of pairs) {
      if (!p.c1) { unmatched++; continue; }
      if (p.c1.topCid === p.c2.cid) agree++;
      else { disagree++; disagreements.push(p); }
    }
    console.log(`  Agreement (matched items): ${agree}/${agree+disagree} = ${agree+disagree>0?(100*agree/(agree+disagree)).toFixed(1):0}%`);
    console.log(`  Unmatched Call 2 items: ${unmatched}`);
    if (disagreements.length > 0) {
      console.log(`  Disagreements:`);
      for (const d of disagreements.slice(0, 6)) {
        console.log(`    "${d.c2.description.slice(0,28).padEnd(28)}"  C1→${nm[d.c1.topCid]||d.c1.topCid}(${d.c1.topScore}) vs C2→${nm[d.c2.cid]||d.c2.cid}`);
      }
      if (disagreements.length > 6) console.log(`    ... ${disagreements.length - 6} more`);
    }

    // What would multi-cset look like if we used Call 1 items directly as the
    // categorised item list? Compute cset from Call 1's item top-1 cats.
    const c1Cset = new Set(c1items.map(it => it.topCid).filter(x => validSet.has(x)));
    const c2Cset = new Set(c2items.map(it => it.cid).filter(x => validSet.has(x)));
    const expectedCset = new Set(label.categoryAmounts.map(c => c.categoryId));
    const setEq = (a, b) => a.size === b.size && [...a].every(x => b.has(x));
    const c1Covers = [...expectedCset].every(x => c1Cset.has(x));
    const c2Covers = [...expectedCset].every(x => c2Cset.has(x));
    console.log(`  cset expected: {${[...expectedCset].map(x=>nm[x]).join(', ')}}`);
    console.log(`  cset from C1:  {${[...c1Cset].map(x=>nm[x]).join(', ')}}  covers-expected=${c1Covers}`);
    console.log(`  cset from C2:  {${[...c2Cset].map(x=>nm[x]).join(', ')}}  covers-expected=${c2Covers}`);
    console.log();

    summary.push({ file: label.file, c1n: c1items.length, c2n: c2items.length, agree, disagree, unmatched, c1Covers, c2Covers });
  }

  console.log("═".repeat(96));
  console.log("SUMMARY");
  console.log("═".repeat(96));
  const totalAgree = summary.reduce((s, r) => s + r.agree, 0);
  const totalDisagree = summary.reduce((s, r) => s + r.disagree, 0);
  const totalUnmatched = summary.reduce((s, r) => s + r.unmatched, 0);
  console.log(`Aggregate agreement: ${totalAgree}/${totalAgree+totalDisagree} = ${(100*totalAgree/(totalAgree+totalDisagree)).toFixed(1)}%`);
  console.log(`Unmatched Call 2 items: ${totalUnmatched}`);
  console.log();
  console.log(`cset coverage of expected categories:`);
  console.log(`  Call 1 items: ${summary.filter(r=>r.c1Covers).length}/${summary.length} receipts`);
  console.log(`  Call 2 items: ${summary.filter(r=>r.c2Covers).length}/${summary.length} receipts`);

  const outPath = path.join(ROOT, "results", `c1-vs-c2-${new Date().toISOString().replace(/[:.]/g, "-")}.json`);
  fs.writeFileSync(outPath, JSON.stringify({ summary }, null, 2));
  console.log(`\nSaved → ${path.relative(ROOT, outPath)}`);
})();
