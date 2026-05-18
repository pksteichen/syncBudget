---
name: Don't let any path between raw JPEG bytes and Gemini re-encode the image
description: Quality-preserving image transport from ReceiptManager to Gemini — historical bitmap re-encode trap + how the current raw-HTTP path avoids it.
type: feedback
---

## The principle

BudgeTrak stores receipt JPEGs at q=92-98 (`ReceiptManager` uses iterative quality bisection targeting ~256 KB/MP). Any code path between the stored file and Gemini's wire format that calls `Bitmap.compress(..., 80, ...)` or similar will silently drop everything above q=80 — and the model's category accuracy is genuinely sensitive to this. Per our 2026-04-19 sweep on an Amazon brake-pads receipt, the model flipped from Transportation/Gas (correct at q≥90) to Home Supplies (wrong at q≤85) EXACTLY at the q=80 boundary.

## How the current path preserves quality

**File → bytes → Base64 → wire.** Since the raw-HTTP migration (2026-05-18), `ReceiptOcrService.extractFromReceipt` reads `file.readBytes()` and passes them through `GeminiHttpClient.generate(imageBytes = bytes)`, which Base64-encodes the raw bytes into the request body's `inline_data.data` field. No Bitmap decode happens anywhere in the OCR path. Don't add one.

## Historical context — the SDK trap (pre-2026-05-18)

We used to use `com.google.ai.client.generativeai:0.9.0`. The DSL had two image entry points:

```kotlin
content {
    image(bitmap)          // ← re-encodes bitmap at JPEG q=80!
    text(prompt)
}
```

That path called `com.google.ai.client.generativeai.internal.util.ConversionsKt.encodeBitmapToBase64Png` (misleadingly named — it actually emits JPEG) which hardcoded `bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)`. The workaround was to use `blob("image/jpeg", rawBytes)` instead, which bypassed the re-encode. The SDK is gone now (replaced for unrelated reasons — Android cert headers, see `reference_gemini_api_key.md`), but the underlying lesson stands: **any future SDK or library that takes a Bitmap is suspect.** Pass bytes, not Bitmaps.

## How to apply

- Don't decode the receipt file to a Bitmap in the OCR path — read raw bytes via `File.readBytes()` and pass through.
- Don't add a Bitmap import to `ReceiptOcrService` or `GeminiHttpClient` — there's no legitimate reason for them to need one.
- If you ever swap HTTP clients or transport libraries: verify there's no intermediate Bitmap re-encode step.
- If the model ever regresses to wrong categories on receipts with fine print or low-contrast tax lines, **check image quality first** — both the stored file's q value and the wire format. The harness at `tools/ocr-harness/` uses ImageMagick to encode at a fixed quality; mismatches between harness encoder and device encoder masked this bug for weeks before we caught it.
