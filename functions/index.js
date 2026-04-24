const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

const db = () => admin.firestore();
const rtdb = () => admin.database();
const fcm = () => admin.messaging();

// Collections whose writes should propagate to other devices via sync_push.
const SYNC_PUSH_COLLECTIONS = new Set([
  'transactions',
  'recurringExpenses',
  'incomeSources',
  'savingsGoals',
  'amortizationEntries',
  'categories',
  'periodLedger',
  'sharedSettings',
]);

// ─── 1. Cascade cleanup on group doc delete ───────────────────────────

/**
 * Cascade cleanup when a group document is deleted (by TTL or manually).
 *
 * Firestore TTL deletes the group doc after 90 days of inactivity, but does
 * NOT auto-delete subcollections, RTDB presence, or Cloud Storage files.
 * This function handles all of that.
 */
exports.cleanupGroupData = functions.firestore
  .document('groups/{groupId}')
  .onDelete(async (snap, context) => {
    const groupId = context.params.groupId;
    const groupRef = db().collection('groups').doc(groupId);

    // Delete all Firestore subcollections (paginated for large collections)
    const subcollections = [
      'transactions', 'recurringExpenses', 'incomeSources',
      'savingsGoals', 'amortizationEntries', 'categories',
      'periodLedger', 'sharedSettings',
      'devices', 'members', 'imageLedger', 'adminClaim',
      'deltas', 'snapshots'  // Legacy CRDT (may exist on old groups)
    ];

    for (const sub of subcollections) {
      let docs;
      do {
        docs = await groupRef.collection(sub).listDocuments({ pageSize: 500 });
        if (docs.length > 0) {
          const batch = db().batch();
          for (const doc of docs) batch.delete(doc);
          await batch.commit();
        }
      } while (docs.length >= 500);
    }

    // Delete RTDB presence nodes for entire group
    try {
      await rtdb().ref(`groups/${groupId}`).remove();
    } catch (e) {
      console.warn(`RTDB cleanup failed for ${groupId}: ${e.message}`);
    }

    // Delete Cloud Storage files (receipts + snapshot archive)
    try {
      const bucket = admin.storage().bucket();
      const [files] = await bucket.getFiles({ prefix: `groups/${groupId}/` });
      const deletePromises = files.map(file => file.delete().catch(() => {}));
      await Promise.all(deletePromises);
    } catch (e) {
      console.warn(`Storage cleanup failed for ${groupId}: ${e.message}`);
    }

    console.log(`Cleaned up group ${groupId}: subcollections, RTDB, and Storage`);
  });

// ─── 2. Event-driven sync_push on data writes ────────────────────────

/**
 * On every write to a sync data collection, fan-out a high-priority FCM
 * `sync_push` message to every other group device. Purpose: wake devices
 * whose process is killed by Doze / App-Standby so their listeners re-pull
 * the change in near-real-time instead of waiting for the next periodic
 * WorkManager run (which OEMs defer for hours on idle devices).
 *
 * The writer is skipped via the `lastEditBy` field.
 *
 * Client-side, each FCM enqueues BackgroundSyncWorker.runOnce() via
 * enqueueUniqueWork(KEEP), so bursts (e.g. 500-txn CSV imports) collapse
 * to a single sync run per recipient device.
 */
exports.onSyncDataWrite = functions.firestore
  .document('groups/{groupId}/{collection}/{docId}')
  .onWrite(async (change, context) => {
    const { groupId, collection } = context.params;
    if (!SYNC_PUSH_COLLECTIONS.has(collection)) return;
    if (!change.after.exists) return;  // skip deletes — tombstone already propagated by listener

    const after = change.after.data() || {};
    const writerDeviceId = after.lastEditBy || after.deviceId || null;

    const tokens = await collectRecipientTokens(groupId, writerDeviceId);
    if (tokens.length === 0) return;

    await sendFcm(tokens, { type: 'sync_push', collection, groupId }, 'sync_push');
  });

// ─── 2b. Event-driven sync_push on meaningful imageLedger writes ──────
//
// imageLedger is intentionally NOT in SYNC_PUSH_COLLECTIONS because most
// of its writes are bookkeeping chatter that doesn't need to wake peers:
// markPossession, markNonPossession, pruneCheckTransaction deletions, etc.
// This trigger fires `sync_push` only on the writes that change what peers
// see and need to react to:
//
//   - Rotation / edit: contentVersion increased.
//   - Recovery re-upload complete: uploadedAt went 0 → >0.
//   - Recovery request created: new entry with uploadedAt === 0 (peers with
//     the file should consider re-uploading).
//
// Fresh uploads (createLedgerEntry for a brand-new receipt) are NOT pushed
// here — the concurrent write to the `transactions` collection (via
// SyncWriteHelper.pushTransaction on saveTransactions) already fires a
// sync_push that wakes peers, and their onBatchChanged fast-path downloads
// the photo via Cloud Storage.
//
// Client writes carry `lastEditBy` so the Cloud Function skips the writer.

