---
name: AdMob WebView crashes on 16k-page emulator AVDs (TrichromeLibrary native bug)
description: On `sdk_gphone16k_x86_64` / `emu64xa16k` Android Studio emulator AVDs, `Fatal signal 5 SIGTRAP code 128 (SI_KERNEL) in tid <N> (MemoryInfra)` with all native frames inside `libmonochrome_64.so` (TrichromeLibrary) brings down the BudgeTrak process within ~60-90s of every launch. Emulator-stack-specific (TrichromeLibrary's bundled native code is incompatible with 16k page sizes); real devices don't reproduce. Loading is triggered by `MobileAds.initialize` regardless of whether any ad renders, so disabling ad *rendering* via subscription override is NOT enough — gate the initialize call itself.
type: feedback
originSessionId: 5682369f-ce9a-4adb-978b-3517ce099586
---
On `sdk_gphone16k_x86_64` / `emu64xa16k` Android Studio emulator AVDs (16k-page-size x86_64 image, Android 15), BudgeTrak crashes within ~60-90 seconds of every launch with this exact signature:

```
F libc: Fatal signal 5 (SIGTRAP), code 128 (SI_KERNEL), fault addr 0x0
       in tid <N> (MemoryInfra), pid <Y> (com.techadvantage.budgetrak[.debug])
F DEBUG: backtrace:
       #00-#14 pc ... TrichromeLibrary.apk!libmonochrome_64.so
       #15-#16 pc ... libc.so (__pthread_start + __start_thread)
```

**Diagnosis:** all 14 native frames are inside `libmonochrome_64.so` (Android System WebView / Chrome's native lib), thread name `MemoryInfra` (Chromium's memory-tracing infrastructure thread). Emulator-stack bug — TrichromeLibrary's bundled native code is incompatible with the experimental 16k-page-size emulator config. Companion logcat warning `Unexpected CPU variant for x86: x86_64. Known variants: atom, sandybridge, silvermont, goldmont, ...` confirms the mismatch. Real devices (real foldables, real tablets, real phones) don't reproduce.

**Why disabling ad rendering wasn't enough (sub-fix discovered 2026-05-16):** the subscription override sets `nativeAdEnabled = false` so no `AndroidView` for the ad slot ever inflates — but `MobileAds.initialize(this)` is still called unconditionally in `BudgeTrakApplication.onCreate`. The SDK init alone is enough to load TrichromeLibrary in-process and spawn the `MemoryInfra` thread. Crash still happens, just less frequently.

**Real fix (landed dev 2026-05-16):** gate `MobileAds.initialize` on entitlement state read synchronously from SharedPreferences in `BudgeTrakApplication.onCreate`:

```kotlin
val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
val isPaidUser = prefs.getBoolean("isPaidUser", false)
val isSubscriber = prefs.getBoolean("isSubscriber", false)
if (!isPaidUser && !isSubscriber) {
    com.google.android.gms.ads.MobileAds.initialize(this) {}
}
```

With entitlement true at app start, no MobileAds, no WebView, no `libmonochrome_64.so`, no crash. Secondary benefit on real devices: paid users skip the ~5 MB SDK init + WebView eager-load entirely.

**Workarounds if you can't gate init** (e.g., testing the ad path itself on a foldable):
- Use a non-16k AVD: regular `emu64xa` system image (4k pages) instead of `emu64xa16k`.
- Update Android System WebView on the emulator via Play Store (hit-or-miss; sometimes a newer build ships fixed `libmonochrome` for 16k).
- Test ads on a physical device instead.

**Diagnostic recipe:** `adb logcat -b crash -d` (the dedicated crash buffer survives process death). Grep for `FATAL EXCEPTION` or `Fatal signal`. If the backtrace is all `libmonochrome_64.so` in `MemoryInfra`, you've hit this. The standard `main` logcat buffer typically doesn't capture the native SIGTRAP — the crash happens off the main thread and the tombstone goes to the `crash` buffer + `/data/tombstones/`.
