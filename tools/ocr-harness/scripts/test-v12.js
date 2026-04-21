#!/usr/bin/env node
// V12 candidate: V11's narrower anti-broad rule + Step 0 TRANSCRIBE first.
// Hypothesis: separating "read the receipt" (image-dependent) from
// "categorize items" (text-dependent) stabilises categorization across
// JPEG encoder differences. Plus narrowed rule so it only fires when
// a broad-name cat competes with a directly-named cat — never pushes
// Kid's Stuff over Clothes because neither has "Supplies/Goods/etc".

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

const HEADER_BASE = `Extract receipt header data as JSON.

- merchant: the consumer brand. Preserve original language.
- merchantLegalName: optional, when clearly distinct.
- date: YYYY-MM-DD ISO.
- amountCents: INTEGER cents for the paid total (tax and tip included). Ignore subtotal/pre-tax/tax-summary rows.`;

function v12Prompt(cats) {
  return HEADER_BASE + `

Step 0 (TRANSCRIBE): return transcription — the full printed text of the receipt, line by line, verbatim. This is your working copy for the rest of the steps. Once transcribed, all downstream reasoning is on the transcription, not the image.

For each PURCHASED item in the transcription, follow these 5 steps (skip promos/coupons/discounts/subtotals/tenders; include "Sales Tax" as a line item):
  Step 1 (ITEM): name the literal thing (e.g. "rubber disc", "leather collar").
  Step 2 (FUNCTION): describe what it's used for.
  Step 3 (DOMAIN): name the real-world domain in 1-3 nouns.
  Step 4 (SCAN): for each category, evaluate whether its NAME contains a noun matching the Step 3 domain directly OR via a close synonym.
  Step 5 (SCORE): score up to 3 categories 0-100.
    - A category whose name DIRECTLY contains a word naming the item's domain (e.g. "Transportation" for a car part, "Pet" for a pet accessory, "Phone" for a phone charger) = 80-100.
    - A category whose name doesn't contain the domain word but is a close synonym = 50-75.
    - A weak or tangential fit = 20-50.
    - Catch-all tie-break (narrow): if one candidate's name contains a generic catch-all word ("Supplies", "Goods", "Items", "General", "Miscellaneous") AND another candidate's name directly contains the domain word, score the directly-named category higher. This tie-break applies ONLY to catch-all-vs-specific pairings — it does NOT reorder two specific categories.

Return items[] with { description, scores: [{categoryId, score, reason}] }.
Also return multiCategoryLikely and topChoice.

Category-picking constraints (global):
  - "Other" is reserved for items that no other category name plausibly describes.
  - Do NOT invent categoryIds not in the list.

Categories:
${categoryList(cats)}`;
}

const SCHEMA = {
  type: "object",
  properties: {
    merchant: { type: "string" }, merchantLegalName: { type: "string" },
    date: { type: "string" }, amountCents: { type: "integer" },
    transcription: { type: "string" },
    items: { type: "array", items: { type: "object", properties: { description: { type: "string" }, scores: { type: "array", items: { type: "object", properties: { categoryId: { type: "integer" }, score: { type: "number" }, reason: { type: "string" } }, required: ["categoryId", "score"] } } }, required: ["description", "scores"] } },
    multiCategoryLikely: { type: "boolean" }, topChoice: { type: "integer" }, notes: { type: "string" },
  },
  required: ["merchant", "date", "amountCents"],
};

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

