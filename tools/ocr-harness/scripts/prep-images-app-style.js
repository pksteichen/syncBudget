#!/usr/bin/env node
// Preprocess test images the same way the Android app does before sending
// to Gemini OCR, so offline harness results reflect production quality.
//
// App pipeline (app/src/main/java/com/techadvantage/budgetrak/data/sync/ReceiptManager.kt):
//   1. Resize so longest edge ≤ 1000px, BUT keep shortest edge ≥ 400px
//      (prevents tall e-receipt screenshots from being crushed into unreadable
//      slivers — 1080x7785 would become 139x1000 without the floor).
//   2. JPEG compress to target bytes = pixelArea * 250KB / 1M pixels (±10%)
//
// Usage: node scripts/prep-images-app-style.js <src-dir> <dst-dir>

import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const MAX_DIM = 1000;
const MIN_DIM = 400;  // short-edge floor; keep in sync with ReceiptManager.MIN_IMAGE_DIMENSION
const LONG_EDGE_HARD_CAP = 3072;  // Gemini accepts up to ~3072px/edge; above that the SDK tiles/resamples
const TARGET_BYTES_PER_MP = 250 * 1024;

// Replicates ReceiptManager.resizeBitmap (Kotlin) exactly.
// Returns { w, h } after the same scaling logic the app applies.
function computeTargetDims(w, h) {
  const longestEdge = Math.max(w, h);
  const shortestEdge = Math.min(w, h);
  if (longestEdge <= MAX_DIM) return { w, h };  // no resize needed

  const scaleFromLongest = MAX_DIM / longestEdge;
  let scale;
  const shortestAfter = shortestEdge * scaleFromLongest;
  if (shortestAfter >= MIN_DIM) {
    scale = scaleFromLongest;
  } else if (shortestEdge >= MIN_DIM) {
    scale = MIN_DIM / shortestEdge;
  } else {
    scale = 1;  // source shortest already below floor; never upscale
  }
  // Hard cap on longest edge: if the min-dim floor would let long edge
  // exceed LONG_EDGE_HARD_CAP, tighten scale even though it pushes the
  // short edge below MIN_DIM. Correct tradeoff for very-tall receipts.
  const longestAfter = longestEdge * scale;
  if (longestAfter > LONG_EDGE_HARD_CAP) {
    scale = LONG_EDGE_HARD_CAP / longestEdge;
  }
  if (scale >= 1) return { w, h };
  return {
    w: Math.max(1, Math.round(w * scale)),
    h: Math.max(1, Math.round(h * scale)),
  };
}

const [, , srcDir, dstDir] = process.argv;
if (!srcDir || !dstDir) { console.error("Usage: prep-images-app-style.js <src> <dst>"); process.exit(1); }
fs.mkdirSync(dstDir, { recursive: true });

const EXTS = new Set([".jpg", ".jpeg", ".png", ".webp"]);
const files = fs.readdirSync(srcDir).filter(f => EXTS.has(path.extname(f).toLowerCase()));

const identify = (file) => {
  const out = execFileSync("magick", ["identify", "-format", "%w %h", file], { encoding: "utf8" });
  const [w, h] = out.trim().split(" ").map(Number);
  return { w, h };
};

let processed = 0, skipped = 0;
for (const f of files) {
  const src = path.join(srcDir, f);
  const outName = f.replace(/\.(jpe?g|png|webp)$/i, ".jpg");
  const dst = path.join(dstDir, outName);

  try {
    const { w, h } = identify(src);
    const { w: newW, h: newH } = computeTargetDims(w, h);
    const targetBytes = Math.round((newW * newH * TARGET_BYTES_PER_MP) / 1_000_000);
    const targetKB = Math.max(10, Math.round(targetBytes / 1024));

    // Use exact pixel dims with `!` (force) so ImageMagick matches the app's
    // floor behaviour. `1000x1000>` alone would ignore the short-edge floor.
    execFileSync("magick", [
      src,
      "-resize", `${newW}x${newH}!`,
      "-filter", "Triangle",
      "-define", `jpeg:extent=${targetKB}kb`,
      "-sampling-factor", "4:2:0",
      dst,
    ]);

    const after = identify(dst);
    const actualBytes = fs.statSync(dst).size;
    console.log(
      `${outName.padEnd(45)} ${w}x${h} → ${after.w}x${after.h}  ${(actualBytes/1024).toFixed(0)}KB (target ${targetKB}KB)`
    );
    processed++;
  } catch (e) {
    console.error(`SKIP ${f}: ${e.message}`);
    skipped++;
  }
}

console.log(`\nProcessed ${processed} | Skipped ${skipped} | Max edge ${MAX_DIM}px | Min edge ${MIN_DIM}px | JPEG target ${(TARGET_BYTES_PER_MP/1024).toFixed(0)}KB/MP`);
