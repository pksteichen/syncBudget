#!/usr/bin/env node
// DuckDuckGo image-search scraper for receipt candidates. For INTERNAL
// OCR test bank only — downloaded images are gitignored (test-data/images/
// is in .gitignore), never redistributed.
//
// Usage:
//   node scripts/scrape-ddg-receipts.js "cvs pharmacy receipt" --prefix cvs_ --count 8
//   node scripts/scrape-ddg-receipts.js --batch
//
// Pipeline:
//   1. GET duckduckgo.com/?q=<query>&iax=images&ia=images  → extract vqd token
//   2. GET duckduckgo.com/i.js?q=<query>&vqd=<token>       → JSON results
//   3. Filter out stock-photo / generator / social-media sites.
//   4. Download up to --count results, skipping duplicates by URL hash.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import crypto from "node:crypto";

const __filename = fileURLToPath(import.meta.url);
const ROOT = path.resolve(path.dirname(__filename), "..");
const OUT_DIR = path.join(ROOT, "test-data", "ddg_scrape");

const UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

// Host patterns we want to SKIP — stock photos, generator templates, and
// any domain where images are usually fake/watermarked/unusable.
const BLOCK_HOST_RE = /(shutterstock|dreamstime|alamy|istockphoto|gettyimages|123rf|depositphotos|adobestock|stock\.adobe|can?stockphoto|pexels|unsplash|pinimg|pinterest|expressexpense|makereceipt|makemyreceipt|receipttemplate|receipts\.app|templatelab|wordtemplatesonline|receiptmakerly|reciept|fakereceipt|fake-receipt|generator|template)/i;

