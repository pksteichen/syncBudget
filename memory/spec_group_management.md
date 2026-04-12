---
name: Group Management Specification
description: SYNC group lifecycle — create, pair, join, admin transfer, removal, dissolution (Cloud-Function-driven)
type: reference
---

# Group Management Specification

## Group creation (admin)
1. `FirestoreService.createGroup()` generates a 12-char hex `groupId` + a 256-bit random encryption key.
2. Encryption key stored locally in `SecurePrefs` (Android KeyStore-backed AES256-GCM). Raw key never leaves the device unencrypted.
3. Firestore group doc created with `createdAt, updatedAt, expiresAt = now + 90d, status = "active"`. `expiresAt` is the TTL field — `lastActivity` must never be used for TTL (it is always in the past; this caused overnight dissolution before 2026-04-04).
4. `familyTimezone` defaulted to the device's timezone in `SharedSettings`.
5. Device registered in `groups/{gid}/devices/{deviceId}` with `isAdmin = true, removed = false`.

## Pairing code
- 6 characters drawn from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no ambiguous `0/O`, `1/I`).
- Stored at `pairing_codes/{code}` with `{groupId, encryptedKeyBlob, expiresAt = now + 10 min}` (TTL field). One-time use — deleted on redemption.
- The sync key is encrypted **using the 6-char code as a PBKDF2 password** (ChaCha20-Poly1305 AEAD). The raw key is never stored in Firestore. Entering the wrong code decrypts to garbage and join is rejected at AEAD-verify time.
- `normalizeCode()` (uppercase + trim) is centralized — both encrypt and decrypt paths use it, so whitespace and case never cause rejections.

## Device join
1. User enters code → `FirestoreService.redeemPairingCode(code)` validates format, checks expiry, deletes the pairing doc.
2. Retrieves `groupId` + decrypts encryption key via the code; stores locally in `SecurePrefs`.
3. Registers device in `devices/{deviceId}` with `isAdmin = false`.
4. Clears local transactional data; starts Firestore listeners. Initial full-collection read populates state.
5. `sharedSettings` and category list arrive via the same listeners.
6. For large receipt counts, the join-snapshot mechanism (see `spec_receipt_photos.md`) avoids per-file recovery.

There is no "data merge" prompt on join — the joining device becomes an empty group member and adopts the group's data.

## Admin vs non-admin
- **Admin** — generate pairing codes, rename/remove devices, edit `familyTimezone`, toggle `showAttribution`, dissolve group. Admin role lives on the device doc (`isAdmin = true`).
- **Non-admin** — view/add/edit data, leave group, claim admin (requires subscription per `project_pricing.md`).
- Admin cannot be removed by other devices. The device roster shows role badges.

## Admin transfer (24-hour objection window)
1. A non-admin device writes an `adminClaim` sub-document (`claimantDeviceId, claimedAt, expiresAt = now + 24h, objections: []`).
2. Other devices see the pending claim and surface a vote dialog (`MainViewModel.voteOnAdminClaim`). Objections are appended via Firestore transaction to preserve concurrent votes.
3. On expiry:
   - **No objections** → atomic Firestore transaction: demote previous admin, promote claimant, delete the `adminClaim` doc.
   - **Any objections** → claim rejected, `adminClaim` deleted.
4. `resolveExpiredClaim` is called opportunistically from `onResume` and background work.

## Device removal
- **Involuntary**: admin marks a device `removed = true` (not deleted — affirmative signal). Target device detects on next sync and auto-leaves (`evictFromSync`). `showAttribution` is reset to `false` on eviction.
- **Voluntary**: non-admin taps Leave → cleans local sync state, deletes its own `members/{uid}` and `devices/{deviceId}` entries, stops listeners.

## Group dissolution — Cloud-Function-driven

Dissolution no longer paginates subcollection deletes from the client. The current flow:

1. Admin invokes `FirestoreService.deleteGroup()`.
2. Client deletes its own `members/{uid}` via the self-deletion rule (`request.auth.uid == uid`).
3. Client deletes the **group doc** (`groups/{groupId}`) directly.
4. The `cleanupGroupData` Cloud Function (`functions/index.js`, v1 API, Node.js 22) triggers on group-doc delete and cascade-deletes all 14 subcollections (`transactions, recurringExpenses, incomeSources, savingsGoals, amortizationEntries, categories, periodLedger, sharedSettings, devices, members, imageLedger, adminClaim`, and legacy `deltas, snapshots` for old groups) plus RTDB presence and Cloud Storage photo files — all via admin SDK, bypassing rules.
5. Non-admin devices detect the missing group doc and auto-leave.

The legacy `deltas` and `snapshots` collections were removed from the **client**-side list on 2026-04-12 (see `project_dissolve_bug_2026_04_12.md`); they remain in the Cloud Function list because old groups may still have leftover docs. Client-side security rules deny queries against collections with no matching rule, so every subcollection touched by the client must have an explicit rule — a trap that caused the dissolve bug before the fix.

## Dissolve-bug lesson
Firestore evaluates rules on query patterns, not document existence. Even querying an empty collection triggers rule evaluation. Before 2026-04-12, an old Firestore offline cache masked this for months on installs with long-lived listeners; the rebrand's fresh cache exposed the bug.

## Solo-mode fallback
- Without a group, Firebase Auth, App Check, RTDB, Firestore reads, and imageLedger are all skipped. Local JSON repos are authoritative. Tombstones are purged locally in `runPeriodicMaintenance` (no sync partners to coordinate with).
- `isSyncConfigured` gates all sync paths. See MEMORY.md "Three-tier background worker".
