---
name: Play Console Data Safety form answers
description: Per-data-type answers submitted on 2026-05-05, plus the change triggers that would require revisiting them. Audit this any time data flows, third parties, or user toggles change.
type: project
originSessionId: 911fd8f0-4c5a-4a6d-a592-a304d4d2704a
---
# Play Console Data Safety form (submitted 2026-05-05)

## Pre-survey gate questions

| Question | Answer |
|---|---|
| Data encrypted in transit? | **Yes** (HTTPS/TLS for all Firebase + Gemini + AdMob; ChaCha20-Poly1305 on top for SYNC) |
| Users can request deletion? | **Yes** — in-app Dissolve/Leave + uninstall + 90-day TTL. Privacy policy `#data-deletion` is the canonical reference. Email path was dropped 2026-05-05. |
| Independent MASVS L1 review? | **No** (would require paid third-party audit) |

## Seven declared data types

### 1. Personal info → User IDs
- **Collected:** Yes · **Shared:** No (Google services = service providers, exempt)
- **Ephemeral:** No (Auth UID persistent; user_pseudo_id sessionizes BigQuery export)
- **Required:** No — Optional (Auth UID requires user to enable SYNC; user_pseudo_id requires diagnostic-data toggle on)
- **Purposes — Collected:** App functionality, Analytics
- **What it covers:** Firebase anonymous Auth UID (when SYNC enabled); Firebase Analytics `user_pseudo_id` (when diagnostic toggle on)
- **Why not Account management:** Play's examples assume traditional account UX (signup, login, profile); BudgeTrak doesn't expose any. Anonymous Auth is an implementation detail.

