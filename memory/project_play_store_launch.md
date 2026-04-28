---
name: Google Play Store launch plan
description: Developer account setup, business-address plan, and the personal → org migration path for BudgeTrak's first Play publication
type: project
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
