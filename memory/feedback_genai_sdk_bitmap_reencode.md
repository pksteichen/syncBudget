---
name: Google GenerativeAI Android SDK re-encodes every Bitmap at JPEG q=80
description: Hidden gotcha in com.google.ai.client.generativeai v0.9.0 — use blob() not image() when you care about image quality going to Gemini
type: feedback
originSessionId: a00b436a-3ced-4e78-a40e-780a8f5acff8
---
When an Android client sends an image to Gemini via the Google GenerativeAI
SDK's DSL:

```kotlin
model.generateContent(content {
    image(bitmap)          // ← re-encodes bitmap at JPEG q=80!
    text(prompt)
})
```

…the SDK calls `com.google.ai.client.generativeai.internal.util.ConversionsKt.
encodeBitmapToBase64Png` (misleadingly named — it actually emits JPEG), which
hardcodes quality=80:

```
bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
```

**Why this matters:** any image quality you put into the Bitmap above q=80 is
silently discarded. For BudgeTrak, ReceiptManager stores receipts at q=92-98
(targeting 256KB/MP with iterative quality bisection). The SDK re-encode
dropped those to q=80 before sending. Per our own quality sweep on the Amazon
brake-pads receipt, the model flipped from Transportation/Gas (correct at
q≥90) to Home Supplies (wrong at q≤85) — EXACTLY at the boundary the SDK's
re-encode hits.

**Fix:** use `blob(...)` instead of `image(...)`:

```kotlin
model.generateContent(content {
    blob("image/jpeg", rawJpegBytes)   // raw bytes → Base64 → wire, no re-encode
    text(prompt)
})
```

`blob(...)` compiles to `Content.Builder.addBlob(String, byte[])` which builds
a `BlobPart` that gets Base64-encoded directly without touching any image
pipeline. Verified via decompiling the SDK's `ConversionsKt` and inspecting
our own compiled class file (the invokevirtual target is `addBlob`, not
`addImage`).

**How to apply:**
- Read the receipt file as raw bytes: `File.readBytes()`.
- Pass via `content { blob("image/jpeg", bytes); text(prompt) }`.
- Don't decode to Bitmap first (and don't even import android.graphics.Bitmap
  from the OCR service — it would be pointless overhead).
- This applies to ALL Gemini OCR paths in BudgeTrak: `ReceiptOcrService`
  Call 1, Call 2, Call 3. Already updated; don't regress.

**Cross-reference:** `project_ocr_pipeline_decisions.md` Phase-2 iteration
notes. This gotcha was the root cause of the 2026-04-19 device-vs-harness
variance on Amazon receipts.

**If the SDK ships a newer version that exposes a quality parameter for
image(bitmap):** ignore it — `blob()` is simpler and avoids any per-version
drift. Only reason to revisit is if blob() ever stops passing bytes through
without re-encoding.
