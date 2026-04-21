#!/usr/bin/env node
// Test: does adding an explicit "prefer specifically-named over
// broad-sounding" rule fix the brakes → Home Supplies failure the user
// is seeing on device? Uses app-sized (1000px) brake receipt.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import "dotenv/config";
import { GoogleGenAI } from "@google/genai";
import { TEST_CATEGORIES } from "../src/categories.js";

const __filename = fileURLToPath(import.meta.url);
const ROOT = path.resolve(path.dirname(__filename), "..");
const LITE = "gemini-2.5-flash-lite";
const client = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

function categoryList(cats) {
  return cats.map(c => c.tag ? `  - id=${c.id} name="${c.name}" tag="${c.tag}"` : `  - id=${c.id} name="${c.name}"`).join("\n");
}

const HEADER = `Extract receipt header data as JSON.

- merchant: the consumer brand. Preserve original language.
- date: YYYY-MM-DD ISO.
- amountCents: INTEGER number of cents for the final paid total.`;

// Shipped V10 prompt
function v10Prompt(cats) {
  return HEADER + `

For each PURCHASED item on the receipt, follow these 5 steps:
  Step 1 (ITEM): name the literal thing.
  Step 2 (FUNCTION): describe what it's used for.
  Step 3 (DOMAIN): name the real-world domain in 1-3 nouns.
  Step 4 (SCAN): for each category, evaluate whether its NAME contains a noun matching the Step 3 domain directly OR via a close synonym.
  Step 5 (SCORE): score up to 3 categories 0-100 based on how directly the name names the domain. Direct name-match = 80-100. Synonym match = 50-75. Weak fit = 20-50.

Return items[] with { description, scores: [{categoryId, score, reason}] }. Skip discounts; include "Sales Tax".
Also return multiCategoryLikely and topChoice.

Category-picking constraints (global):
  - "Other" is reserved for items that no other category name plausibly describes.
  - Do NOT invent categoryIds not in the list.

Categories:
${categoryList(cats)}`;
}

// V11 candidate: add explicit anti-broad-catch-all rule to Step 5
function v11Prompt(cats) {
  return HEADER + `

For each PURCHASED item on the receipt, follow these 5 steps:
  Step 1 (ITEM): name the literal thing.
  Step 2 (FUNCTION): describe what it's used for.
  Step 3 (DOMAIN): name the real-world domain in 1-3 nouns.
  Step 4 (SCAN): for each category, evaluate whether its NAME contains a noun matching the Step 3 domain directly OR via a close synonym.
  Step 5 (SCORE): score up to 3 categories 0-100.
    - A category whose name DIRECTLY contains a word naming the item's domain (e.g. name contains "Transportation" for a car part, "Pet" for a pet accessory, "Phone" for a phone charger) = 80-100.
    - A category whose name doesn't contain the domain word but is a close synonym = 50-75.
    - A weak or tangential fit = 20-50.
    - IMPORTANT: do NOT score a broad-sounding category (e.g. name contains "Supplies", "Goods", "Items", "General") ABOVE a category whose name directly names the domain. A directly-named category always wins over a broader-sounding one when both plausibly fit. A category is not a "catch-all" just because its name is generic — interpret it narrowly to the words it actually contains.

Return items[] with { description, scores: [{categoryId, score, reason}] }. Skip discounts; include "Sales Tax".
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
    items: { type: "array", items: { type: "object", properties: { description: { type: "string" }, scores: { type: "array", items: { type: "object", properties: { categoryId: { type: "integer" }, score: { type: "number" }, reason: { type: "string" } }, required: ["categoryId", "score"] } } }, required: ["description", "scores"] } },
    multiCategoryLikely: { type: "boolean" }, topChoice: { type: "integer" }, notes: { type: "string" },
  },
  required: ["merchant", "date", "amountCents"],
};

(async () => {
  const cats = TEST_CATEGORIES;
  const nm = Object.fromEntries(cats.map(c => [c.id, c.name]));

  for (const variant of [{ id: "v10", fn: v10Prompt }, { id: "v11", fn: v11Prompt }]) {
    console.log(`\n=== ${variant.id} ===`);
    for (const fname of ["amazon_brakepads.jpg", "amazon_charger.jpg", "amazon_tierodboots.jpg"]) {
      const img = fs.readFileSync(path.join(ROOT, "test-data", "amazon_app_sized", fname));
      const res = await client.models.generateContent({
        model: LITE,
        contents: [{ role: "user", parts: [
          { text: variant.fn(cats) },
          { inlineData: { mimeType: "image/jpeg", data: img.toString("base64") } },
        ] }],
        config: { responseMimeType: "application/json", responseSchema: SCHEMA, temperature: 0 },
      });
      const p = JSON.parse(res.text);
      const item0 = p.items?.[0];
      const top = [...(item0?.scores || [])].sort((a, b) => (b.score || 0) - (a.score || 0))[0];
      console.log(`  ${fname.padEnd(28)} topChoice=${nm[p.topChoice]}(${p.topChoice})  item1_top=${nm[top?.categoryId]}(${top?.score})`);
    }
  }
})();
