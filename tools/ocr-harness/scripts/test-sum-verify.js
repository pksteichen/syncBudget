#!/usr/bin/env node
// A/B: does adding a sum-verification step to R7-T10 eliminate the 2 save-
// blocking sum mismatches per 30 calls (walmart_2 $1 drift, sams_2 $19 drift)?
//
// Variant A: R7-T10 baseline
// Variant V: R7-T10 + sum verification directive (below)
//
// Metric of interest: sumMatch (Σcategoryamounts == amount within $0.05).
// Secondary: cset, cshare, amount accuracy (shouldn't regress).

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import "dotenv/config";
import { GoogleGenAI } from "@google/genai";

import { TEST_CATEGORIES } from "../src/categories.js";
import { gradeResult } from "../src/grader.js";
import { buildPrompt } from "../src/prompt.js";

const __filename = fileURLToPath(import.meta.url);
const ROOT = path.resolve(path.dirname(__filename), "..");
const FLASH = "gemini-2.5-flash";

const C3 = `\n\nCATEGORY RULE — itemize then consolidate:\nStep 1: For each line item, assign {description, price, categoryId}. Step 2: Group by categoryId and sum. Step 3: Return one categoryAmounts entry per distinct categoryId.`;
const C4 = `\n\nCATEGORY RULE — use tax markers:\nReceipts often mark each item with tax codes (T, F, N, S, O). Taxed non-food (T) → Home Supplies (30186) or Other (30426); non-taxed (N, F) → Groceries (22695).`;
const C6 = `\n\nCATEGORY RULE — detailed category→item mapping:\nFood/produce/meat/pantry → 22695 Groceries.\nBeverages from a cafe → 21716; bottled drinks from a supermarket → 22695.\nCleaning, paper towels, ziploc/foil, pet items → 30186 Home Supplies.\nBatteries, stationery, pens, office supplies → 30426 Other.\nKids' toys, school workbooks, kids clothes → 1276 Kid's Stuff.\nPharmacy/OTC medicine → 17351 Health/Pharmacy.\nFuel, parking, tolls → 48281 Transportation/Gas.\nWork safety gear, uniforms → 47837 Employment Expenses.\nHardware, electrical, lighting, paint → 30186 Home Supplies.`;
const MP = `\n\nPRIORITY REMINDER: merchant and amount are the most important fields. Do not compromise them while attending to category work. Merchant MUST be the consumer brand (not a cashier name, customer name, or translated English word).`;

// The new verification directive. Key wording:
// - Makes the sum invariant an explicit hard rule
// - Says WHAT to do on mismatch (recheck, tax allocation, missing items)
// - Ties it directly to the amount field
const SV = `\n\nCRITICAL SUM INVARIANT: After composing categoryAmounts, the sum of their amounts MUST equal the receipt total (the amount field) within $0.05. Before returning, perform this check:
  sum = Σ(entry.amount for entry in categoryAmounts)
  if |sum − amount| > 0.05:
    re-examine your itemization. Common causes of drift:
      • tax lines not allocated to any category
      • an item counted in the wrong bucket AND missed entirely from another
      • a discount / coupon line not subtracted
    fix the categoryAmounts so the sum equals amount.
If you cannot reconcile (e.g., damaged receipt with illegible line items), return a single categoryAmounts entry whose amount equals the receipt total — NEVER return entries whose sum fails the invariant.`;

const SCHEMA = {
  type: "object",
  properties: {
    merchant: { type: "string" }, merchantLegalName: { type: "string" },
    date: { type: "string" }, amount: { type: "number" },
    categoryAmounts: { type: "array", items: { type: "object", properties: { categoryId: { type: "integer" }, amount: { type: "number" } }, required: ["categoryId", "amount"] } },
    notes: { type: "string" },
  },
  required: ["merchant", "date", "amount"],
};

const client = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

