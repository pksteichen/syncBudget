---
name: 5-member group limit — deferred server-side Firestore rule
description: Client-side enforcement (UI gate + joinGroup defense) shipped in v2.10.08, but a Firestore rule on devices/{docId} is needed to close the concurrent-joiner race. Deferred to post-launch.
type: project
---

## Status
**Deferred follow-up.** Client enforcement shipped in v2.10.08 (2026-05-08); server rule pending.

## What shipped in v2.10.08
- **A. UI gate** (`SyncScreen.kt`): Generate Pairing Code button now toasts `S.sync.memberLimitReached` ("Group is full (5 members maximum)") when `devices.size >= 5` instead of calling `onGeneratePairingCode`.
- **B. joinGroup defense** (`GroupManager.kt`): after registering membership (required to read devices subcollection per Firestore rules), fetch device count. If ≥ 5, roll back membership + clear local prefs (`groupId`, `isAdmin`, encrypted `encryptionKey`), return false.

## Why the design intent is "5"
Implicit in `MainScreen.kt:506-508` — Solari sync indicator uses `.take(4)` for *other* devices (4 + self = 5). A 6th member can sync data successfully but won't render an indicator. No documented spec; the cap appears to come from indicator palette / display layout choice.

## Race window the client fix doesn't close
Two devices using the same pairing code concurrently can each pass the `existingDevices.size >= 5` check (both see size=4 before either registers their device). Both register, group ends with 6+. Plus admin's `devices.size` view is eventually consistent, so the UI gate can also miss right after a member leaves and re-joins.

## Proposed server-side fix
Add a count constraint to `firestore.rules` on `groups/{groupId}/devices/{docId}` writes:

```
match /groups/{groupId}/devices/{docId} {
  allow read: if isMember(groupId);
  allow create: if isMember(groupId)
    && get(/databases/$(database)/documents/groups/$(groupId)).data.deviceCount < 5;
  allow update, delete: if isMember(groupId);
}
```

Requires maintaining a `deviceCount` field on `groups/{groupId}` because Firestore rules can't `list().count()` cheaply. Two ways to keep it accurate:

1. **Cloud Function** (`onWrite` on `devices/{docId}`): increments/decrements `deviceCount` atomically. Simplest, race-free, no client work.
2. **Transaction in `registerDevice` / `removeDevice`**: client reads + increments + writes `deviceCount` in a single transaction. Cheaper (no Function invocation) but couples count maintenance to client code paths.

Option 1 is the cleaner path; matches existing `cleanupGroupData` Function pattern (functions/index.js, v1, Node.js 22). Test against existing groups to ensure the count gets backfilled correctly.

## Why deferred
Concurrent-joiner race is rare for the typical 1-3 person family group. UI gate + joinGroup defense covers the common cases. Server-side hardening can land in a post-launch v2.x release without compatibility concerns since adding a stricter rule only blocks clients from doing something they already shouldn't.

## How to validate the deferred fix when shipped
1. Backfill `deviceCount` field on every existing `groups/*` doc via a one-time script.
2. Deploy the Function + rules update in lockstep.
3. Test: 5-member group, attempt `registerDevice` — should fail with PERMISSION_DENIED. Remove a device, count drops, re-add succeeds.
