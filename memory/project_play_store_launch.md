---
name: Google Play Store launch plan
description: Developer account setup, business-address plan, and the personal → org migration path for BudgeTrak's first Play publication
type: project
originSessionId: 2ae43715-e466-4f34-8cb2-c1df4c388ef5
---
## Strategy
Publish BudgeTrak under a **personal** Google Play Developer account first to ship sooner, then transfer to an **organization** account once DUNS + LLC fully verify. Transfers between accounts are supported but take 5-15 business days and require both accounts in good standing.

## Business addresses

The LLC (Minnesota) has, or will have, three separate addresses — this is normal for a business:

| Purpose | Planned address | Public? | Notes |
|---|---|---|---|
| Registered office (MN Articles of Organization) | RA service's street address | Yes, via MN Sec. State | Already filed. Do not change. |
| Principal business / DUNS | **Virtual mailbox (to be obtained)** — MN location via iPostal1 or similar | Only via paid D&B reports | Initially registered with home address — **update via iUpdate** once DUNS issues. |
| Public contact (Google Play Trader) | Same virtual mailbox | Yes (on Play Store listing) | Matches DUNS → cleaner trader verification. |

**Do NOT use home address anywhere public-facing.** Currently only DUNS has it; update when the DUNS is issued.

**USPS Street Addressing was considered** (converting a PO Box to appear as the post office's street address with a unit number) but the user's local post office doesn't offer the service. Virtual mailbox is the fallback.

## DUNS status
- Registered 2026-04-?? with Tech Advantage LLC + home address.
- DUNS number issues in up to 30 business days (free tier) or ~7 business days (paid expedited, ~$229-$499).
- Once issued, **immediately** log into https://iupdate.dnb.com and change the business address from home to the virtual mailbox address.
- Processing time for address update: 5-30 business days.
- Total realistic timeline before org Google Play account can be opened cleanly: 4-8 weeks on the free path, 2-3 weeks with paid expedite.

## Sequence

### Phase 1 (this week)
1. Sign up for iPostal1 (or Anytime Mailbox / PostScan Mail). Pick an MN location.
2. Complete USPS Form 1583 with online notarization (MN requires notarization). ~1-3 days.
3. Request a proof-of-address letter from the service.
4. Open the **personal** Google Play Developer account at https://play.google.com/console/signup. Use the virtual mailbox address for public contact; use home address for identity verification (private to Google's compliance team).
5. Publish BudgeTrak to internal testing track.

### Phase 2 (2-8 weeks out)
1. When DUNS number arrives → immediately update address in iUpdate to the virtual mailbox.
2. Wait for update to propagate (5-30 business days).
3. Open the **organization** Google Play Developer account using the verified LLC + updated DUNS. Second $25 fee.
4. Initiate app transfer from personal → org account. 5-15 business days.
5. Once transfer completes, the personal account is empty and can be closed (optional — keeping it dormant is fine).

## Play Store listing prerequisites (checklist — track separately)
- Privacy policy URL: `https://techadvantagesupport.github.io/privacy` ✓ (moved from `/budgetrak-legal/privacy` on 2026-04-27 to org root Pages site; legacy URL still serves for v2.7 fallback)
- Release-signed AAB ✓ — `BudgeTrak.aab` (v2.7 / versionCode 4) at `/storage/emulated/0/Download/`. Upload key SHA-256: `E0:2B:5D:D6:5E:86:1B:3B:79:AC:F4:F3:F4:76:D4:3B:35:D1:FC:3A:D4:E1:6D:26:C0:CC:0D:22:E9:9D:04:0A`. Keystore at `~/keystore/upload-keystore.jks` (backed up to `/storage/emulated/0/Download/BudgeTrak Keystore Backup/`); password in `local.properties` and password manager.
- Content rating questionnaire — fill in Play Console during listing setup.
- Feature graphic, screenshots, icon — existing assets should work.
- Trader status: yes (paid tier + ads = commercial).
- EU DSA trader contact — required once business address + contact details are live.

## Why personal → org vs. waiting for org
Trade-off acknowledged: publisher display name on Play Store changes from personal ("Paul Steichen") to "Tech Advantage LLC" during the account transfer. Reviews and ratings carry through transfer. Users may or may not notice the publisher rename. Ship-fast-and-iterate beat wait-30-days in this case.

---

## Current state (as of 2026-05-02 / v2.10.04)

**Where we are:**
- Personal Google Play Developer account opened — verified.
- App listing created — package `com.techadvantage.budgetrak` ("BudgeTrak").
- **App-level status: "Draft app"** — Google's new-personal-account gate hasn't been satisfied (see "Closed-test gate" below).
- **Internal testing track:** Active · Not reviewed. v2.10.00 (versionCode 15) was uploaded manually; further releases possible via CI as `status: draft` (then promote in Console).
- **Closed testing track:** Inactive. Required to unlock production.
- **Open testing track:** Inactive. Optional.
- **Production track:** Inactive. Locked until 12-testers/14-days closed test completes + production-access application approved.
- **CI auto-publish (`.github/workflows/release.yml`):** wired and validated through the upload step. `release_status` workflow input defaults to `draft` while the app is in draft state; flip to `completed` once production access is granted.
- **Public visibility:** zero — listing not in Play Store search; no public install path.

## Remaining step-by-step to go fully public

### A. Finish app content tasks (5 of 11 currently complete)
Visible at **Play Console → Dashboard → "Finish setting up your app"**. Done: Privacy policy, App access, Ads, Content rating, Target audience. Outstanding:

1. **Data safety** — declare what user data is collected, retention, deletion. BudgeTrak's actual data: anonymous Auth UID + sync group docs + Crashlytics + Analytics events when opted in. Privacy policy already enumerates the five deletion paths under `#data-deletion`.
2. **Government apps** — N/A; declare "No, this app is not a government app."
3. **Financial features** — declare "Personal finance tracking / budgeting" and any relevant sub-categories. No banking integrations, no payments, no lending.
4. **Health** — N/A; no health data collected. (BudgeTrak is finance, not health.)
5. **Select an app category and provide contact details** — category: "Finance" → "Budgeting" subcategory if available. Contact email: `techadvantagesupport@gmail.com`. Public contact address: virtual mailbox once obtained.

### B. Stand up closed testing track + recruit 12 testers
Once app-content tasks A1–A5 are clean:

1. **Play Console → Test and release → Closed testing → Create new track** (or use the auto-created "Alpha" track). Pick countries (start with US to keep scope narrow).
2. **Add testers** — Closed testing → Testers tab → "Create email list" → paste 12+ emails. Real Google accounts only; iCloud / Yahoo addresses won't work for the Play Store opt-in dance.
3. **Create a release on closed testing** — upload an AAB (CI dispatch with `release_track: alpha, release_status: draft`, then promote in Console; or do it manually).
4. **Send to Google for review.** Closed testing review usually takes hours, not days.
5. **Have all 12 testers opt in via the share URL** (see "Tester onboarding mechanics" below). The Console shows opt-in count at **Closed testing → Testers tab**; needs to reach 12 before the 14-day clock matters.
6. **Wait 14 days.** During this window, testers should genuinely use the app — Google sometimes spot-checks. Push at least one or two updates during the window so the closed-test trail looks active, not synthetic.

### C. Apply for production access
Visible at **Dashboard → Production → Apply for production**. Greyed out until the 12/14 conditions above are met. Once eligible:

1. Click **Apply for production**.
2. Answer the questionnaire about the closed test (preview at `Apply for production` → "Preview questions").
3. Submit. Google reviews the application + the closed-test history. Approval typically 1–3 business days.

### D. First production release
After production access is granted:

1. **Update the workflow's default `release_status`** from `draft` back to `completed` if you want fully unattended auto-publish, or leave at `draft` and promote manually for safety on first runs.
2. **CI dispatch** with `release_track: production, release_status: draft`. AAB uploads to production track as a draft.
3. **Manually promote in Console** (Production → Releases → Send for review). Google's full production review takes 1–7 days for first-time production publishes.
4. **After review approves**, choose rollout %: 1% → 5% → 20% → 100% over a few days, or "Full rollout" immediately if confident.

### E. Make the listing publicly searchable
The release being live on production track is necessary but not sufficient. Listing visibility is a separate switch:

1. **Play Console → Setup → Advanced settings → Country availability** — select countries the app should be available in (start with US; expand later).
2. **Play Console → Setup → App content → All tasks complete** — every prompt must be ✓ for the listing to clear public exposure.
3. After both, the listing appears in Play Store search and at `https://play.google.com/store/apps/details?id=com.techadvantage.budgetrak`.

### F. Org-account transfer (later, per Phase 2 above)
Independent of A–E. Can happen before or after going public. See "Sequence → Phase 2" earlier in this doc.

## Tester onboarding mechanics (Internal + Closed testing)

Adding an email to the Testers list **does not** automatically install the app. Testers must:

1. **Get the opt-in URL.** In Play Console:
   - Internal testing: **Test and release → Internal testing → Testers tab → "How testers join your test" → "Copy link"** (e.g. `https://play.google.com/apps/internaltest/4700000000000000000`).
   - Closed testing: same path under Closed testing.
2. **Tester opens the URL on their Android device** (or web browser signed into the same Google account). They see a "Become a tester" page.
3. **Tester taps "Become a tester."** Google verifies the email matches one in the testers list. Status flips to "You're a tester."
4. **Wait ~5–15 minutes** for Play Store cache to update. (Sometimes immediate, sometimes laggy.)
5. **Tester installs from regular Play Store listing** at `https://play.google.com/store/apps/details?id=com.techadvantage.budgetrak`. Google Play recognizes the account as a tester and serves the test-track binary instead of the production one.

Important caveats:

- **Account match.** The Google account signed in on the device must match the email added in Console. If the device has multiple accounts, Play Store uses the active one — easy mismatch.
- **No personal Gmail aliases.** `tester+budgetrak@gmail.com` won't match `tester@gmail.com` even though Gmail treats them the same.
- **Sideloaded debug APK conflicts.** A tester who already has a sideloaded debug build of the same package on their device must **uninstall it first** — the debug keystore signature won't match the upload-key-signed Play binary, and Android refuses to install over a different signature.
- **Internal test = up to 100 testers.** Closed test in the 12/14 gate context uses the same UI but enforces the count requirement at production-access apply time, not at upload time.

## Quick reference — current blocked actions

| Action | Blocked by |
|---|---|
| CI dispatch `release_status: completed` (any track) | App in "Draft app" state — A1–A5 + closed test |
| Production track upload | Production access not granted — closed-test gate |
| Listing publicly searchable | Country availability + remaining content tasks |
| Org-account transfer | DUNS address-update propagation — Phase 2 dependency, independent of A–E |
