---
name: Gemini API key — current config + rotation requirements
description: How BudgeTrak's Gemini API key is locked down today (raw-HTTP path with manual header attach), and what changes if rotated.
type: reference
---

# Gemini API key

## Current key (created 2026-04-14)

Project: `sync-23ce9` · Console name: **"Gemini API Key"** · Embedded in the release APK as `BuildConfig.GEMINI_API_KEY` (sourced from `local.properties.GEMINI_API_KEY`; never committed).

**Restrictions (current — re-applied 2026-05-18 after raw-HTTP migration):**

- **Application restriction: Android apps.** Three `package + SHA-1` entries:
  - `com.techadvantage.budgetrak` + Play App Signing cert (production installs from Play)
  - `com.techadvantage.budgetrak` + Upload key cert (kept for completeness; Play re-signs AABs with #1 before delivery, so this entry isn't strictly load-bearing at runtime)
  - **`com.techadvantage.budgetrak.debug`** + debug keystore cert (sideloaded debug APKs)
- **API restriction: "Gemini API" only** (single-API allowlist).

**Debug package suffix is critical.** Debug builds run as `com.techadvantage.budgetrak.debug` (per `applicationIdSuffix = ".debug"` in `app/build.gradle.kts`). The cert restriction is package+SHA-1 pair-matched, so the debug entry MUST use the `.debug` package — not the production package. Initial 2026-05-12 setup used the wrong package for the debug entry; symptom was `API_KEY_ANDROID_APP_BLOCKED` with `androidPackage: com.techadvantage.budgetrak.debug` in the error body. Fixed 2026-05-18.

## Critical context: standalone SDK does NOT send these headers

Until 2026-05-18 OCR + AI categorization used `com.google.ai.client.generativeai:0.9.0` (standalone Google AI SDK). **That SDK does not attach `X-Android-Package` / `X-Android-Cert` headers** — it has no `Context` to derive them from. Result: the Android-app restriction silently rejected every call once Google's gateway started strictly enforcing the policy (symptom: `"Requests from this Android client application <empty> are blocked"`).

**Fix (shipped 2026-05-18):** removed the SDK entirely; OCR + AI categorization now use raw OkHttp calls to `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=…`. The new `data/ocr/GeminiHttpClient.kt` reads the app's own signing-cert SHA-1 via `PackageManager.getPackageInfo(GET_SIGNING_CERTIFICATES)`, formats it uppercase-hex (no colons), and attaches both headers on every request. This is what makes the cert restriction actually defend the key.

If you ever switch back to the SDK or to Firebase AI Logic, **the headers will need a new attach path** (Firebase AI Logic auto-attaches via Firebase init context, but pulls Firebase BOM 33+ — see `project_ocr_receipt_capture.md`).

## Defense scope

Google's API gateway validates `X-Android-Package` + `X-Android-Cert` headers on every call. A re-signed APK has a different cert SHA-1 and the gateway rejects with `403 API_KEY_ANDROID_APP_BLOCKED`. **Defends against:** key extraction → use elsewhere. **Does NOT defend against:** decompile-and-repackage-as-proxy on a real signed install (use the actual app to send arbitrary prompts) — that's a per-user rate-limit / Firebase AI Logic problem.

**Note on naming:** Google Cloud Console rebranded "Generative Language API" to "Gemini API" sometime around 2025. The underlying endpoint is still `generativelanguage.googleapis.com`.

## CRITICAL — if you ever rotate this key or create a new one

A policy change rolled out by Google (visible as a banner on the API-restriction page): **new keys created for Vertex AI / Gemini APIs must be bound to a service account before the API restriction can be applied.** Existing keys (like ours, created 2026-04-14) are grandfathered.

If you rotate this key (security incident, compromise suspicion, or just hygiene rotation), you'll hit this requirement and need to:
1. **Bind the new key to a service account** via "Authenticate API calls through a service account" toggle on the key's edit page.
2. **Pick or create the SA.** Easiest path: reuse `play-publisher@sync-23ce9.iam.gserviceaccount.com` (already has multiple Play API roles) by granting it the Vertex AI / Gemini API role. Cleaner path: create a dedicated `gemini-client@sync-23ce9.iam.gserviceaccount.com` SA with only the minimum Gemini role.
3. **Then** apply the Android app + API restrictions exactly as the original key (remember the `.debug` package for the debug-keystore entry).

Rotation procedure:
1. Create the new key in Google Cloud Console → APIs & Services → Credentials → "+ Create credentials" → "API key".
2. Bind to SA (see above).
3. Apply Android + API restrictions matching the originals (including the `.debug` package quirk).
4. Update `~/dailyBudget/local.properties` with `GEMINI_API_KEY=<new>` (or update the CI secret `GEMINI_API_KEY` in the GitHub Actions environment if/when CI starts injecting it).
5. Ship a new release with the rotated key embedded.
6. Wait until the release is on Production and rolled out (~24h), then delete the old key from the console.

## Where to find each SHA-1

- **Play App Signing cert:** Play Console → Test and release → Setup → App integrity → App signing → "App signing key certificate" SHA-1.
- **Upload key cert:** same page, "Upload key certificate" SHA-1.
- **Debug keystore cert:** local — `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1`. As of 2026-05-18 the debug SHA-1 on Paul's Termux build host is `FB:09:50:11:F7:88:00:3A:C8:A1:96:2D:EA:59:C2:4D:D5:A3:39:2C` — re-extract if you change keystores.

## How to apply

- When the user asks about the Gemini key: this file has the current state, restrictions, and rotation gotcha.
- When the user is rotating: walk them through the SA-binding step BEFORE applying API restriction (banner on the page enforces this order for new keys).
- When the user asks "is the embedded API key safe to ship in the AAB": yes — with the Android-cert restriction in place AND the raw-HTTP header attach in `GeminiHttpClient`, leaked key material is unusable from anywhere except the actual signed BudgeTrak app on a real Android device.
- When debugging `API_KEY_ANDROID_APP_BLOCKED` errors: check the `androidPackage` field in the response body. If it ends in `.debug` and you registered the production package, that's the bug.
