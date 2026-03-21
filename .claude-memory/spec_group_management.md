---
name: Group Management Specification
description: Family sync group lifecycle — create, join, pairing codes, admin roles, transfer, removal, dissolution
type: reference
---

# Group Management Specification

## Group Creation (Admin)
1. Generate 12-char hex group ID + 256-bit random encryption key
2. Store key in SecurePrefs (Android KeyStore-backed AES256-GCM)
3. Create Firestore group doc with nextDeltaVersion=1, timestamps
4. Set familyTimezone to device default in SharedSettings
5. Device marked isAdmin=true

## Pairing Code
- 6 chars from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no ambiguous 0/O, 1/I)
- Expires in 10 minutes, one-time use (deleted on redemption)
- Stored in Firestore `pairing_codes/{code}` with groupId + Base64 encryption key
- ExpiresAt stored as Firestore Timestamp (required for TTL policy)

## Device Join
1. User enters code → redeemPairingCode validates format, expiry
2. Retrieves groupId + encryption key, stores locally
3. Registers device in Firestore (isAdmin=false, removed=false)
4. Local data stamped with sync clocks for initial push
5. Join warning dialog confirms data merge before proceeding

## Admin vs Non-Admin
- **Admin**: generate pairing codes, rename/remove devices, edit timezone, toggle attribution, dissolve group
- **Non-admin**: view/add data, leave group, claim admin (requires subscription)
- Admin cannot be removed; device roster shows role badges

## Admin Transfer (24-hour claim process)
1. Non-admin creates AdminClaim (claimantDeviceId, 24hr expiry)
2. Other devices can Object (appended to objections list via Firestore transaction)
3. After expiry: no objections → atomic transfer (demote old + promote new + delete claim); any objections → rejected

## Device Removal
- Admin marks device `removed=true` (not deleted — affirmative signal)
- Removed device detects on next sync → auto-leaves locally
- Voluntary leave: non-admin taps Leave → cleans local state + notifies Firestore

## Group Dissolution
1. Admin writes `status="dissolved"` on group doc (immediate signal to all devices)
2. Paginated subcollection deletion: deltas → devices → snapshots
3. Delete group doc
4. Non-admin devices detect `status="dissolved"` → auto-leave
