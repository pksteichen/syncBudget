#!/usr/bin/env node
// Test whether passing a user-pre-selected subset of categories improves
// multi-category accuracy. Hypothesis: fewer options in the prompt = fewer
// misclassification chances.
//
// Test receipt: sams_club.jpg (label: 22695 Groceries + 30186 Home Supplies + 30426 Other)
// Also Target (22695 + 30186) and Amazon-191443 (17351 + 30426 + 57937) for breadth.

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

const TESTS = [
  {
    file: "sams_club.jpg",
    variants: [
      { id: "A_full",   cats: TEST_CATEGORIES },
      { id: "B_ground", cats: TEST_CATEGORIES.filter(c => [22695, 30186, 30426].includes(c.id)) },
      { id: "C_user",   cats: TEST_CATEGORIES.filter(c => [22695, 30186].includes(c.id)) },
    ],
  },
  {
    file: "target.jpg",
    variants: [
      { id: "A_full",   cats: TEST_CATEGORIES },
      { id: "B_ground", cats: TEST_CATEGORIES.filter(c => [22695, 30186].includes(c.id)) },
    ],
  },
  {
    file: "Screenshot_20260415_191443_Amazon Shopping.jpg",
    variants: [
      { id: "A_full",   cats: TEST_CATEGORIES },
      { id: "B_ground", cats: TEST_CATEGORIES.filter(c => [17351, 30426, 57937].includes(c.id)) },
    ],
  },
];

const C3 = `\n\nCATEGORY RULE — itemize then consolidate:\nStep 1: For each line item, assign {description, price, categoryId}. Step 2: Group by categoryId and sum. Step 3: Return one categoryAmounts entry per distinct categoryId.`;
const C4 = `\n\nCATEGORY RULE — use tax markers:\nReceipts often mark each item with tax codes (T, F, N, S, O). Taxed non-food (T) → Home Supplies (30186) or Other (30426); non-taxed (N, F) → Groceries (22695).`;
const C6 = `\n\nCATEGORY RULE — detailed category→item mapping:\nFood/produce/meat/pantry → 22695 Groceries.\nBeverages from a cafe → 21716; bottled drinks from a supermarket → 22695.\nCleaning, paper towels, ziploc/foil, pet items → 30186 Home Supplies.\nBatteries, stationery, pens, office supplies → 30426 Other.\nKids' toys, school workbooks, kids clothes → 1276 Kid's Stuff.\nPharmacy/OTC medicine → 17351 Health/Pharmacy.\nFuel, parking, tolls → 48281 Transportation/Gas.\nWork safety gear, uniforms → 47837 Employment Expenses.\nHardware, electrical, lighting, paint → 30186 Home Supplies.`;
const MP = `\n\nPRIORITY REMINDER: merchant and amount are the most important fields. Do not compromise them while attending to category work. Merchant MUST be the consumer brand (not a cashier name, customer name, or translated English word).`;

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
  console.log(`Pre-selected category test — 2 runs per variant\n`);

  const results = [];
  for (const test of TESTS) {
    const label = labels.find(l => l.file === test.file);
    if (!label) { console.log(`  [skip] ${test.file} — no label`); continue; }
    console.log(`=== ${test.file} (label cats: ${label.categoryAmounts.map(c => c.categoryId).join(", ")}) ===`);
    for (const variant of test.variants) {
      const prompt = C3 + C4 + C6 + MP + "\n\n" + buildPrompt(variant.cats);
      const img = fs.readFileSync(path.join(ROOT, "test-data", "images", test.file));
      const mime = mimeFor(test.file);
      for (const run of [1, 2]) {
        process.stdout.write(`  ${variant.id.padEnd(10)} cats=${variant.cats.length} run${run} `);
        try {
          const t0 = Date.now();
          const r = await call({ imageBytes: img, mimeType: mime, prompt });
          const ms = Date.now() - t0;
          const grade = gradeResult(label, r.parsed);
          const actualCats = r.parsed.categoryAmounts?.map(c => `${c.categoryId}:${c.amount}`).join(",") || "";
          const cset = grade.categoryAmounts?.setMatch ? "✓" : "✗";
          const cshr = grade.categoryAmounts?.shareMatch ? "✓" : "✗";
          console.log(`m${grade.merchant.pass?"✓":"✗"} a${grade.amount.pass?"✓":"✗"} cset${cset} cshr${cshr} out=${r.tokens?.candidatesTokenCount||"?"}tok ${ms}ms [${actualCats}]`);
          results.push({ file: test.file, variant: variant.id, run, grade, actualCats, tokens: r.tokens, ms });
        } catch (e) {
          console.log(`ERR ${e.message.slice(0,60)}`);
        }
      }
    }
    console.log();
  }

  const outFile = path.join(ROOT, "results", `preselect-${new Date().toISOString().replace(/[:.]/g,"-")}.json`);
  fs.writeFileSync(outFile, JSON.stringify({ run: "preselect-categories", results }, null, 2));
  console.log(`Saved → ${path.relative(ROOT, outFile)}`);
}

main().catch(e => { console.error("Fatal:", e.stack || e.message); process.exit(1); });
