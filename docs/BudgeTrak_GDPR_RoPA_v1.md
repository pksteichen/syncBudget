# BudgeTrak — Record of Processing Activities (RoPA)

**Required under GDPR Article 30(1).** This is an internal document
that lists every processing activity BudgeTrak performs on personal
data of natural persons in the EU/EEA, the lawful basis for each, the
categories of recipients, retention periods, and the technical and
organisational measures protecting the data. It must be kept up to
date as features change, and it must be available for supervisory
authorities on request (Art. 30(4)).

The public-facing [Privacy Policy](https://techadvantagesupport.github.io/privacy)
is what users read; this document is what regulators read. The two
must stay consistent — when a new feature, processor, or data flow
is added, update both.

**This document is NOT published.** It contains internal specifics
(precise SDK names, retention defaults, security measure
implementations) that are not appropriate for the public Privacy
Policy but are required for GDPR compliance.

---

## 0. Document Metadata

| Field | Value |
|---|---|
| Document version | 1.0 |
| Document date | 2026-05-20 |
| Author | Tech Advantage LLC (controller) |
| Next scheduled review | 2026-11-20 (every 6 months) or on material feature change |
| Cross-references | Privacy Policy (public); `firebase-config-reference.txt` (backend); `project_play_data_safety.md` (Play Console Data Safety form); `feedback_vendor_neutral_in_app_copy.md` (in-app copy guidance) |

### Triggers requiring an update to this RoPA

Any of the following requires a same-day review of the affected
section(s):

- Adding or removing a third-party processor.
- Changing an AI provider or model.
- Changing retention defaults on any data category.
- Adding a new opt-in feature that processes personal data.
- Material change to the security architecture (encryption,
  authentication, integrity verification).
- Receipt of a data-subject access request, complaint, or supervisory
  authority inquiry — log it here and link to the response.

---

## 1. Controller and Contact Information

| Item | Value |
|---|---|
| **Controller** | Tech Advantage LLC |
| **Jurisdiction of establishment** | United States (Limited Liability Company) |
| **Primary contact for data subjects** | techadvantagesupport@gmail.com |
| **Privacy-related contact** | techadvantagesupport@gmail.com |
| **Data Protection Officer (DPO)** | Not appointed. BudgeTrak does not meet the GDPR Art. 37 criteria for mandatory DPO appointment: (a) we are not a public authority, (b) our core activities do not consist of regular and systematic monitoring of data subjects on a large scale, and (c) we do not process special categories of data (Art. 9) or criminal-conviction data (Art. 10) on a large scale. |
| **EU representative** | Not appointed. BudgeTrak's processing is only occasional, does not include special categories of data, and is unlikely to result in a risk to the rights and freedoms of EU data subjects given the privacy-by-design architecture (end-to-end encryption, anonymous authentication, no PII collection). Art. 27(2)(a) exemption is relied upon. **REVIEW** if user base grows to a level where processing becomes other than occasional, or if a complaint is received from an EU data subject. |

### Lawful basis register

Each processing activity below identifies its specific lawful basis.
The bases used across BudgeTrak are:

- **Contract (Art. 6(1)(b))** — performance of the contract with the
  user (operating the app they installed).
- **Legitimate interest (Art. 6(1)(f))** — diagnostic data for
  stability, anti-abuse, and security. Balancing test: minimal data,
  opt-out provided, no profiling, no impact on data-subject rights.
- **Consent (Art. 6(1)(a))** — Help Chat, AI Receipt OCR, AI CSV
  Categorize, advertising on the free tier. Consent is explicit,
  granular per feature, revocable at any time, and recorded on-device.

No special categories of data (Art. 9) are processed. No
criminal-conviction data (Art. 10) is processed.

---

## 2. Processing Activities

Each activity entry includes Art. 30(1) fields: purposes (b),
categories of data subjects + personal data (c), categories of
recipients (d), third-country transfers (e), retention (f),
technical and organisational measures (g).

---

### Activity #1 — Core App Operation (On-Device)

**Purpose (b):** Provide the core budgeting functionality of the app
(record transactions, calculate available cash, manage recurring
expenses, savings goals, amortization, etc.).

**Categories of data subjects (c):** App users.

**Categories of personal data (c):** Financial information that the
user voluntarily enters: transaction amounts, dates, merchant names,
descriptions, categories, recurring bills, income sources, savings
goals, amortization entries, budget configuration, app settings,
receipt photos. **Note:** None of this data leaves the device under
this activity. It is stored only in the app's private storage
(Android sandboxed `filesDir`) and is not accessible to other apps
or to Tech Advantage.

**Categories of recipients (d):** None. Data stays on the user's
device.

**Third-country transfers (e):** None.

**Retention (f):** Indefinite, controlled by the user. Removed on
app uninstall (Android guarantee).

**Technical and organisational measures (g):**
- Stored in app-private sandbox (other apps cannot read).
- Optional encrypted backups (ChaCha20-Poly1305 with user-supplied
  password via PBKDF2-SHA256, 100k iterations).
- No network transmission under this activity.

**Lawful basis:** Contract (Art. 6(1)(b)) — performance of the
service the user installed.

---

### Activity #2 — SYNC (Multi-Device Family Budget Sharing, Opt-In)

**Purpose (b):** Allow a household (up to 5 devices) to share a
single budget across devices via cloud relay.

**Categories of data subjects (c):** App users who explicitly create
or join a SYNC group.

**Categories of personal data (c):** Same as Activity #1, plus an
anonymous backend-authentication user ID and a randomly-generated
group ID. **Importantly: all financial data is end-to-end encrypted
on the user's device before transmission.** The processor only sees
encrypted blobs and the metadata required for routing (anonymous
device ID, timestamp, encrypted field name hash).

**Categories of recipients (d):** Google LLC (Firebase Firestore,
Cloud Storage, and Realtime Database) acting as a processor under
the Google Cloud Platform Data Processing Addendum.

**Third-country transfers (e):** Yes. Data is transferred to Google
data centers, which may be located in the United States and other
countries. Transfer safeguards:
- Google participates in the EU-U.S. Data Privacy Framework
  (Adequacy Decision of 10 July 2023).
- Google's Standard Contractual Clauses (2021 EU SCCs) are
  incorporated into the Google Cloud DPA for any non-DPF transfer.
- **Material risk reduction:** all data is encrypted with
  ChaCha20-Poly1305 on the user's device before transmission. The
  encryption key never leaves the user's devices. Even if a third
  country compelled disclosure from Google, only ciphertext would
  be available. This is documented as the primary technical
  safeguard under Art. 32.

**Retention (f):**
- Encrypted blobs in Firestore + Cloud Storage: until the group is
  dissolved by the admin, OR all member devices are inactive for 90
  consecutive days (server-side TTL cleanup), OR a specific
  transaction/photo is deleted by the user.
- Receipt photos in Cloud Storage: hardcoded 14-day TTL regardless
  of group activity (each device fetches and caches locally).

**Technical and organisational measures (g):**
- End-to-end ChaCha20-Poly1305 AEAD encryption, key never leaves
  user device.
- Anonymous Firebase Authentication (no email/password collected).
- Firebase App Check + Play Integrity attestation to block
  unauthorized clients.
- Firestore + Storage security rules enforce per-group access
  control: each group document can only be read/written by its
  member device tokens.
- Server-side cleanup runs deletion cascade across Firestore +
  RTDB + Storage when a group is dissolved.

**Lawful basis:** Contract (Art. 6(1)(b)) — performance of the SYNC
feature the user explicitly opted into.

---

### Activity #3 — Anonymous Authentication for Cloud Features

**Purpose (b):** Provide an authenticated identity for SYNC and
Help Chat upload operations so server-side access controls can
distinguish legitimate clients.

**Categories of data subjects (c):** App users who use SYNC and/or
opt into Help Chat.

**Categories of personal data (c):** Anonymous backend
authentication user ID (a random identifier, not the user's email,
name, phone, or any directly-identifying personal data). Persistent
across app sessions on the same device unless the user uninstalls.

**Categories of recipients (d):** Google LLC (Firebase
Authentication).

**Third-country transfers (e):** Same as Activity #2 (DPF + SCCs +
end-to-end encryption of the underlying data).

