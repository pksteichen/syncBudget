---
name: Google API "Android apps" key restrictions require X-Android-Package + X-Android-Cert headers
description: How to make an Android API-key restriction actually defend the key when the SDK or transport doesn't auto-attach the headers — and the debug-package gotcha.
type: feedback
---

## The rule

Google's API gateway validates Android-app key restrictions by checking `X-Android-Package` + `X-Android-Cert` headers on every call. **If the headers aren't sent, the gateway rejects with `403 API_KEY_ANDROID_APP_BLOCKED` and `androidPackage: <empty>` in the response body.** The restriction looks active in the Cloud Console but provides zero protection until the client actually sends the headers.

**Why:** the headers are how Google distinguishes "BudgeTrak on a real signed install" from "extracted key replayed from curl." No headers → no way to validate → blanket reject (once Google's gateway starts strictly enforcing the policy, which it did for our key sometime between 2026-05-12 and 2026-05-18).

## Auto-attach vs manual

| Library / transport | Attaches headers? |
|---|---|
| Firebase SDKs (initialized with `FirebaseApp.initializeApp(context)`) | Yes — via Firebase init context |
| Firebase AI Logic (Vertex AI for Firebase) | Yes — uses Firebase init context |
| Google Play Services SDKs | Yes — via Play Services context |
| **Standalone Google AI SDK (`com.google.ai.client.generativeai`)** | **No** — constructor only takes `modelName` + `apiKey`, no `Context` |
| Raw OkHttp / HttpURLConnection | No — you attach them yourself |

If you're using anything in the **No** column, you must attach them manually or the Android-app restriction is silently broken.

## How to attach manually

```kotlin
val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
val signature = info.signingInfo?.let {
    if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
}!![0]
val sha1Hex = MessageDigest.getInstance("SHA-1")
    .digest(signature.toByteArray())
    .joinToString("") { "%02X".format(it) }

request.header("X-Android-Package", context.packageName)
request.header("X-Android-Cert", sha1Hex)  // uppercase hex, no colons
```

Working implementation lives at `data/ocr/GeminiHttpClient.kt`. Cache the SHA-1 after first read — it's invariant per install.

## The `.debug` package gotcha

Our `app/build.gradle.kts` declares `applicationIdSuffix = ".debug"` for debug builds. The runtime package is `com.techadvantage.budgetrak.debug`, **not** `com.techadvantage.budgetrak`. When registering Android-app restrictions in the Cloud Console:

- Production entry: `com.techadvantage.budgetrak` + Play App Signing SHA-1
- Upload entry: `com.techadvantage.budgetrak` + Upload key SHA-1
- **Debug entry: `com.techadvantage.budgetrak.debug` + debug keystore SHA-1** ← use the `.debug` package

If you register the debug entry with the production package, debug builds get rejected with `androidPackage: com.techadvantage.budgetrak.debug` in the error body (the actual runtime package) — that's the diagnostic signal. Initial 2026-05-12 setup had this wrong; fixed 2026-05-18.

## How to apply

- When adding a new Google API key restriction: enumerate every package that will call the API (`applicationIdSuffix` adds suffixes; flavors can change the base id) and register each `(package, SHA-1)` pair separately.
- When debugging a `<empty>` androidPackage error: check whether your transport library auto-attaches the headers. If it doesn't, either switch to one that does (Firebase AI Logic) or attach them manually.
- When debugging a non-empty androidPackage but still-rejected error: check whether the package you registered matches what the build actually runs as (look at `applicationIdSuffix` and flavor config).
- When extracting a SHA-1: `keytool -list -v -keystore <path> -alias <alias> -storepass <pw>` outputs SHA1 with colons. Either format works in the Console form; the wire format `X-Android-Cert` is uppercase hex without colons.

## Related

- `reference_gemini_api_key.md` — current Gemini key restriction state + rotation procedure.
- `project_ocr_receipt_capture.md` — the OCR path that motivated the raw-HTTP migration.
