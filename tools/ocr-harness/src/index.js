#!/usr/bin/env node
// Main runner. Usage:
//   npm start                       # Gemini only (default)
//   npm run gemini                  # same
//   npm run anthropic               # Anthropic only (needs ANTHROPIC_API_KEY)
//   npm run both                    # side-by-side
//   node src/index.js --limit 5     # run first 5 test items only
//   node src/index.js --verbose     # dump each raw response

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import "dotenv/config";

import { buildPrompt, PROMPT_VERSION } from "./prompt.js";
import { TEST_CATEGORIES } from "./categories.js";
import { extractWithGemini, isGeminiConfigured } from "./providers/gemini.js";
import { extractWithAnthropic, isAnthropicConfigured } from "./providers/anthropic.js";
import { gradeResult, summarize, pct } from "./grader.js";

const __filename = fileURLToPath(import.meta.url);
const ROOT = path.resolve(path.dirname(__filename), "..");
const LABELS_PATH = path.join(ROOT, "test-data", "labels.json");
const IMAGES_DIR = path.join(ROOT, "test-data", "images");
const RESULTS_DIR = path.join(ROOT, "results");

function parseArgs(argv) {
  const args = { provider: "gemini", limit: Infinity, verbose: false };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--provider") args.provider = argv[++i];
    else if (a === "--limit") args.limit = parseInt(argv[++i], 10);
    else if (a === "--verbose") args.verbose = true;
    else if (a === "--help" || a === "-h") { printHelp(); process.exit(0); }
  }
  if (!["gemini", "anthropic", "both"].includes(args.provider)) {
    console.error(`Unknown provider: ${args.provider}`);
    process.exit(1);
  }
  return args;
}

function printHelp() {
  console.log(`budgetrak-ocr-harness
  --provider gemini|anthropic|both   (default: gemini)
  --limit N                          run first N test items only
  --verbose                          print each raw response
  --help                             this message`);
}

function mimeTypeFor(filename) {
  const ext = path.extname(filename).toLowerCase();
  if (ext === ".jpg" || ext === ".jpeg") return "image/jpeg";
  if (ext === ".png") return "image/png";
  if (ext === ".webp") return "image/webp";
  throw new Error(`Unsupported image extension: ${ext}`);
}

async function runProvider(providerName, extract, labels, prompt, args) {
  console.log(`\n━━━ ${providerName.toUpperCase()} (prompt ${PROMPT_VERSION}) ━━━`);
  const results = [];
  for (const label of labels) {
    const imagePath = path.join(IMAGES_DIR, label.file);
    if (!fs.existsSync(imagePath)) {
      console.log(`  [skip] ${label.file} — image not found`);
      results.push({ label, error: "missing image" });
      continue;
    }
    const imageBytes = fs.readFileSync(imagePath);
    const mimeType = mimeTypeFor(label.file);
    let response;
    try {
      response = await extract({ imageBytes, mimeType, prompt });
    } catch (e) {
      console.log(`  [error] ${label.file} — ${e.message}`);
      results.push({ label, error: e.message });
      continue;
    }
    if (!response.ok) {
      console.log(`  [fail] ${label.file} — ${response.error}`);
      results.push({ label, error: response.error, elapsedMs: response.elapsedMs });
      continue;
    }
    const grade = gradeResult(label, response.extracted);
    const marks = [
      grade.merchant.pass ? "✓" : "✗",
      grade.date.pass ? "✓" : "✗",
      grade.amount.pass ? "✓" : "✗",
      grade.category.skipped ? "·" : (grade.category.pass ? "✓" : "✗"),
    ].join("");
    console.log(
      `  ${marks} ${label.file.padEnd(32)} ` +
      `m=${response.extracted.merchant ?? "—"}  d=${response.extracted.date ?? "—"}  ` +
      `$=${response.extracted.amount ?? "—"}  ` +
      `(${response.elapsedMs}ms)`
    );
    if (args.verbose) {
      console.log("    expected:", JSON.stringify(label));
      console.log("    actual:  ", JSON.stringify(response.extracted));
    }
    results.push({
      label,
      extracted: response.extracted,
      grade,
      elapsedMs: response.elapsedMs,
      model: response.model,
    });
  }

  const summary = summarize(results);
  console.log(`\n  Summary  ${providerName}`);
  console.log(`    merchant  ${pct(summary.merchant.pass, summary.merchant.total)}  (${summary.merchant.pass}/${summary.merchant.total})`);
  console.log(`    date      ${pct(summary.date.pass, summary.date.total)}  (${summary.date.pass}/${summary.date.total})`);
  console.log(`    amount    ${pct(summary.amount.pass, summary.amount.total)}  (${summary.amount.pass}/${summary.amount.total})`);
  console.log(`    category  ${pct(summary.category.pass, summary.category.total)}  (${summary.category.pass}/${summary.category.total})`);
  if (summary.items.total > 0) {
    const avgJac = (summary.items.jaccardSum / summary.items.total);
    console.log(`    items     ${pct(summary.items.pass, summary.items.total)}  (${summary.items.pass}/${summary.items.total}, avg Jaccard ${avgJac.toFixed(3)})`);
  }
  const avgMs = summary.tests ? Math.round(summary.elapsedMsTotal / summary.tests) : 0;
  console.log(`    avg latency  ${avgMs}ms`);

  // Dump raw results for later diffing.
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const outFile = path.join(RESULTS_DIR, `${providerName}-${PROMPT_VERSION}-${stamp}.json`);
  fs.writeFileSync(outFile, JSON.stringify({ provider: providerName, promptVersion: PROMPT_VERSION, summary, results }, null, 2));
  console.log(`    saved → ${path.relative(ROOT, outFile)}`);

  return { summary, results };
}

async function main() {
  const args = parseArgs(process.argv);

  if (!fs.existsSync(LABELS_PATH)) {
    console.error(`labels file missing: ${LABELS_PATH}`);
    process.exit(1);
  }
  const allLabels = JSON.parse(fs.readFileSync(LABELS_PATH, "utf8"));
  const labels = allLabels.slice(0, args.limit);
  if (labels.length === 0) {
    console.error("No test items in labels.json. Add receipts to test-data/images/ and entries to labels.json.");
    process.exit(1);
  }
  console.log(`Loaded ${labels.length} test item(s) (of ${allLabels.length}) from labels.json`);

  const prompt = buildPrompt(TEST_CATEGORIES);

  if (args.provider === "gemini" || args.provider === "both") {
    if (!isGeminiConfigured()) {
      console.error("GEMINI_API_KEY not set — see .env.example");
      if (args.provider === "gemini") process.exit(1);
    } else {
      await runProvider("gemini", extractWithGemini, labels, prompt, args);
    }
  }
  if (args.provider === "anthropic" || args.provider === "both") {
    if (!isAnthropicConfigured()) {
      const msg = "ANTHROPIC_API_KEY not set — skipping Anthropic path.";
      if (args.provider === "anthropic") { console.error(msg); process.exit(1); }
      else console.log(`\n${msg}`);
    } else {
      await runProvider("anthropic", extractWithAnthropic, labels, prompt, args);
    }
  }
}

main().catch(e => {
  console.error("Fatal:", e.stack || e.message);
  process.exit(1);
});