(async () => {
  const labels = JSON.parse(fs.readFileSync(path.join(ROOT, "test-data", "labels.json"), "utf8"));
  const subset = pickSubset(labels);
  const cats = TEST_CATEGORIES;
  const nm = Object.fromEntries(cats.map(c => [c.id, c.name]));

  console.log(`V12 (transcribe-first + narrow anti-broad) on ${subset.length} receipts\n`);

  const rows = [];
  for (const label of subset) {
    const imgPath = path.join(ROOT, "test-data", "images", label.file);
    if (!fs.existsSync(imgPath)) continue;
    const img = fs.readFileSync(imgPath);
    const mime = mimeFor(label.file);
    process.stdout.write(`▶ ${label.file.slice(0, 44).padEnd(44)}  `);
    try {
      const res = await client.models.generateContent({
        model: LITE,
        contents: [{ role: "user", parts: [
          { text: v12Prompt(cats) },
          { inlineData: { mimeType: mime, data: img.toString("base64") } },
        ] }],
        config: { responseMimeType: "application/json", responseSchema: SCHEMA, temperature: 0 },
      });
      const parsed = JSON.parse(res.text);
      const multi = deriveMulti(parsed.items, cats, parsed.multiCategoryLikely);
      // Build categoryAmounts from items' top scores (no Call 3 in this simple test)
      const validSet = new Set(cats.map(c => c.id));
      let categoryAmounts;
      if (multi) {
        const byCat = new Map();
        for (const it of (parsed.items || [])) {
          if (isTax(it.description)) continue;
          const scores = (it.scores || []).filter(s => validSet.has(s.categoryId));
          if (scores.length === 0) continue;
          scores.sort((a,b) => (b.score||0) - (a.score||0));
          byCat.set(scores[0].categoryId, (byCat.get(scores[0].categoryId) || 0) + 1);
        }
        categoryAmounts = [...byCat.entries()].map(([cid, n]) => ({ categoryId: cid, amount: (parsed.amountCents || 0) / 100 / byCat.size }));
      } else {
        const cid = parsed.topChoice ?? 30426;
        categoryAmounts = [{ categoryId: cid, amount: (parsed.amountCents || 0) / 100 }];
      }
      const result = { merchant: parsed.merchant, date: parsed.date, amount: (parsed.amountCents || 0) / 100, categoryAmounts };
      const grade = gradeResult(label, result);
      rows.push({ file: label.file, label, parsed, result, grade, multi });
      const exp = label.categoryAmounts?.length === 1 ? label.categoryAmounts[0].categoryId : "multi";
      const got = categoryAmounts.map(c => c.categoryId).join(",");
      const cset = grade.categoryAmounts.setMatch ? "✓" : "✗";
      const route = multi ? "multi" : "single";
      console.log(`${route.padEnd(8)} cset${cset}  exp=${exp} got=${got}`);
    } catch (e) {
      console.log(`FAIL: ${e.message?.slice(0, 60)}`);
      rows.push({ file: label.file, label, err: e.message });
    }
  }

  console.log("\n═══ V12 SUMMARY ═══");
  const ok = rows.filter(r => r.result);
  const singlesOk = ok.filter(r => r.label.categoryAmounts?.length === 1 || (r.label.categoryAmounts == null && r.label.categoryId));
  const multiOk = ok.filter(r => r.label.categoryAmounts?.length > 1);
  const singlesCorrect = singlesOk.filter(r => { const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId; return r.result.categoryAmounts?.[0]?.categoryId === exp; }).length;
  const multiRouted = multiOk.filter(r => r.multi).length;
  const multiCset = multiOk.filter(r => r.grade.categoryAmounts.setMatch).length;
  const amazonCorrect = ok.filter(r => r.file.startsWith("amazon_")).filter(r => { const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId; return r.result.categoryAmounts?.[0]?.categoryId === exp; }).length;

  console.log(`Singles:      ${singlesCorrect}/${singlesOk.length}`);
  console.log(`Multi routed: ${multiRouted}/${multiOk.length}`);
  console.log(`Multi cset:   ${multiCset}/${multiOk.length}`);
  console.log(`Amazon:       ${amazonCorrect}/3`);
  console.log(`Combined:     ${singlesCorrect + multiRouted}`);

  // Per-cat singles
  console.log(`\nPer-category (single-cat):`);
  const perCat = new Map();
  for (const r of singlesOk) {
    const exp = r.label.categoryAmounts?.[0]?.categoryId ?? r.label.categoryId;
    const got = r.result.categoryAmounts?.[0]?.categoryId;
    if (!perCat.has(exp)) perCat.set(exp, { correct: 0, n: 0 });
    perCat.get(exp).n++;
    if (got === exp) perCat.get(exp).correct++;
  }
  for (const [cid, s] of perCat) console.log(`  ${(nm[cid] || cid).padEnd(32)} ${s.correct}/${s.n}`);

  // Spot-check target_long_1 per-item (the regression case)
  const t1 = rows.find(r => r.file === "target_long_1.jpg");
  if (t1?.parsed) {
    console.log(`\ntarget_long_1 per-item top scores:`);
    for (const it of (t1.parsed.items || []).slice(0, 15)) {
      const top = [...(it.scores || [])].sort((a, b) => (b.score || 0) - (a.score || 0))[0];
      console.log(`  ${it.description.slice(0, 24).padEnd(24)} → ${nm[top?.categoryId] || "?"}(${top?.score})`);
    }
  }

  const outPath = path.join(ROOT, "results", `validate-v12-${new Date().toISOString().replace(/[:.]/g, "-")}.json`);
  fs.writeFileSync(outPath, JSON.stringify(rows, null, 2));
  console.log(`\nSaved → ${path.relative(ROOT, outPath)}`);
})();
