---
name: Dissolve bug root cause (April 12, 2026)
description: The deleteGroup function failed on every call since security rules were deployed because two legacy collection names (deltas, snapshots) had no matching security rule. Firestore evaluates rules on query patterns even for empty collections.
type: project
originSessionId: e62277a3-386c-4af8-8747-78a2f79a4bee
---
## Root cause

`FirestoreService.deleteGroup()` iterated 14 subcollections and called `deleteSubcollection()` on each. Two of them — `deltas` and `snapshots` — were legacy CRDT collections from pre-v2.1 that had no matching Firestore security rule. The catch-all rule (`match /{document=**} { allow read, write: if false; }`) denied the query, throwing PERMISSION_DENIED. The exception wasn't caught, so the entire dissolve aborted.

Firestore evaluates security rules against the **query pattern**, not against whether documents exist. Even querying an empty/nonexistent collection goes through rule evaluation. So the bug fired on every dissolve, including groups that never had `deltas` or `snapshots` data.

## Why it was hidden before

The old app (`com.securesync.app`) had been running for months. Firestore's **offline cache** had accumulated a complete view of subcollections from long-lived listeners. When `deleteSubcollection` queried `deltas`, the SDK served the empty result from cache without hitting the server — no rules evaluation. Any fresh install, cache clear, or package rename (which creates a new app with an empty cache) exposed the bug.

## The fix (commit d14d8d6)

1. Removed `deltas` and `snapshots` from the client-side subcollection list. The `cleanupGroupData` Cloud Function handles them via admin SDK (bypasses rules).
2. Removed `members` from the collection-query loop. The `members` read rule restricts collection queries. Instead, the caller's own member doc is deleted by auth UID AFTER the group doc delete, using the self-deletion rule (`request.auth.uid == uid`).
3. Added step-by-step logging to every operation in `deleteGroup` — this is what actually identified the bug on the first instrumented run.

## Additional changes made during debugging

- **Members read rule broadened** in Firebase Console: added `|| isMember(groupId)`. Unnecessary for the fix (dissolve no longer queries members) but harmless. Left in place.
- **App Check enforcement** was toggled off during debugging. Re-enabled on all 3 services after the fix was confirmed.
- **App Check Debug provider** added to `BudgeTrakApplication.onCreate` for debug builds (`BuildConfig.DEBUG`), with Play Integrity for release builds. Debug token registered in Firebase Console.
- **App Check TTL** set to 4 hours (from default 1 hour) to reduce Doze-related token expiry cascades.

## Firestore rules lesson

Every subcollection that `deleteGroup` touches from the client MUST have a matching security rule. If a subcollection has no rule, the catch-all denies access. The Cloud Function uses admin SDK and doesn't need rules, so legacy collections should only be in the Cloud Function's list, not the client-side list.
