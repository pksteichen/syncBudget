---
name: Google Play "Verified" badge — future consideration
description: Currently VPN-only, but worth tracking for if/when Google expands to finance / other sensitive-data categories. BudgeTrak meets several criteria already; some require deferred infrastructure (org account, MASA review).
type: project
---

## What the badge is

Google Play "Verified" badge — a visible trust marker on the app's details page and in search results. Awarded to apps that meet a set of security, privacy, and operational criteria. Goal of the program is to highlight developers who are transparent about how they handle sensitive user data.

**Currently scoped to VPN apps only.** Not available to finance apps (BudgeTrak's category) as of 2026-05-02. Worth tracking because Google has signaled intent to expand the badge to additional categories that handle sensitive data — finance is a natural next category given the same trust dynamics.

## Criteria (full list as published)

1. Follow Play safety and security guidelines.
2. Complete a Mobile Application Security Assessment (MASA) Level 2 validation.
3. Publish on Google Play for at least 90 days.
4. Achieve at least 10,000 installs and 250 reviews.
5. Have an **Organization** developer account type (not Personal).
6. Meet target API level requirements for Google Play apps.
7. Submit Data Safety section declaration, including:
   - Independent security review
   - Encryption in transit

The list is not exhaustive — Google reserves additional unpublished criteria.

## BudgeTrak status against each criterion (as of 2026-05-02)

| Criterion | Status | Notes |
|---|---|---|
| Play safety/security guidelines | ✓ on track | App Check, encrypted SYNC, anonymous auth, opt-in telemetry |
| MASA Level 2 validation | ✗ deferred | Costs ~$5–15K via accredited labs (App Defense Alliance partners). Not started; would need to plan budget + engagement when expansion happens. |
| 90 days on Play Store | ✗ in progress | First Internal testing release 2026-05-01. Public launch TBD. |
| 10K installs + 250 reviews | ✗ deferred | Goal post-launch; depends on marketing push. |
| Org developer account | ✗ deferred | Currently **Personal** account. Transfer to org planned — see [Play Store launch plan](project_play_store_launch.md) (personal-first then transfer-to-org strategy, DUNS timeline). |
| Target API level | ✓ | targetSdk=35 (current Play minimum). |
| Data Safety: encryption in transit | ✓ | Declared "Yes" on first submission. All Firebase/Gemini calls over TLS; SYNC adds ChaCha20-Poly1305 application-layer on top. |
| Data Safety: independent security review | ✗ deferred | Same engagement as MASA — pen-test + code review by accredited firm. |

**Realistic earliest qualification date:** ~6 months after the badge expands to finance category (would need: org transfer + MASA engagement + 90-day clock + organic install/review growth).

## How to apply (when eligible)

There's no manual "apply" surface — Google evaluates eligibility automatically based on the Data Safety section + Play Console signals (install count, account type, MASA registry). Once VPN-only restriction lifts and BudgeTrak meets the criteria, the badge appears without explicit application.

## Triggers to revisit this memory

- Google announces expansion of Verified badge beyond VPN
- Pre-launch checklist hits the "go to Production" phase
- Org account transfer completes
- Considering pen-test / security audit budget for any other reason

If any of these happens, re-read this file and assess whether to invest in MASA + independent review.
