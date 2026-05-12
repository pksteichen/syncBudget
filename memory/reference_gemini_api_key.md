---
name: Gemini API key — current config + rotation requirements
description: How BudgeTrak's Gemini API key is locked down today, and what changes if it's ever rotated (post-2026 service-account binding requirement).
type: reference
---

# Gemini API key

## Current key (created 2026-04-14)

Project: `sync-23ce9` · Console name: **"Gemini API Key"** · Embedded in the release APK as `BuildConfig.GEMINI_API_KEY` (sourced from `local.properties.GEMINI_API_KEY`; never committed).

**Restrictions (applied 2026-05-12 before opening source to a third-party testing firm):**

- **Application restriction: Android apps.** Three SHA-1 entries registered (`com.techadvantage.budgetrak` + each cert):
  - Play App Signing cert
  - Upload key cert
  - Debug keystore cert (for local `assembleDebug` AI testing)
- **API restriction: "Gemini API" only** (single-API allowlist).

Google's API gateway validates `X-Android-Package` + `X-Android-Cert` headers on every call; an attacker who extracts the key from a decompiled APK can't reuse it from a re-signed APK because the cert SHA-1 won't match. Defense scope: key extraction → use elsewhere. Does NOT defend against "decompile BudgeTrak and use it as a prompt-injection proxy" — that's a Firebase AI Logic / rate-limit problem.

**Note on naming:** the Google Cloud Console rebranded "Generative Language API" to "Gemini API" sometime around 2025. The underlying endpoint is still `generativelanguage.googleapis.com`. If documentation references either name, they mean the same thing.

## CRITICAL — if you ever rotate this key or create a new one

A policy change rolled out by Google (visible as a banner on the API-restriction page): **new keys created for Vertex AI / Gemini APIs must be bound to a service account before the API restriction can be applied.** Existing keys (like ours, created 2026-04-14) are grandfathered.

If you rotate this key (security incident, compromise suspicion, or just hygiene rotation), you'll hit this requirement and need to:
1. **Bind the new key to a service account** via "Authenticate API calls through a service account" toggle on the key's edit page.
2. **Pick or create the SA.** Easiest path: reuse `play-publisher@sync-23ce9.iam.gserviceaccount.com` (already has multiple Play API roles) by granting it the Vertex AI / Gemini API role. Cleaner path: create a dedicated `gemini-client@sync-23ce9.iam.gserviceaccount.com` SA with only the minimum Gemini role.
3. **Then** apply the Android app + API restrictions exactly as the original key.

Rotation procedure:
1. Create the new key in Google Cloud Console → APIs & Services → Credentials → "+ Create credentials" → "API key".
2. Bind to SA (see above).
3. Apply Android + API restrictions matching the originals.
4. Update `~/dailyBudget/local.properties` with `GEMINI_API_KEY=<new>` (or update the CI secret `GEMINI_API_KEY` in the GitHub Actions environment if/when CI starts injecting it).
5. Ship a new release with the rotated key embedded.
6. Wait until the release is on Production and rolled out (~24h), then delete the old key from the console.

## How to apply

- When the user asks about the Gemini key: this file has the current state, restrictions, and rotation gotcha.
- When the user is rotating: walk them through the SA-binding step BEFORE applying API restriction (banner on the page enforces this order for new keys).
- When the user asks "is the embedded API key safe to ship in the AAB": yes — with the Android-cert restriction in place, leaked key material is unusable from anywhere except the actual signed BudgeTrak app on a non-rooted Android device with Play Services.