**Retention (f):** Anonymous user ID persists until the user
uninstalls the app or signs out (rare for anonymous auth). No
specific deletion mechanism is offered because the ID is not linked
to any directly-identifying data.

**Technical and organisational measures (g):**
- Anonymous sign-in only; no credentials collected.
- HTTPS/TLS for all auth handshakes.
- Token refresh ≤ 1 hour; cached locally in Firebase SDK secure
  storage.

**Lawful basis:** Legitimate interest (Art. 6(1)(f)) — necessary to
provide secure server-side access control for SYNC and Help Chat,
which the user has explicitly opted into. Balancing test:
authentication is the minimum identifier required for security and
collects no personal data beyond a random opaque ID.

---

### Activity #4 — Anti-Abuse Attestation (App Check + Play Integrity)

**Purpose (b):** Prevent unauthorized client applications from
accessing BudgeTrak's cloud backend.

**Categories of data subjects (c):** App users (all, regardless of
opt-ins).

**Categories of personal data (c):** Cryptographic attestation
issued by Google Play Integrity verifying that the app is genuine,
running on a real Android device, and signed with our production
keys. The attestation contains anonymous device-integrity verdicts;
no personally-identifying data.

**Categories of recipients (d):** Google LLC (Play Integrity API +
Firebase App Check).

