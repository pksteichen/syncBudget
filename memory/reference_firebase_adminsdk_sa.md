---
name: Firebase Admin SDK service account — capabilities for project administration
description: The firebase-adminsdk-fbsvc@sync-23ce9 SA can perform Identity Toolkit + Firebase Rules admin via API, but lacks Datastore Owner (TTL config) and Project IAM Admin (role-granting). Authorized for direct administrative use.
type: reference
---

# Firebase Admin SDK SA — what it can and can't do

**SA path**: `/data/data/com.termux/files/home/dailyBudget/app/src/debug/assets/service-account.json` (gitignored — debug-only asset).
**SA email**: `firebase-adminsdk-fbsvc@sync-23ce9.iam.gserviceaccount.com`
**Project**: `sync-23ce9`

This is the **Firebase Admin SDK** service account auto-provisioned by Firebase, separate from the BigQuery SA at `~/.config/budgetrak/sa-key.json`. Mint a token with the `https://www.googleapis.com/auth/cloud-platform` scope to access the broadest set of APIs the SA has roles for.

## Known capabilities (verified 2026-05-19)

| Operation | API | Status |
|---|---|---|
| Enable / disable sign-in providers (incl. Anonymous Auth) | `PATCH identitytoolkit.googleapis.com/admin/v2/projects/{p}/config` | ✓ works |
| Create rulesets | `POST firebaserules.googleapis.com/v1/projects/{p}/rulesets` | ✓ works |
| Update rule release pointer (e.g. `cloud.firestore`) | `PATCH firebaserules.googleapis.com/v1/projects/{p}/releases/{rel}` | ✓ works |
| Read Firestore rules (read-only scope) | via `tools/fetch-rules.js` | ✓ works |

## Known gaps

| Operation | API | Failure | Workaround |
|---|---|---|---|
| Configure Firestore TTL on a field | `PATCH firestore.googleapis.com/v1/projects/{p}/databases/(default)/collectionGroups/{c}/fields/{f}?updateMask=ttlConfig` | HTTP 403 PERMISSION_DENIED | User clicks in Firebase Console → Firestore → TTL, OR grant SA `roles/datastore.owner` |
| Read project IAM policy | `cloudresourcemanager.googleapis.com/v1/projects/{p}:getIamPolicy` | HTTP 403 | (manual via Console) |
| Test IAM permissions | `cloudresourcemanager.googleapis.com/v1/projects/{p}:testIamPermissions` | HTTP 403 | (manual via Console) |
| Grant additional roles | `setIamPolicy` | likely 403 | User grants via Console; can't self-elevate |

## Authorization model

The user has explicitly authorized direct administrative use of this SA — same model as `/push` for code repos. When the user asks for a Firebase project admin action that this SA can perform, **just do it via API**, no separate confirmation needed. The Firebase CLI is installed but its OAuth token frequently goes stale (RAPT re-auth required) — prefer the SA path unless you specifically need a user-credentialed action.

## Suggested deferred action

Granting `roles/datastore.owner` (or the narrower `roles/firebaseiamapps.adminViewer` for read + a custom role for TTL writes) to this SA would unblock TTL config + similar Firestore admin operations from this environment. Not critical — Console clicks are easy — but would close the last common gap.

## Past invocations

- 2026-05-19 — enabled Anonymous Auth (Help Chat assistant supports solo users)
- 2026-05-19 — deployed `firestore.rules` containing the `helpChatLogs/{chatId}` block (ruleset `f9ffebde-4584-479c-9f55-db712b73a8e5`)
- 2026-05-19 — attempted TTL on `helpChatLogs.expireAt` (PERMISSION_DENIED; user did manually via Console)