### 2. Financial info → Purchase history
- **Collected:** Yes · **Shared:** No (Gemini = Google service provider)
- **Ephemeral:** No (Google's 24h abuse-review cache fails the strict "in-memory, real-time only" bar)
- **Required:** No — Optional (`aiCsvCategorizeEnabled` Settings toggle, default off, Paid+Sub gated)
- **Purposes — Collected:** App functionality
- **What it covers:** merchant + amount + date sent to Gemini for AI CSV categorization
- **Not declared here:** SYNC-encrypted transactions (E2EE-exempt per Play's exemption #1)
- **Why not Other financial info:** the only non-E2EE financial flow is the AI CSV merchant/amount/date, which is literally purchase history; Other financial info would be over-disclosure

### 3. Photos and videos → Photos
- **Collected:** Yes · **Shared:** No (Gemini = service provider)
- **Ephemeral:** No (same Google 24h cache reasoning as Purchase history)
- **Required:** No — Optional (Subscriber-only, requires explicit sparkle-icon tap per receipt)
- **Purposes — Collected:** App functionality
- **What it covers:** receipt photos sent to Gemini for OCR
- **Not declared here:** SYNC-encrypted receipt photos in Cloud Storage (E2EE-exempt)

### 4. App info and performance → Crash logs
- **Collected:** Yes · **Shared:** No (Crashlytics = service provider)
- **Ephemeral:** No (90-day Crashlytics retention; queried via BigQuery weeks later)
- **Required:** No — Optional (`crashlyticsEnabled` toggle: "Send crash reports and anonymous usage data")
- **Purposes — Collected:** Analytics
- **What it covers:** Firebase Crashlytics stack traces, anonymous device info, build identity custom keys
- **Why not App functionality:** crash logs aren't read at runtime to enable features; collected for post-hoc debugging only

### 5. App info and performance → Diagnostics
- **Collected:** Yes · **Shared:** No (Crashlytics + Analytics = service providers)
- **Ephemeral:** No
- **Required:** No — Optional (same `crashlyticsEnabled` toggle as Crash logs)
- **Purposes — Collected:** Analytics
- **What it covers:** Crashlytics custom keys (`cashDigest` hash, `txnCount`, `listenerStatus`, etc.); Analytics events `health_beacon` (daily heartbeat) and `ocr_feedback` (OCR correction deltas, booleans only)

### 6. App activity → App interactions
- **Collected:** Yes · **Shared:** No (Analytics = service provider)
- **Ephemeral:** No (BigQuery export is persistent)
- **Required:** No — Optional (same toggle)
- **Purposes — Collected:** Analytics
- **What it covers:** Firebase Analytics auto-collected events: `session_start`, `first_open`, `screen_view`, `app_update`, `app_remove`, `os_update`

### 7. Device or other IDs → Device or other IDs
- **Collected:** Yes · **Shared:** Yes (AdMob ad serving crosses the third-party line — even though Google is also your service provider for FCM/FID)
- **Ephemeral:** No
- **Required:** **Yes** (free-tier ad ID has no in-app opt-out; "pay to upgrade" doesn't count as user-controllable per Play guidance)
- **Purposes — Collected:** App functionality, Advertising or marketing, Fraud prevention/security/compliance
- **Purpose — Shared:** Advertising or marketing
- **What it covers:**
  - AdMob advertising ID (free tier ad serving) — drives the Shared tick
  - FCM registration token (peer-wake when SYNC enabled) — App functionality
  - Firebase Installation ID (Play Integrity / App Check attestation) — Fraud prevention/security/compliance

## What was deliberately NOT declared (and why)

- **Personal info → Name/Email/Phone/Address** — never collected.
- **Financial info → User payment info** — Play Billing handles entirely; we never see card data.
- **Financial info → Other financial info** — would only have covered SYNC-encrypted recurring expenses / income sources / savings goals / amortization; all E2EE-exempt.
- **Location → Approximate location** — was a candidate when Firebase Analytics IP-geo was on; **disabled at the GA4 admin level on 2026-05-05** (Admin → Data collection and modification → Data collection → "Granular location and device data collection" toggled off).
- **App activity → In-app search history / Other user-generated content / Other actions** — transaction search is local-only; transaction notes go through SYNC E2EE; no behavioral telemetry.
- **Files and docs** — CSV/PDF exports stay on-device; backups go to user-selected SAF location.
- **Health and fitness, Messages, Audio, Calendar, Contacts, Web browsing** — not touched.

## Change triggers — when to revisit this submission

### Immediate resubmission required if any of these happen:

**New non-E2EE data flow:**
- A non-Google third-party SDK is added (any analytics, crash, A/B testing, or backend service beyond Firebase + AdMob + Gemini)
- A new server endpoint that sees plaintext user data
- The AI provider changes (e.g., Gemini → OpenAI / Claude / Anthropic). Each provider's caching + training-data policy is different.

**Toggle / opt-out changes:**
- Removing the "Send crash reports and anonymous usage data" toggle → Crash logs / Diagnostics / App interactions / User IDs all flip to **Required**
- Removing AdMob entirely → Device IDs **Shared** tick goes away; Advertising purpose drops; ad ID disappears so the Required justification weakens
- Removing AI CSV opt-in → Purchase history declaration goes away (only flow that needed it)
- Removing AI OCR sparkle-tap → Photos declaration goes away

**Re-enabling things we explicitly disabled:**
- Re-enabling Analytics IP-geo (GA4 admin "Granular location and device data collection") → must add **Location → Approximate location**
- Removing or changing the manifest meta-data `google_analytics_adid_collection_enabled=false` → reasoning for Device IDs collection-purpose section needs revisiting (Analytics would be added back)
- Calling `FirebaseAnalytics.setUserId(...)` with a non-anonymous value → User IDs becomes much more sensitive; may need Account management purpose

**New data types entering the picture:**
- Any location-using feature (Maps, geofencing, store locator, etc.) → Location declaration
- Adding a messaging or chat feature → Messages declaration
- Reading device contacts or calendar → those declarations
- Reading user files beyond app-private storage (vs writing to user-chosen storage) → Files and docs

**Personalization features:**
- If you ever add content recommendations driven by user behavior (e.g., "you frequently buy coffee on Mondays — consider a coffee budget"), the **Personalization** purpose needs adding to the relevant data types

### Soft triggers — worth revisiting opportunistically:

- **Vertex AI Abuse Monitoring opt-out approval** (currently enterprise-only; if Tech Advantage LLC ever gets approved): could legitimately flip Purchase history + Photos to **Ephemeral: Yes**, hiding them from the public store listing.
- **MASVS L1 audit completed**: pre-survey gate "independent security review" flips to Yes.
- **Privacy policy edits**: any change to `#data-deletion`, third-party services table, or the diagnostic-data section should prompt an audit of this form for consistency.

## Where the answers live

- **Privacy policy** (must stay consistent): `https://techadvantagesupport.github.io/privacy` — repo `techadvantagesupport.github.io`, file `privacy.md`.
- **Spec linkage:** `spec_diagnostics.md` (Crashlytics + Analytics architecture), `MEMORY.md` SYNC section (E2EE), `project_ai_csv_categorization.md`, `project_ocr_pipeline_decisions.md`, `project_ad_implementation.md`.
- **Code-side enforcement:** `BudgeTrakApplication.onCreate` lines ~115–119 (single toggle gates both Crashlytics and Analytics); `AndroidManifest.xml` (ad-ID linking off); GA4 admin (IP-geo off).