exports.onImageLedgerWrite = functions.firestore
  .document('groups/{groupId}/imageLedger/{receiptId}')
  .onWrite(async (change, context) => {
    const { groupId, receiptId } = context.params;

    // Skip snapshot-request doc — it uses its own signaling path.
    if (receiptId === '_snapshot_request') return;

    const before = change.before.exists ? change.before.data() : null;
    const after  = change.after.exists  ? change.after.data()  : null;
    if (!after) return;  // deletion handled by transaction-sync path

    const beforeVer = (before && before.contentVersion) || 0;
    const afterVer  = after.contentVersion || 0;
    const beforeUp  = (before && before.uploadedAt) || 0;
    const afterUp   = after.uploadedAt || 0;

    const isRotation         = before && afterVer > beforeVer;
    const isRecoveryComplete = before && beforeUp === 0 && afterUp > 0;
    const isRecoveryRequest  = !before && afterUp === 0;

    if (!isRotation && !isRecoveryComplete && !isRecoveryRequest) return;

    const writerDeviceId = after.lastEditBy || after.originatorDeviceId || null;
    const tokens = await collectRecipientTokens(groupId, writerDeviceId);
    if (tokens.length === 0) return;

    await sendFcm(
      tokens,
      { type: 'sync_push', collection: 'imageLedger', groupId },
      'sync_push_ledger'
    );
  });

// ─── 3. Heartbeat: wake devices whose RTDB presence has gone stale ───

const HEARTBEAT_STALE_MS = 15 * 60 * 1000;  // 15 min

/**
 * Every 15 minutes, walk active groups and wake any device whose RTDB
 * presence `lastSeen` is older than HEARTBEAT_STALE_MS. This catches
 * devices that Android has thrown into the `rare` or `restricted`
 * App-Standby Bucket so their periodic worker has stopped firing —
 * our own 2026-04-12 dump showed 4h46m silence on one device.
 */
exports.presenceHeartbeat = functions.pubsub
  .schedule('every 15 minutes')
  .timeZone('UTC')
  .onRun(async () => {
    const nowMs = Date.now();
    const cutoff = nowMs - HEARTBEAT_STALE_MS;

    const groupsSnap = await db().collection('groups').get();
    let wakeCount = 0;

    for (const groupDoc of groupsSnap.docs) {
      const groupId = groupDoc.id;
      const presenceSnap = await rtdb().ref(`groups/${groupId}/presence`).once('value');
      const presence = presenceSnap.val() || {};

      const staleDeviceIds = Object.entries(presence)
        .filter(([, rec]) => (rec && rec.lastSeen && rec.lastSeen < cutoff))
        .map(([deviceId]) => deviceId);

      if (staleDeviceIds.length === 0) continue;

      const tokens = await tokensForDevices(groupId, staleDeviceIds);
      if (tokens.length === 0) continue;

      await sendFcm(tokens, { type: 'heartbeat', groupId }, 'heartbeat');
      wakeCount += tokens.length;
    }

    console.log(`presenceHeartbeat: woke ${wakeCount} stale device(s)`);
    return null;
  });

// ─── Helpers ─────────────────────────────────────────────────────────

async function collectRecipientTokens(groupId, writerDeviceId) {
  const devicesSnap = await db()
    .collection(`groups/${groupId}/devices`)
    .get();

  // Defense-in-depth membership check (option A — free because we already
  // read the devices collection). Firestore security rules are the
  // authoritative gate; if they're ever misconfigured and a write slips
  // through from a device that isn't a current group member (or was
  // removed), suppress the fan-out so a rule regression can't be amplified
  // into a group-wide FCM spam. Writer membership is validated against the
  // same snapshot — no extra read.
  if (writerDeviceId) {
    const writerDoc = devicesSnap.docs.find(d => d.id === writerDeviceId);
    if (!writerDoc || writerDoc.data()?.removed === true) {
      console.warn(
        `Fan-out suppressed: writer ${writerDeviceId} is not a current member of ${groupId}`
      );
      return [];
    }
  }

  const tokens = [];
  devicesSnap.forEach(doc => {
    if (doc.id === writerDeviceId) return;
    const d = doc.data() || {};
    if (d.removed === true) return;
    if (typeof d.fcmToken === 'string' && d.fcmToken.length > 0) {
      tokens.push(d.fcmToken);
    }
  });
  return tokens;
}

async function tokensForDevices(groupId, deviceIds) {
  const tokens = [];
  for (const deviceId of deviceIds) {
    const snap = await db().doc(`groups/${groupId}/devices/${deviceId}`).get();
    if (!snap.exists) continue;
    const d = snap.data() || {};
    if (d.removed === true) continue;
    if (typeof d.fcmToken === 'string' && d.fcmToken.length > 0) {
      tokens.push(d.fcmToken);
    }
  }
  return tokens;
}

async function sendFcm(tokens, data, label) {
  // sendEachForMulticast returns a BatchResponse with per-token results.
  // We use sendMulticast if available (up to 500 tokens), otherwise chunk.
  const stringified = {};
  for (const [k, v] of Object.entries(data)) stringified[k] = String(v);

  const CHUNK = 500;
  for (let i = 0; i < tokens.length; i += CHUNK) {
    const batch = tokens.slice(i, i + CHUNK);
    try {
      const res = await fcm().sendEachForMulticast({
        tokens: batch,
        data: stringified,
        android: { priority: 'high' },
      });
      if (res.failureCount > 0) {
        console.warn(`${label}: ${res.failureCount}/${batch.length} FCM sends failed`);
      }
    } catch (e) {
      console.error(`${label}: FCM send batch failed: ${e.message}`);
    }
  }
}
