---
name: Firestore-native sync (implemented)
description: Project status — the hand-rolled CRDT was replaced with Firestore-native per-field encryption in v2.1. Current operational detail lives in MEMORY.md SYNC section.
type: project
---

## Status
Complete. Shipped as v2.1 (Firestore-native, per-field encryption) and refined through v2.6 (echo suppression, consistency check, solo-user fixes). Operational detail — listener lifecycle, consistency check, echo suppression, 3-tier background worker, per-collection cursors, enc-hash cache — lives in the "SYNC" section of `MEMORY.md`. Key-files map is in `architecture.md`.

## What was replaced
The hand-rolled CRDT sync (~4,000 lines: LamportClock, DeltaBuilder, DeltaSerializer, CrdtMerge, SnapshotManager, SyncEngine, IntegrityChecker) was removed on the `firestore-native-sync` branch and merged 2026-03-23. Replacement in `data/sync/` is ~1,500 lines.

## Core design choices
- **Per-field encryption** — each business field stored as `enc_fieldName` (ChaCha20-Poly1305 via `CryptoHelper.encryptWithKey`). Metadata fields (`deviceId`, `updatedAt`, `deleted`, `lastEditBy`) plaintext.
- **Field-level updates** — `FirestoreDocSync.pushRecord` diffs against `lastKnownState` and pushes only changed `enc_*` fields via Firestore `update()`. New docs use `set()`.
- **Filtered listeners** — `addSnapshotListener` with `whereGreaterThan("updatedAt", cursor)` per collection. Per-collection cursors in SharedPrefs (global cursors caused drop-outs when partial deliveries got interrupted by process death).
- **Conflict detection** — `lastEditBy` plaintext field + `localPendingEdits` persisted in SharedPrefs. Different-field concurrent edits converge naturally (merge both). Same-field conflicts flag `isUserCategorized = false` for user review.
- **No clocks on data classes** — `deviceId` and `deleted` are the only sync metadata fields. Tombstones via `deleted=true`.

## Why it matters now
- A future agent might find a `CrdtMerge` reference or similar in old memory/specs and assume it still exists. It doesn't.
- The `.active` filter now checks content (`source.isNotEmpty()`), not clock values — solo users who never synced have clock 0 on every record, and their data is valid.
- The client dissolution path deletes only the group doc + own member; the `cleanupGroupData` Cloud Function cascades the rest. The client subcollection list must match existing Firestore security rules exactly (see `project_dissolve_bug_2026_04_12.md`).

## Tag history
- v2.1 — Firestore-native per-field encryption landed.
- v2.2 — enc-hash cache, cursor persistence, echo optimizations.
- v2.3 — save-function consolidation, audit fixes.
- v2.4 — RTDB presence, filtered listeners, App Check token refresh coverage.
- v2.5 — async loading + Back=Home + maintenance consolidation.
- v2.6 — consistency check (Layer 1 counts + Layer 2 cashHash majority vote), background echo suppression, solo-user gating.
