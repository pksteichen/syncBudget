#!/usr/bin/env node
// Focused Lite benchmark: category accuracy on single-category receipts only.
// Filters out multi-cat receipts and Vietnamese (no category labels), then runs
// Flash-Lite with the R11 transcribe-then-extract prompt.

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

const categoryList = TEST_CATEGORIES
  .map(c => c.tag ? `  - id=${c.id} name="${c.name}" tag="${c.tag}"` : `  - id=${c.id} name="${c.name}"`)
  .join("\n");

// Best Lite variant from Round 8: R11 base (transcribe + integer cents) with
// the R7-T10 category rules (C3+C4+C6+MP) prepended cat-first.
const C3 = `\n\nCATEGORY RULE — itemize then consolidate:\nStep 1: For each line item, assign {description, price, categoryId}. Step 2: Group by categoryId and sum. Step 3: Return one categoryAmounts entry per distinct categoryId.`;
const C4 = `\n\nCATEGORY RULE — use tax markers:\nReceipts often mark each item with tax codes (T, F, N, S, O). Taxed non-food (T) → Home Supplies (30186) or Other (30426); non-taxed (N, F) → Groceries (22695).`;
const C6 = `\n\nCATEGORY RULE — detailed category→item mapping:\nFood/produce/meat/pantry → 22695 Groceries.\nBeverages from a cafe → 21716; bottled drinks from a supermarket → 22695.\nCleaning, paper towels, ziploc/foil, pet items → 30186 Home Supplies.\nBatteries, stationery, pens, office supplies → 30426 Other.\nKids' toys, school workbooks, kids clothes → 1276 Kid's Stuff.\nPharmacy/OTC medicine → 17351 Health/Pharmacy.\nFuel, parking, tolls → 48281 Transportation/Gas.\nWork safety gear, uniforms → 47837 Employment Expenses.\nHardware, electrical, lighting, paint → 30186 Home Supplies.`;
const MP = `\n\nPRIORITY REMINDER: merchant and amount are the most important fields. Do not compromise them while attending to category work. Merchant MUST be the consumer brand (not a cashier name, customer name, or translated English word).`;

const LITE_BASE = `Extract receipt data as JSON: {transcription, merchant, merchantLegalName?, date, amountCents (integer), categoryAmounts?}.

Stage 1: transcription = every line of the receipt as plain text.
Stage 2: extract remaining fields.

Amount is INTEGER CENTS. Ignore GST-summary pre-tax rows.

- date MUST be YYYY-MM-DD ISO.
- merchant is the consumer brand. Preserve original language.
- merchantLegalName is optional.
- Do NOT return a cashier/customer personal name as merchant.
- Category ids must come from:
${categoryList}`;

const LITE_PROMPT = C3 + C4 + C6 + MP + "\n\n" + LITE_BASE;

const SCHEMA = {
  type: "object",
  properties: {
    transcription: { type: "string" },
    merchant: { type: "string" }, merchantLegalName: { type: "string" },
    date: { type: "string" }, amountCents: { type: "integer" },
    categoryAmounts: { type: "array", items: { type: "object", properties: { categoryId: { type: "integer" }, amount: { type: "number" } }, required: ["categoryId", "amount"] } },
    notes: { type: "string" },
  },
  required: ["transcription", "merchant", "date", "amountCents"],
};

const client = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

async function call({ imageBytes, mimeType }) {
  let lastErr;
  for (let attempt = 1; attempt <= 4; attempt++) {
    try {
      const res = await client.models.generateContent({
        model: LITE,
        contents: [{ role: "user", parts: [{ text: LITE_PROMPT }, { inlineData: { mimeType, data: imageBytes.toString("base64") } }] }],
        config: { responseMimeType: "application/json", responseSchema: SCHEMA, temperature: 0 },
      });
      const parsed = JSON.parse(res.text);
      if (typeof parsed.amountCents === "number" && parsed.amount === undefined) parsed.amount = parsed.amountCents / 100;
      return { parsed, tokens: res.usageMetadata };
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
  // Single-cat with a category label (exclude Vietnamese MC-OCR which has no categoryAmounts)
  const single = labels.filter(l => l.categoryAmounts && l.categoryAmounts.length === 1);
  console.log(`Lite single-category bench on ${single.length} receipts\n`);

  const bySource = {};
  const results = [];
  let idx = 0;
  for (const label of single) {
    idx++;
    if (idx % 10 === 0) process.stdout.write(`.`);
    const img = fs.readFileSync(path.join(ROOT, "test-data", "images", label.file));
    const mime = mimeFor(label.file);
    try {
      const r = await call({ imageBytes: img, mimeType: mime });
      const grade = gradeResult(label, r.parsed);
      const row = { file: label.file, source: label.source, grade };
      results.push(row);
      (bySource[label.source] = bySource[label.source] || []).push(row);
    } catch (e) {
      console.log(`\n  [err ${label.file}] ${e.message.slice(0,50)}`);
    }
  }
  console.log();

  const print = (label, rows) => {
    const n = rows.length; if (!n) return;
    const m = rows.filter(r => r.grade.merchant.pass).length;
    const d = rows.filter(r => r.grade.date.pass).length;
    const a = rows.filter(r => r.grade.amount.pass).length;
    const cset = rows.filter(r => r.grade.categoryAmounts?.setMatch).length;
    const pct = (x) => ((x/n)*100).toFixed(0).padStart(3);
    console.log(`  ${label.padEnd(25)} n=${String(n).padStart(3)}  m ${pct(m)}%  d ${pct(d)}%  a ${pct(a)}%  cat ${pct(cset)}%`);
  };
  console.log("\n=== Lite single-cat breakdown ===");
  for (const src of Object.keys(bySource).sort()) print(src, bySource[src]);
  print("all", results);

  // Miss details for category
  const catMisses = results.filter(r => !r.grade.categoryAmounts?.setMatch);
  if (catMisses.length) {
    console.log(`\n=== ${catMisses.length} category misses ===`);
    for (const r of catMisses) {
      const exp = r.grade.categoryAmounts.expected.map(c => c.categoryId).join(",");
      const act = r.grade.categoryAmounts.actual?.map(c => c.categoryId).join(",") || "(none)";
      console.log(`  ${r.file}  expected ${exp}  got ${act}`);
    }
  }

  const outFile = path.join(ROOT, "results", `lite-singlecat-${new Date().toISOString().replace(/[:.]/g,"-")}.json`);
  fs.writeFileSync(outFile, JSON.stringify({ run: "lite-singlecat", results }, null, 2));
  console.log(`\nSaved → ${path.relative(ROOT, outFile)}`);
}

main().catch(e => { console.error("Fatal:", e.stack || e.message); process.exit(1); });