**Third-country transfers (e):** Same as Activity #2.

**Retention (f):** Attestation tokens are short-lived (40 hours
maximum for release builds; 1 hour for debug). Stored ephemerally
in Firebase SDK secure storage on the device. Not retained on
Tech Advantage servers — they are validated on each API call and
discarded.

**Technical and organisational measures (g):**
- Attestation cryptographically signed by Google; tampered or
  emulated devices cannot produce valid attestations.
- Token refresh handled by Firebase SDK; cached locally.
- HTTPS/TLS for all attestation requests.

**Lawful basis:** Legitimate interest (Art. 6(1)(f)) — essential to
prevent unauthorized scraping or abuse of the cloud backend that
would degrade the service for legitimate users. Balancing test:
attestation collects no personal data beyond device-integrity
verdicts.

---

### Activity #5 — Crash Reports and Diagnostics

**Purpose (b):** Identify and fix bugs that affect users.

**Categories of data subjects (c):** App users who have NOT opted
out (default on, single opt-out toggle at Settings → Privacy → Send
crash reports and anonymous usage data).

**Categories of personal data (c):**
- Crash stack traces and error messages.
- Anonymous device information (model, OS version, app version).
- Anonymous backend authentication user ID (if the user uses SYNC).
- Counters of the user's data (transaction count, recurring expense
  count, etc.) — never the contents.
- A SHA-256 hex digest of the user's available cash balance
  (one-way hashed on-device before transmission; cannot be reversed).
- Timestamped lifecycle events used to debug sync (no financial
  data in event names or properties).

**Categories of recipients (d):** Google LLC (Firebase Crashlytics).

**Third-country transfers (e):** Same as Activity #2.

**Retention (f):** 90 days at the processor (Firebase Crashlytics
default). After 90 days the crash report is automatically deleted
by Google and Tech Advantage cannot retrieve it.

**Technical and organisational measures (g):**
- HTTPS/TLS in transit.
- Cash balance is one-way SHA-256 hashed on-device before
  transmission — even Tech Advantage cannot reverse the digest.
- IP-based geographical inference is **disabled** in the Firebase
  Analytics configuration (verified via Firebase Console).
- Single in-app opt-out toggle that immediately halts data
  collection.

**Lawful basis:** Legitimate interest (Art. 6(1)(f)) — necessary
to maintain a stable, usable app. Balancing test: minimal data,
no PII, on-device hashing of the only sensitive field, easy
opt-out provided at first launch and in Settings.

---

### Activity #6 — Anonymous Usage Analytics

