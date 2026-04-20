#!/usr/bin/env node
// Simulate ReceiptManager.processAndSavePhoto's resize + iterative-quality
// compression so harness test images match what the Android app actually
// stores on disk. Mirrors the Kotlin algorithm verbatim:
//
//   1. Decode source.
//   2. If longestEdge > MAX_IMAGE_DIMENSION (1000), resize to maxDim=1000
//      with shortestEdge floor MIN_IMAGE_DIMENSION (400). Bilinear filter
//      (Android's Bitmap.createScaledBitmap(filter=true)).
//   3. Compute target bytes = area * 256KB/1M.
//   4. Iteratively compress: start q=92; if not in ±10% of target, try
//      either q=50 (when q=92 was already over) or q=98 (when under);
//      then up to 3 log-linear interpolation rounds between the closest
//      bracket, clamped to [20, 100].
//   5. Subsampling: Android's Bitmap.compress uses 4:4:4 at q≥90, 4:2:0
//      below (ImageMagick defaults to 4:2:0 regardless — force 1x1 for
//      high-q to match).
//
// Usage:
//   node scripts/simulate-app-compression.js <input> <output>
//   node scripts/simulate-app-compression.js --batch <dir-in> <dir-out>

import { execSync, execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const MAX_IMAGE_DIMENSION = 1000;
const MIN_IMAGE_DIMENSION = 400;
const TARGET_BYTES_PER_MP = 250 * 1024;  // 256000

// Android's Bitmap.compress subsampling heuristic (empirical):
// at q >= 90 uses 4:4:4 (no chroma subsampling), below uses 4:2:0.
function subsamplingFor(q) {
  return q >= 90 ? "1x1" : "2x2";
}

function getDimensions(imgPath) {
  const out = execFileSync("magick", ["identify", "-format", "%w %h", imgPath], { encoding: "utf8" }).trim();
  const [w, h] = out.split(/\s+/).map(Number);
  return { w, h };
}

// Port of resizeBitmap(bitmap, MAX_IMAGE_DIMENSION, MIN_IMAGE_DIMENSION).
// Returns the final (width, height) and whether a resize happened.
function computeResize(w, h) {
  const longest = Math.max(w, h);
  const shortest = Math.min(w, h);
  if (longest <= MAX_IMAGE_DIMENSION) return { w, h, resized: false, scale: 1 };
  const scaleFromLongest = MAX_IMAGE_DIMENSION / longest;
  let scale;
  const shortestAfter = shortest * scaleFromLongest;
  if (shortestAfter >= MIN_IMAGE_DIMENSION) {
    scale = scaleFromLongest;
  } else if (shortest >= MIN_IMAGE_DIMENSION) {
    scale = MIN_IMAGE_DIMENSION / shortest;
  } else {
    scale = 1;
  }
  if (scale >= 1) return { w, h, resized: false, scale: 1 };
  const newW = Math.max(1, Math.trunc(w * scale));
  const newH = Math.max(1, Math.trunc(h * scale));
  return { w: newW, h: newH, resized: true, scale };
}

// Produce JPEG bytes at a specific quality for the already-resized image at
// `srcPath`. Caches the resized PPM/PNG step to /tmp so we only resize once.
function compressAtQuality(resizedRawPath, q) {
  const tmpOut = `/tmp/sim_compress_q${q}_${Date.now()}.jpg`;
  execFileSync("magick", [
    resizedRawPath,
    "-quality", String(q),
    "-sampling-factor", subsamplingFor(q),
    // Android's Bitmap encoder doesn't write EXIF/XMP. Strip to match.
    "-strip",
    tmpOut,
  ]);
  const bytes = fs.readFileSync(tmpOut);
  fs.unlinkSync(tmpOut);
  return bytes;
}

// Port of compressToTargetSize — iterative quality bisection.
function compressToTargetSize(resizedRawPath, targetBytes) {
  const minTarget = Math.round(targetBytes * 0.9);
  const maxTarget = Math.round(targetBytes * 1.1);
  const samples = [];  // [[q, size], ...]
  let bestBytes = null;
  let bestDistance = Infinity;
  let bestQ = null;

  function tryQuality(q) {
    if (samples.some(s => s[0] === q)) return null;
    const bytes = compressAtQuality(resizedRawPath, q);
    const size = bytes.length;
    samples.push([q, size]);
    const dist = Math.abs(size - targetBytes);
    if (dist < bestDistance) { bestDistance = dist; bestBytes = bytes; bestQ = q; }
    return { q, size, inRange: size >= minTarget && size <= maxTarget };
  }

  const r1 = tryQuality(92);
  if (r1.inRange) return { bytes: bestBytes, q: bestQ, samples };
  const secondQ = samples[0][1] > targetBytes ? 50 : 98;
  const r2 = tryQuality(secondQ);
  if (r2.inRange) return { bytes: bestBytes, q: bestQ, samples };

  for (let round = 0; round < 3; round++) {
    const below = samples.filter(s => s[1] <= targetBytes).sort((a,b)=>b[1]-a[1])[0];
    const above = samples.filter(s => s[1] > targetBytes).sort((a,b)=>a[1]-b[1])[0];
    let predictedQ;
    if (below && above) {
      const lnT = Math.log(targetBytes);
      const lnL = Math.log(below[1]);
      const lnH = Math.log(above[1]);
      const d = lnH - lnL;
      predictedQ = d > 0.001
        ? Math.trunc(((lnT - lnL) / d) * (above[0] - below[0]) + below[0])
        : Math.trunc((below[0] + above[0]) / 2);
    } else {
      const s = [...samples].sort((a,b)=>a[0]-b[0]);
      predictedQ = Math.trunc(s[s.length-1][0] * (targetBytes / s[s.length-1][1]));
    }
    predictedQ = Math.max(20, Math.min(100, predictedQ));
    const r = tryQuality(predictedQ);
    if (r && r.inRange) return { bytes: bestBytes, q: bestQ, samples };
  }
  return { bytes: bestBytes, q: bestQ, samples };
}

// End-to-end: input image → device-equivalent JPEG bytes.
function processLikeApp(inputPath) {
  const { w, h } = getDimensions(inputPath);
  const { w: newW, h: newH, resized } = computeResize(w, h);

  // Stage 1: resize (if needed) to a pixel-perfect uncompressed intermediate
  // so each quality attempt compresses from the same pristine pixels.
  // Use bilinear filter (Triangle in IM) to match Android's Bitmap.createScaledBitmap(filter=true).
  const resizedRaw = `/tmp/sim_resized_${Date.now()}.png`;
  if (resized) {
    execFileSync("magick", [
      inputPath,
      "-filter", "Triangle",          // bilinear, matches Android
      "-resize", `${newW}x${newH}!`,  // force exact dims, no aspect fudge
      "-strip",
      resizedRaw,
    ]);
  } else {
    // Still decode to PNG to normalize input format.
    execFileSync("magick", [inputPath, "-strip", resizedRaw]);
  }

  // Stage 2: compress to target bytes.
  const area = newW * newH;
  const targetBytes = Math.trunc(area * TARGET_BYTES_PER_MP / 1_000_000);
  const result = compressToTargetSize(resizedRaw, targetBytes);
  fs.unlinkSync(resizedRaw);
  return {
    inputPath,
    sourceDims: { w, h },
    outputDims: { w: newW, h: newH },
    targetBytes,
    finalBytes: result.bytes.length,
    finalQuality: result.q,
    samples: result.samples,
    data: result.bytes,
  };
}

function main() {
  const args = process.argv.slice(2);
  if (args[0] === "--batch") {
    const inDir = args[1];
    const outDir = args[2];
    if (!inDir || !outDir) { console.error("Usage: --batch <inDir> <outDir>"); process.exit(2); }
    fs.mkdirSync(outDir, { recursive: true });
    const files = fs.readdirSync(inDir).filter(f => /\.(jpe?g|png|webp)$/i.test(f));
    console.log(`Processing ${files.length} files...\n`);
    for (const f of files) {
      const r = processLikeApp(path.join(inDir, f));
      fs.writeFileSync(path.join(outDir, f.replace(/\.(jpe?g|png|webp)$/i, ".jpg")), r.data);
      const sampStr = r.samples.map(([q, s]) => `q${q}=${(s/1024).toFixed(1)}KB`).join(" ");
      console.log(`${f.padEnd(36)} ${r.sourceDims.w}x${r.sourceDims.h} → ${r.outputDims.w}x${r.outputDims.h}  target=${(r.targetBytes/1024).toFixed(1)}KB final=${(r.finalBytes/1024).toFixed(1)}KB @q${r.finalQuality}  (${sampStr})`);
    }
    return;
  }
  const [inPath, outPath] = args;
  if (!inPath || !outPath) { console.error("Usage: <input> <output>   or   --batch <inDir> <outDir>"); process.exit(2); }
  const r = processLikeApp(inPath);
  fs.writeFileSync(outPath, r.data);
  console.log(`${inPath} → ${outPath}`);
  console.log(`  source: ${r.sourceDims.w}x${r.sourceDims.h}`);
  console.log(`  output: ${r.outputDims.w}x${r.outputDims.h}`);
  console.log(`  target: ${(r.targetBytes/1024).toFixed(1)}KB, final: ${(r.finalBytes/1024).toFixed(1)}KB @ q=${r.finalQuality}`);
  console.log(`  probes: ${r.samples.map(([q, s]) => `q${q}→${(s/1024).toFixed(1)}KB`).join(", ")}`);
}

main();
