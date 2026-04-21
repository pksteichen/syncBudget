---
name: Compression long-edge cap (TODO)
description: Cap long edge at 3072px in ReceiptManager/harness prep-images-app-style compression; narrow-tall receipts currently grow unbounded due to MIN_DIM=400 shortest-edge floor.
type: project
originSessionId: 1ebb1f67-d429-406a-b518-08881dc22bb6
---
Cap long edge at 3072px in the OCR image-compression pipeline (both the Node harness prep script and the app's `ReceiptManager`).

**Why:** current pipeline is MAX_DIM=1000, MIN_DIM=400 (shortest-edge floor). For narrow-tall receipts (e.g. Target `target_long_1.jpg` = 400×2883, `target_long_2.jpg` = 400×2596), keeping the short edge at 400 lets the long edge grow unbounded — preserves OCR legibility but has no ceiling. Gemini accepts up to ~3072px per edge. Beyond that the SDK may tile/resample in ways we don't control, and it wastes bandwidth/tokens without OCR benefit.

**How to apply:** when implementing, add long-edge clamp to both:
  - `tools/ocr-harness/scripts/prep-images-app-style.js` (harness re-encode)
  - `ReceiptManager` compression path in the app (`fix: bypass Gemini SDK's q=80 Bitmap re-encode in OCR path` commit fd24911 changed this area — same file)
If long edge > 3072, scale both dims down proportionally; short edge may drop below 400 in that case, which is the correct tradeoff.

Raised 2026-04-20 during OCR test-bank labeling, when Claude hit its own 2000px viewing limit on the same receipts. Not urgent — flagged as "later."