**Purpose (b):** Measure feature usage and improve the app (AI
receipt-scanning accuracy, sync reliability, AI cache effectiveness).

**Categories of data subjects (c):** App users who have NOT opted
out (default on, shares the same opt-out toggle as Activity #5).

**Categories of personal data (c):** Anonymous events:
- `first_open`, `session_start`, `app_update` (standard Firebase
  Analytics events; record that the app was used).
- `ocr_feedback` — booleans + integer deltas describing how much
  the user corrected an OCR-populated transaction. Never includes
  the actual values.
- `health_beacon` — once-daily counters (listener up/down, device
  count in SYNC group, total transactions count).
- `ai_call_metrics` — per AI API call: feature label
  (`help_chat`/`ocr`/`csv_categorize`), model name, token counts
  (prompt/cached/output), cache hit percent. No prompt text, no
  reply text, no merchant names, no transaction content.

**Categories of recipients (d):** Google LLC (Firebase Analytics).
Optional BigQuery export to Tech Advantage's project for richer
slicing (no additional recipients).

**Third-country transfers (e):** Same as Activity #2.

**Retention (f):** Event-level data retention: 14 months at the
processor (Firebase Analytics default). User-property data: 2
months default. **Update if Firebase defaults change.**

**Technical and organisational measures (g):**
- IP-based geographical inference **disabled** in Firebase
  Analytics configuration.
- All event parameters are counts, booleans, or fixed enum strings
  — no free-text payloads ever land in Analytics.
- HTTPS/TLS in transit.
- Same opt-out as Activity #5.

**Lawful basis:** Legitimate interest (Art. 6(1)(f)) — improving
product quality and identifying feature issues. Balancing test:
no PII, no profiling, no advertising-related processing; easy
opt-out.

---

### Activity #7 — In-App Purchase Verification

**Purpose (b):** Verify entitlement to paid features.

**Categories of data subjects (c):** Users who have made a one-time
purchase or active subscription.

**Categories of personal data (c):** Purchase token, product ID,
purchase status. **No payment method, credit card number, or
billing address is processed by BudgeTrak** — those are processed
entirely by Google Play.

**Categories of recipients (d):** Google LLC (Google Play Billing).

**Third-country transfers (e):** Same as Activity #2.

**Retention (f):** Purchase tokens stored on-device for entitlement
checks. Periodically re-verified with Google Play; obsolete tokens
overwritten. No server-side retention by Tech Advantage.

**Technical and organisational measures (g):**
- All purchase verification handled by the official Google Play
  Billing client library.
- Server-side `verifyPurchase` Cloud Function (Gen 2) re-checks
  with Google Play's RTDN endpoint to detect refunds. Calls go
  through Firebase App Check.
- HTTPS/TLS.

**Lawful basis:** Contract (Art. 6(1)(b)) — performance of the
paid-tier contract the user entered into.

---

### Activity #8 — Help Chat (AI Assistant, Opt-In)

**Purpose (b):** Answer user questions about how the app works;
collect user feedback for product improvement.

**Categories of data subjects (c):** Users who:
- Have ticked the consent checkbox at Settings → Privacy → Allow
  Chatbot to transmit and store your messages…, AND
- Have tapped Accept on the in-app consent dialog.

**Categories of personal data (c):**
- Text the user types into the chat (potentially including any
  personal information the user voluntarily shares, though the
  system prompt asks the bot to never solicit such information).
- The bot's replies.
- A randomly-generated 128-bit chat ID (not linked to any
  user-identifying data).
- Timestamps, app version, locale tag.
- An AI-generated sentiment score (1-10 integer; the model's
  internal rating of the user's message tone, used to gate the
  in-chat Play Store review prompt).

**Categories of recipients (d):**
- Google LLC (Gemini API) — receives the user's typed message + a
  short excerpt of BudgeTrak's help documentation (the "knowledge
  base") + the most recent ~10 turns of conversation history.
  Returns a reply + sentiment score. Per Google's documented
  defaults, request and response data is deleted after the request
  completes; not used for training.
- Google LLC (Firebase Firestore) — stores the transcript for up
  to 7 days under the random chat ID; not linked to any user
  identifier.

**Third-country transfers (e):** Same as Activity #2. **Additional
note:** chat text is NOT encrypted at rest (it must be readable by
Tech Advantage for the abuse-review purpose). Firestore's standard
at-rest encryption applies but the encryption key is held by
Google. Material risk: Google's compelled-disclosure exposure is
higher for chat than for SYNC data. Mitigation: anonymous chat ID
(no tie to user identity), 7-day TTL, opt-in only.

**Retention (f):**
- Server-side: 7 days TTL on the Firestore `helpChatLogs`
  collection. Each upload refreshes the TTL window. Idle chats
  auto-expire.
- Client-side: 48-hour automatic prune of the local buffer
  regardless of upload status.

**Technical and organisational measures (g):**
- Opt-in consent dialog required before any data is sent.
- Random 128-bit chat ID — not linked to backend authentication
  user ID, device ID, IP address, or any directly-identifying
  data.
- Firestore TTL policy enforces 7-day automatic deletion.
- HTTPS/TLS in transit.
- API key restricted by Android package name + signing certificate
  SHA-1 fingerprint (Google API key restrictions).
- Per-tier daily caps (Free 10 / Paid 25 / Subscriber 50 messages
  per day) to prevent runaway processing.
- Sentiment score is stored as metadata for internal review of
  Gemini's accuracy; not used for any decision affecting the user.

**Lawful basis:** Consent (Art. 6(1)(a)). Explicit, granular,
revocable at any time via Settings.

---

### Activity #9 — AI Receipt OCR (Subscriber, Opt-In)

**Purpose (b):** Extract merchant name, date, amount, and category
suggestion from a receipt photo the user explicitly chose to scan.

**Categories of data subjects (c):** Active subscribers who tap
the sparkle icon on a specific transaction's receipt photo.

**Categories of personal data (c):** Receipt photo image bytes
(JPEG, compressed). May contain merchant name, address, date,
amount, item descriptions printed on the receipt.

**Categories of recipients (d):** Google LLC (Gemini API).

**Third-country transfers (e):** Same as Activity #2.

**Retention (f):**
- At the AI provider: deleted immediately after the request
  completes (per the provider's documented defaults; not used for
  training).
- On-device: the photo remains with the transaction record (under
  the user's control).
- In cloud (only if SYNC enabled): encrypted with the group key;
  see Activity #2.

**Technical and organisational measures (g):**
- HTTPS/TLS in transit.
- Image is sent only when the user explicitly taps the sparkle
  icon; no automatic background processing.
- API key restricted by Android package + signing cert.

**Lawful basis:** Consent (Art. 6(1)(a)). User action (tap of the
sparkle icon) constitutes the consent event for each individual
receipt.

---

### Activity #10 — AI CSV Categorization (Paid + Subscriber, Opt-In)

**Purpose (b):** Suggest categories for bank-statement transactions
that BudgeTrak's on-device categorizer cannot classify with
confidence.

**Categories of data subjects (c):** Paid users and subscribers who
have ticked the AI CSV Categorize toggle in Settings AND are
importing a bank CSV.

**Categories of personal data (c):** Merchant name + amount of
individual transactions that the on-device matcher could not
confidently categorize. **The transaction date is NOT sent.**

**Categories of recipients (d):** Google LLC (Gemini API).

**Third-country transfers (e):** Same as Activity #2.

**Retention (f):** Deleted immediately after the request completes
at the AI provider. On-device the user retains the categorized
transactions in their normal local data.

**Technical and organisational measures (g):**
- HTTPS/TLS in transit.
- Transactions chunked at 100 per request to bound payload size.
- Falls back silently to the on-device matcher on any AI failure.
- API key restricted by Android package + signing cert.

**Lawful basis:** Consent (Art. 6(1)(a)). Settings toggle is off
by default; user must explicitly enable.

---

### Activity #11 — Advertising (Free Tier Only)

**Purpose (b):** Display native ads in the free tier of the app to
support continued development.

**Categories of data subjects (c):** Free-tier users who have NOT
opted out of personalized advertising via their Android device
settings.

**Categories of personal data (c):** Android advertising
identifier (resettable by the user at any time); basic device
information (model, OS version, app version); approximate
ad-relevant signals as handled by the ad network.

**Categories of recipients (d):** Google LLC (AdMob).

**Third-country transfers (e):** Same as Activity #2.

**Retention (f):** Per Google AdMob's published policy. Tech
Advantage does not retain or process the advertising identifier
itself.

**Technical and organisational measures (g):**
- AdMob SDK is NOT loaded for paid users (gated in
  `BudgeTrakApplication.onCreate`) — paid tier never initializes
  the ad SDK.
- User can reset or limit the Android advertising identifier at
  any time via device settings.
- HTTPS/TLS.

**Lawful basis:** Consent (Art. 6(1)(a) — for personalized
advertising; the user's choice in Android settings is the consent
signal) AND Legitimate interest (Art. 6(1)(f) — for non-personalized
ad serving as a fallback). EU users are presented with the AdMob
consent flow on first ad load (standard SDK behavior).

---

## 3. Data Subject Rights and Procedures

The Privacy Policy describes the user-facing process. Internal
procedures for handling each right:

### Right of access (Art. 15)
- User contacts `techadvantagesupport@gmail.com`.
- Respond within 30 days (Art. 12(3)).
- Standard response: BudgeTrak does not associate any personal data
  with directly-identifying information. The user's local data is
  fully under their control on-device. Cloud-side data (if SYNC was
  used) is encrypted with a key only the user has and cannot be
  recovered without the device.
- If the user provides their anonymous backend auth UID (visible via
  the app's diagnostic dump), we can confirm whether any data
  exists under that UID but cannot decrypt it.

### Right to rectification (Art. 16)
- Not applicable to encrypted data we cannot read. User performs
  rectification by editing the data in-app on their device.

### Right to erasure (Art. 17 — "right to be forgotten")
- Multiple in-app mechanisms (Dissolve Group, Leave Group,
  Uninstall, 90-day inactivity cleanup) documented in the Privacy
  Policy.
- Help Chat transcripts: cannot be erased on a per-user basis
  because they're stored anonymously. Inform the user of the 7-day
  TTL.
- No email-based erasure: the anonymous design precludes
  identifying "this user's data."

### Right to restriction (Art. 18)
- Apply opt-outs in Settings (diagnostics, Help Chat consent, ad
  personalization).

### Right to data portability (Art. 20)
- Use the in-app Export feature (Transactions → Save → CSV / Excel
  / PDF) or the full backup (Settings → Backups).
- No backend-stored personal data exists in a portable form
  (everything cloud-side is encrypted blobs).

### Right to object (Art. 21)
- Diagnostic data + analytics: opt out in Settings → Privacy.
- Advertising personalization: opt out via Android device settings.

### Automated decision-making (Art. 22)
- BudgeTrak does NOT perform automated decision-making that
  produces legal or similarly significant effects on the user. AI
  category suggestions are advisory only and presented in a UI
  the user can override before saving.

### Right to lodge a complaint (Art. 77)
- User should contact their EU member state's supervisory
  authority. We point users to https://edpb.europa.eu/about-edpb/about-edpb/members_en
  if asked.

---

## 4. Data Protection Impact Assessment (DPIA) — Threshold Review

Art. 35 requires a DPIA when processing is "likely to result in a
high risk to the rights and freedoms of natural persons", with
specific triggers in Art. 35(3) and the EDPB guidelines.

**Threshold review for BudgeTrak's current processing:**

| Trigger | Applies? | Rationale |
|---|---|---|
| Systematic and extensive automated evaluation, including profiling, that produces legal effects | No | No profiling; AI suggests categories but does not produce decisions. |
| Large-scale processing of special categories of data (Art. 9) | No | No special categories processed. |
| Systematic monitoring of a publicly-accessible area on a large scale | No | Not applicable. |
| EDPB criterion: evaluation or scoring including profiling | No | No profiling; sentiment score is internal metadata only, not used for decisions about users. |
| EDPB criterion: automated decision-making with legal/significant effects | No | None. |
| EDPB criterion: systematic monitoring | No | Diagnostics are opt-out anonymous counts, not monitoring. |
| EDPB criterion: sensitive data or data of a highly personal nature | Partial | Financial data is sensitive. **Mitigation: end-to-end encrypted before any cloud transmission; the controller cannot read it.** |
| EDPB criterion: data processed on a large scale | Borderline | Currently small alpha. **REVIEW** if user count exceeds 100k or if processing volume grows materially. |
| EDPB criterion: matching or combining datasets | No | No combining; each activity is siloed. |
| EDPB criterion: data concerning vulnerable data subjects | No | Adult/teen audience; no children under 13. |
| EDPB criterion: innovative use or applying new technological solutions | Partial | AI features are novel. **Mitigation: opt-in only, transparent disclosure, no profiling, no user-impacting decisions.** |
| EDPB criterion: when the processing prevents data subjects from exercising a right or using a service | No | All AI features have non-AI fallbacks. |

**Conclusion:** A formal DPIA is not currently required because no
two EDPB criteria apply at the level of "high risk." Two criteria
apply partially (sensitive data, novel technology) but are
mitigated by privacy-by-design (end-to-end encryption, opt-in
consent, no profiling). **REVIEW THIS CONCLUSION** if:
- User base exceeds 100,000 EU/EEA users.
- A new processing activity is added that processes financial data
  without end-to-end encryption.
- AI features are extended to produce decisions affecting the user
  (e.g., automatic budget cuts, automated transaction blocking).

---

## 5. Security Measures (Art. 32) — General Description

Beyond the per-activity measures above, the following apply globally:

- **Encryption in transit:** HTTPS/TLS for all network
  communication. Certificate pinning not currently used (relies on
  Android's standard CA store).
- **Encryption at rest:**
  - All SYNC data: ChaCha20-Poly1305 AEAD, key generated and held
    only on user devices.
  - Local backups: same primitive, password-derived key via
    PBKDF2-SHA256 (100k iterations).
  - Receipt photos in cloud: ChaCha20-Poly1305 with group key.
- **Authentication:** Anonymous Firebase Authentication; tokens
  refresh ≤ 1 hour.
- **Anti-abuse:** Firebase App Check + Play Integrity attestation
  required for all Firestore + Storage + Cloud Function calls.
- **Server-side access control:** Firestore + Storage rules enforce
  per-group, per-device access; published rules under version
  control (`firestore.rules`, `storage.rules`).
- **Confidentiality of personnel:** Tech Advantage is a single-owner
  LLC; no employees with access to systems beyond the owner.
- **Resilience:** Firebase provides multi-region replication;
  point-in-time restore available for Firestore.
- **Regular testing of effectiveness of measures:** Firestore +
  Storage rules audited via `node tools/fetch-rules.js` on each
  release; manual review against the source-of-truth files in the
  repo.

---

## 6. International Transfers — Detailed Mechanism

All third-party processors are Google LLC. Transfers to the United
States rely on the following mechanisms in order of priority:

1. **EU-U.S. Data Privacy Framework** — Google LLC self-certified
   under the DPF (effective 10 July 2023; Adequacy Decision by EU
   Commission). This is the primary transfer mechanism.

2. **Standard Contractual Clauses (2021)** — Incorporated into the
   Google Cloud Platform Data Processing Addendum (DPA) that Tech
   Advantage has agreed to. Used as a fallback in the event the
   DPF Adequacy Decision is invalidated (as happened with
   Schrems II / Privacy Shield).

3. **End-to-end encryption (technical safeguard)** — For SYNC data
   and receipt photos, the practical risk of any government-
   compelled disclosure is eliminated because Google holds only
   ciphertext. This is the load-bearing safeguard given the
   historical instability of EU-US transfer mechanisms.

For data NOT end-to-end encrypted (Help Chat transcripts,
diagnostics, analytics), the DPF + SCCs are the operative
safeguards. The anonymity of the data (no email, no name, no
device ID, no IP at-rest) significantly reduces residual risk.

---

## 7. Records of Processing-Related Events

This section is appended to over time. Each entry: date, event,
action taken.

| Date | Event | Action |
|---|---|---|
| 2026-05-20 | Initial RoPA created. | Document published internally. |

*(Add data-subject requests, supervisory authority correspondence,
breach notifications under Art. 33, and material processing
changes here as they occur.)*

---

## 8. Personal Data Breach Notification Procedure (Art. 33–34)

In the event of a personal data breach:

1. **Detection:** Crashlytics alerts, Firebase security rule
   violations, abnormal Firestore access patterns, third-party
   processor incident notifications.
2. **Containment:** Suspend affected processing activity. Rotate
   API keys / Firebase service-account credentials if compromised.
3. **Assessment:** Within 24 hours, determine:
   - Nature of breach (confidentiality, integrity, availability).
   - Affected categories of data subjects + data.
   - Likely consequences.
   - Number of affected records.
4. **Notification to supervisory authority (Art. 33):** Within 72
   hours if the breach is likely to result in a risk to the rights
   and freedoms of natural persons. Submit to the lead supervisory
   authority of the EU member state with the most affected
   residents.
5. **Notification to data subjects (Art. 34):** Without undue delay
   if the breach is likely to result in a HIGH risk. Use in-app
   notification + email if the user has provided one (note: most
   users have not; in-app push is the practical channel).
6. **Documentation:** Log the breach in Section 7 of this document
   regardless of notification threshold.

**Key consideration for BudgeTrak's architecture:** because all
SYNC financial data is end-to-end encrypted with keys held only on
user devices, a breach of cloud-side data does NOT expose financial
data — only ciphertext. This significantly reduces the likelihood
of any breach meeting the "risk to rights and freedoms" threshold.
Breaches affecting Help Chat transcripts (not encrypted at-rest)
would have a higher notification likelihood.

---

## 9. Annexes

### Annex A — Master list of processors

| Processor | Role | DPA / SCC reference | Last reviewed |
|---|---|---|---|
| Google LLC | All Firebase services, Gemini AI, AdMob, Play Billing, Play Integrity | Google Cloud Platform DPA + DPF self-certification | 2026-05-20 |

### Annex B — Data flow diagram

```
USER DEVICE
├── Local sandbox storage (Activity #1) — never leaves device
├── Encrypted backup files (Activity #1) — local only, password-encrypted
│
├─[opt-in: SYNC]→ Encrypted blobs → Firestore + Cloud Storage + RTDB (Activity #2)
├─[opt-in: SYNC or Help Chat]→ Anonymous UID → Firebase Auth (Activity #3)
├─[always]→ Integrity attestation → Play Integrity + App Check (Activity #4)
│
├─[default-on, opt-out]→ Crash data → Crashlytics (Activity #5)
├─[default-on, opt-out]→ Anonymous events → Analytics (Activity #6)
│
├─[on purchase]→ Purchase token → Google Play Billing (Activity #7)
│
├─[opt-in consent: Help Chat]→ Chat text + KB excerpt → Gemini → reply (Activity #8)
├─[opt-in consent: Help Chat]→ Anonymous transcript → Firestore (7d TTL) (Activity #8)
│
├─[opt-in: Subscriber + tap sparkle]→ Receipt JPEG → Gemini → fields (Activity #9)
├─[opt-in: Paid/Sub Settings toggle]→ Merchant+amount → Gemini → category (Activity #10)
│
└─[free tier only]→ Ad request → AdMob (Activity #11)
```

### Annex C — Cross-references

- Public Privacy Policy: https://techadvantagesupport.github.io/privacy
- Spanish Privacy Policy: https://techadvantagesupport.github.io/es/privacy
- Play Console Data Safety form details: `memory/project_play_data_safety.md`
- Backend configuration source-of-truth: `firebase-config-reference.txt`
- Firestore + Storage security rules: `firestore.rules`, `storage.rules`
- Help Chat feature details: `memory/project_help_chat_assistant.md`
- AI prompt caching strategy: `memory/feedback_gemini_prompt_caching.md`