async function getVqd(query) {
  const url = `https://duckduckgo.com/?q=${encodeURIComponent(query)}&iax=images&ia=images`;
  const res = await fetch(url, { headers: { "User-Agent": UA } });
  const html = await res.text();
  const m = html.match(/vqd=["&]([0-9a-z-]+)/i);
  if (!m) throw new Error(`No vqd token for query "${query}"`);
  return m[1];
}

async function searchImages(query, vqd) {
  const url = `https://duckduckgo.com/i.js?l=us-en&o=json&q=${encodeURIComponent(query)}&vqd=${vqd}&f=,,,,,&p=1`;
  const res = await fetch(url, { headers: { "User-Agent": UA, "Referer": "https://duckduckgo.com/" } });
  const json = await res.json();
  return json.results || [];
}

function isUsable(result) {
  const imageUrl = result.image || "";
  const sourceHost = (result.source || "").toLowerCase();
  if (BLOCK_HOST_RE.test(imageUrl)) return false;
  if (BLOCK_HOST_RE.test(sourceHost)) return false;
  // Receipts are typically portrait-oriented and > 400 on long edge.
  const longest = Math.max(result.height || 0, result.width || 0);
  if (longest < 400) return false;
  // Filter gigantic images — usually stock templates saved huge.
  if (longest > 4000) return false;
  return true;
}

async function downloadOne(url, outPath) {
  const res = await fetch(url, {
    headers: { "User-Agent": UA, "Referer": "https://duckduckgo.com/" },
    redirect: "follow",
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const buf = Buffer.from(await res.arrayBuffer());
  // Reject tiny files (likely error pages, 1x1 pixels, etc.)
  if (buf.length < 3000) throw new Error(`too small: ${buf.length}B`);
  // Verify JPEG/PNG magic.
  const isJpeg = buf[0] === 0xff && buf[1] === 0xd8;
  const isPng = buf.slice(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
  const isWebp = buf.slice(0, 4).toString() === "RIFF" && buf.slice(8, 12).toString() === "WEBP";
  if (!isJpeg && !isPng && !isWebp) throw new Error("not image/jpeg|png|webp");
  fs.writeFileSync(outPath, buf);
  return buf.length;
}

async function runQuery(query, prefix, count) {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const seen = new Set();
  console.log(`\n▶ "${query}"  (prefix=${prefix}, target=${count})`);

  const vqd = await getVqd(query);
  await new Promise(r => setTimeout(r, 400));  // gentle rate limiting
  const results = await searchImages(query, vqd);
  const usable = results.filter(isUsable);
  console.log(`  ${results.length} total, ${usable.length} pass filter`);

  let downloaded = 0;
  let i = 0;
  for (const r of usable) {
    if (downloaded >= count) break;
    const urlHash = crypto.createHash("md5").update(r.image).digest("hex").slice(0, 8);
    if (seen.has(urlHash)) continue;
    seen.add(urlHash);
    const ext = (r.image.match(/\.(jpe?g|png|webp)(\?|$)/i)?.[1] || "jpg").toLowerCase();
    const fname = `${prefix}${String(i + 1).padStart(2, "0")}_${urlHash}.${ext === "jpeg" ? "jpg" : ext}`;
    const outPath = path.join(OUT_DIR, fname);
    try {
      const size = await downloadOne(r.image, outPath);
      downloaded++;
      console.log(`    ✓ ${fname} (${(size / 1024).toFixed(1)}KB, ${r.width}×${r.height}, src=${r.source})`);
    } catch (e) {
      console.log(`    ✗ skip (${e.message.slice(0, 50)}) ← ${r.image.slice(0, 70)}`);
    }
    i++;
    await new Promise(r => setTimeout(r, 250));  // gentle rate limit between downloads
  }
  console.log(`  downloaded ${downloaded}/${count}`);
  return downloaded;
}

// Batch mode: domain-targeted queries for the missing categories
const BATCH_QUERIES = [
  { q: "costco gas receipt", prefix: "costco_gas_", count: 3 },
  { q: "auto repair shop receipt invoice", prefix: "auto_repair_", count: 4 },
  { q: "cvs pharmacy receipt", prefix: "cvs_", count: 4 },
  { q: "walgreens receipt", prefix: "walgreens_", count: 4 },
  { q: "home depot receipt", prefix: "home_depot_", count: 4 },
  { q: "lowes receipt", prefix: "lowes_", count: 3 },
  { q: "best buy electronics receipt", prefix: "best_buy_", count: 4 },
  { q: "apple store receipt", prefix: "apple_store_", count: 3 },
  { q: "macys clothing store receipt", prefix: "macys_", count: 3 },
  { q: "kohls receipt", prefix: "kohls_", count: 3 },
  { q: "old navy receipt", prefix: "old_navy_", count: 3 },
  { q: "movie theater ticket receipt amc", prefix: "amc_movie_", count: 3 },
  { q: "regal cinemas receipt", prefix: "regal_", count: 2 },
  { q: "electric utility bill receipt", prefix: "electric_bill_", count: 3 },
  { q: "gas utility bill receipt", prefix: "gas_bill_", count: 2 },
  { q: "verizon phone bill receipt", prefix: "verizon_bill_", count: 2 },
  { q: "petsmart receipt", prefix: "petsmart_", count: 3 },
  { q: "toys r us receipt", prefix: "toys_", count: 2 },
  { q: "target receipt", prefix: "target_", count: 3 },
  { q: "starbucks receipt", prefix: "starbucks_", count: 3 },
  { q: "hair salon receipt", prefix: "hair_salon_", count: 2 },
  { q: "uber receipt", prefix: "uber_", count: 3 },
  { q: "hotel receipt", prefix: "hotel_", count: 3 },
];

async function main() {
  const args = process.argv.slice(2);
  if (args[0] === "--batch") {
    let total = 0;
    for (const { q, prefix, count } of BATCH_QUERIES) {
      try {
        total += await runQuery(q, prefix, count);
      } catch (e) {
        console.log(`  ✗ query "${q}" failed: ${e.message}`);
      }
      await new Promise(r => setTimeout(r, 800));  // gentle between queries
    }
    console.log(`\n═══ TOTAL DOWNLOADED: ${total} ═══`);
    return;
  }

  const query = args[0];
  const prefix = args[args.indexOf("--prefix") + 1] || "ddg_";
  const count = parseInt(args[args.indexOf("--count") + 1] || "5", 10);
  if (!query || args[0].startsWith("--")) {
    console.error("Usage: scrape-ddg-receipts.js \"query\" [--prefix p_] [--count N]");
    console.error("   or: scrape-ddg-receipts.js --batch");
    process.exit(2);
  }
  await runQuery(query, prefix, count);
}

main().catch(e => { console.error(e); process.exit(1); });
