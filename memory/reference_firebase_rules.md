---
name: Firebase rules — source of truth + audit findings
description: firestore.rules, storage.rules, database.rules.json now live in the repo root; firebase.json references them. Fetch script at tools/fetch-rules.js. Photo-sync surface is correctly gated; three general-security ⚠️ items flagged separately.
type: reference
originSessionId: ea9e173a-ca3d-4f87-b67a-ceac73953250
---
# Firebase Rules

## Source of truth (added 2026-04-23)

- **Firestore rules**: `firestore.rules` at repo root.
- **Cloud Storage rules**: `storage.rules` at repo root.
- **RTDB rules**: `database.rules.json` at repo root.
- **Deploy config**: `firebase.json` now lists all three so `firebase deploy --only firestore:rules` / `storage:rules` / `database` work.
- **Refresh from live project**: `node tools/fetch-rules.js` — uses the debug-only service-account JSON (gitignored) to call the Firebase Rules API. Writes all three rule files, overwriting the local copies. Run when rules are modified via Firebase Console.

## Photo-sync rule correctness

- `firestore.rules` — `imageLedger/{docId}` and all other sync-data collections gate on `isMember(groupId)`. The `{document=**}` catch-all denies everything else.
- `storage.rules` — `groups/{gid}/**` requires auth + Firestore membership lookup. Covers `receipts/*.enc`, `photoSnapshot.enc`, `joinSnapshot.enc`.
- RTDB — not used for photo sync (only presence).

**Verdict:** photo sync surface is properly gated. FCM fan-out via `onImageLedgerWrite` + the v2.7 writer-membership check in `collectRecipientTokens` is a defense-in-depth layer on top.

## General-security observations (out-of-scope for photo audit, worth tracking)

### Group doc readable by any authenticated user
`firestore.rules:11` → `match /groups/{groupId} { allow read: if request.auth != null; }` — not `isMember`. An authenticated-but-non-member can poll and see `imageLedgerFlagClock`, `deviceChecksums` (SHA-hashed cash-per-device), `familyTimezone`, `expiresAt`, `status`. Likely required for the pairing-code join flow (new device verifies groupId exists before submitting its members entry); tightening needs care.

### Pairing code readable by any authenticated user
`firestore.rules:57-62` → `allow read, delete: if request.auth != null`. An attacker who knows a pairing code can retrieve `encryptedKey` and offline-brute-force the pairing password. Protection depends on code length + password strength. TTL is 10 min (memory).

### RTDB presence writable without membership check
`database.rules.json:7-10` → `.write: auth != null` for `groups/$groupId/presence/$deviceId`. Any authenticated user can inject fake presence entries. Cannot escalate to FCM spam (requires real `devices/{id}/fcmToken` entry, which is Firestore-gated), but pollutes the presence roster. RTDB rules can't cross-reference Firestore, so tightening requires either Cloud-Functions-based validation or a rule-language workaround.

### No explicit App Check check in rules
App Check enforcement lives at the project/bucket level (Firebase Console), not in rule expressions. If that toggle is ever flipped off, rules alone don't catch it. Mitigation: a monitoring alert on `request.auth.token.firebase_app_check == null` rejected requests — not currently set up.

## Related pre-existing memory files
- `memory/project_prelaunch_todo.md` — general hardening checklist.
- `memory/feedback_image_ledger_no_create_bump.md` — photo-sync-specific design invariant.
