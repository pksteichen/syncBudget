---
name: AdMob + Firebase Analytics share AD_SERVICES_CONFIG — merger fails without override
description: When integrating play-services-ads alongside firebase-analytics, the build fails with "Manifest merger failed: Attribute property#android.adservices.AD_SERVICES_CONFIG@resource value=(@xml/gma_ad_services_config) ... is also present at [com.google.android.gms:play-services-measurement-api]". Fix is a tools:replace override.
type: feedback
---

**Symptom.** Adding `com.google.android.gms:play-services-ads:23.6.0` while Firebase Analytics is in the build fails Gradle's `:app:processDebugMainManifest` task with:

```
Manifest merger failed : Attribute property#android.adservices.AD_SERVICES_CONFIG@resource
value=(@xml/gma_ad_services_config) from [com.google.android.gms:play-services-ads-lite:23.6.0]
AndroidManifest.xml is also present at [com.google.android.gms:play-services-measurement-api:21.5.0]
AndroidManifest.xml value=(@xml/ga_ad_services_config).
Suggestion: add 'tools:replace="android:resource"' to <property> element at AndroidManifest.xml.
```

**Why:** both libraries declare the Android Privacy Sandbox `AD_SERVICES_CONFIG` property pointing to different config XMLs. The merger refuses to silently pick one — the host manifest must explicitly resolve.

**Fix:**

1. Add the `tools` namespace on `<manifest>`:
   ```xml
   <manifest xmlns:android="http://schemas.android.com/apk/res/android"
       xmlns:tools="http://schemas.android.com/tools">
   ```

2. Inside `<application>`, declare the property explicitly with `tools:replace`:
   ```xml
   <property
       android:name="android.adservices.AD_SERVICES_CONFIG"
       android:resource="@xml/gma_ad_services_config"
       tools:replace="android:resource" />
   ```

   Use `gma_ad_services_config` (AdMob's) since this is an ad-serving app. The Firebase Analytics measurement config (`ga_ad_services_config`) is a subset for attribution-only flows; AdMob's is broader and Google's docs say either is acceptable as long as one is chosen explicitly.

**How to apply:** any time we add another Google library that touches `AD_SERVICES_CONFIG` (e.g., a future Play Billing SDK that adds attribution, or a Privacy Sandbox lib upgrade), expect this conflict to resurface — same fix, may need to update which `@xml/*_ad_services_config` we resolve to. Keep the comment block in `AndroidManifest.xml` so future-me sees the rationale.

**Verified:** v2.10.09 build (2026-05-08) on AGP 8.7.3 + play-services-ads 23.6.0 + firebase-bom 32.7.0.