async function call({ imageBytes, mimeType, prompt }) {
  let lastErr;
  for (let attempt = 1; attempt <= 4; attempt++) {
    try {
      const res = await client.models.generateContent({
        model: FLASH,
        contents: [{ role: "user", parts: [{ text: prompt }, { inlineData: { mimeType, data: imageBytes.toString("base64") } }] }],
        config: { responseMimeType: "application/json", responseSchema: SCHEMA, temperature: 0 },
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

function mimeFor(f) {
  const e = path.extname(f).toLowerCase();
  if (e === ".png") return "image/png";
  if (e === ".webp") return "image/webp";
  return "image/jpeg";
}

async function main() {
  const labels = JSON.parse(fs.readFileSync(path.join(ROOT, "test-data", "labels.json"), "utf8"));
  const multi = labels.filter(l => l.categoryAmounts && l.categoryAmounts.length > 1);
  console.log(`Sum-verify A/B on ${multi.length} multi-cat receipts × 2 variants × 2 runs = ${multi.length*4} Flash calls\n`);

  const results = [];
  for (const label of multi) {
    const groundCatIds = label.categoryAmounts.map(c => c.categoryId);
    const constrainedCats = TEST_CATEGORIES.filter(c => groundCatIds.includes(c.id));
    const basePrompt = C3 + C4 + C6 + MP + "\n\n" + buildPrompt(constrainedCats);
    const variants = [
      { id: "A_base",   prompt: basePrompt },
      { id: "V_verify", prompt: C3 + C4 + C6 + MP + SV + "\n\n" + buildPrompt(constrainedCats) },
    ];
    console.log(`=== ${label.file} (label cats: ${groundCatIds.join(", ")}) ===`);
    for (const variant of variants) {
      const img = fs.readFileSync(path.join(ROOT, "test-data", "images", label.file));
      const mime = mimeFor(label.file);
      for (const run of [1, 2]) {
        process.stdout.write(`  ${variant.id.padEnd(9)} run${run} `);
        try {
          const t0 = Date.now();
          const r = await call({ imageBytes: img, mimeType: mime, prompt: variant.prompt });
          const ms = Date.now() - t0;
          const grade = gradeResult(label, r.parsed);
          const modelAmount = r.parsed.amount;
          const ca = r.parsed.categoryAmounts || [];
          const sumCA = ca.reduce((s, c) => s + (c.amount || 0), 0);
          const drift = Math.abs(sumCA - modelAmount);
          const sumMatch = drift <= 0.05;
          const cset = grade.categoryAmounts?.setMatch ? "✓" : "✗";
          const cshr = grade.categoryAmounts?.shareMatch ? "✓" : "✗";
          console.log(`m${grade.merchant.pass?"✓":"✗"} d${grade.date.pass?"✓":"✗"} a${grade.amount.pass?"✓":"✗"} sum${sumMatch?"✓":"✗"} cset${cset} cshr${cshr} drift $${drift.toFixed(2)} out=${r.tokens?.candidatesTokenCount||"?"}tok ${ms}ms`);
          results.push({ file: label.file, variant: variant.id, run, grade, sumMatch, drift, modelAmount, sumCA, tokens: r.tokens, ms });
        } catch (e) {
          console.log(`ERR ${e.message.slice(0,60)}`);
        }
      }
    }
    console.log();
  }

  // Aggregate
  console.log(`=== Aggregate (${multi.length} receipts × 2 runs = ${multi.length*2} calls/variant) ===`);
  for (const vid of ["A_base", "V_verify"]) {
    const rs = results.filter(r => r.variant === vid && r.grade);
    const n = rs.length;
    const m = rs.filter(r => r.grade.merchant.pass).length;
    const a = rs.filter(r => r.grade.amount.pass).length;
    const sum = rs.filter(r => r.sumMatch).length;
    const cset = rs.filter(r => r.grade.categoryAmounts?.setMatch).length;
    const cshr = rs.filter(r => r.grade.categoryAmounts?.shareMatch).length;
    const avgOut = rs.reduce((s,r) => s + (r.tokens?.candidatesTokenCount || 0), 0) / n;
    const avgMs = rs.reduce((s,r) => s + r.ms, 0) / n;
    console.log(`${vid}: m ${m}/${n}  a ${a}/${n}  sum ${sum}/${n}  cset ${cset}/${n}  cshr ${cshr}/${n}  avgOut ${avgOut.toFixed(0)}tok  avgMs ${avgMs.toFixed(0)}`);
  }

  const outFile = path.join(ROOT, "results", `sum-verify-${new Date().toISOString().replace(/[:.]/g,"-")}.json`);
  fs.writeFileSync(outFile, JSON.stringify({ run: "sum-verify", results }, null, 2));
  console.log(`\nSaved → ${path.relative(ROOT, outFile)}`);
}

main().catch(e => { console.error("Fatal:", e.stack || e.message); process.exit(1); });
