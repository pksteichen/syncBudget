const functions = require('firebase-functions');
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');
const { google } = require('googleapis');
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

// ─── 4. Weekly orphan-presence cleanup ────────────────────────────────
//
// RTDB presence rules (`database.rules.json`) allow any authenticated user
// to write to `groups/$gid/presence/$deviceId` — they can't cross-reference
// Firestore to enforce group membership. That means a malicious or
// malformed client could inject fake presence entries for arbitrary
// (groupId, deviceId) pairs. This can't escalate to FCM spam
// (`tokensForDevices` looks up Firestore `devices/{id}/fcmToken`, which
// requires `isMember` to write), but it bloats the RTDB presence node
// and slows `RealtimePresenceService.getDevices()` for legitimate users.
//
// This weekly sweep walks every group's presence entries, checks that a
// matching `devices/{deviceId}` exists in Firestore (with `removed` not
// true), and removes any orphan RTDB entries. Runs Sunday 03:00 UTC —
// low-traffic window; weekly cadence keeps cost negligible.
//
// Scale note: sequential walk has the same O(n) scaling concern as
// `presenceHeartbeat`. Fine at the current scale (< 1K groups); tracked
// alongside the heartbeat scaling TODO in `project_prelaunch_todo.md`.

exports.presenceOrphanCleanup = functions.pubsub
  .schedule('every sunday 03:00')
  .timeZone('UTC')
  .onRun(async () => {
    const groupsSnap = await db().collection('groups').get();
    let totalPruned = 0;
    let groupsChecked = 0;

    for (const groupDoc of groupsSnap.docs) {
      const groupId = groupDoc.id;
      try {
        const presenceSnap = await rtdb().ref(`groups/${groupId}/presence`).once('value');
        const presence = presenceSnap.val() || {};
        const presentIds = Object.keys(presence);
        if (presentIds.length === 0) continue;

        // Bulk-fetch device docs so N presence entries cost N reads at most
        // (no retry cost for missing docs — getAll returns non-existent snapshots).
        const deviceRefs = presentIds.map(id =>
          db().doc(`groups/${groupId}/devices/${id}`)
        );
        const deviceDocs = await db().getAll(...deviceRefs);

        const removals = [];
        for (let i = 0; i < presentIds.length; i++) {
          const deviceId = presentIds[i];
          const devDoc = deviceDocs[i];
          const isLegit = devDoc.exists && devDoc.data()?.removed !== true;
          if (!isLegit) {
            removals.push(rtdb().ref(`groups/${groupId}/presence/${deviceId}`).remove());
            console.log(`presenceOrphanCleanup: pruning ${groupId}/${deviceId}`);
          }
        }
        if (removals.length > 0) {
          await Promise.all(removals);
          totalPruned += removals.length;
        }
        groupsChecked++;
      } catch (e) {
        console.warn(`presenceOrphanCleanup: group ${groupId} failed: ${e.message}`);
      }
    }

    console.log(
      `presenceOrphanCleanup: checked ${groupsChecked} group(s), pruned ${totalPruned} orphan presence entrie(s)`
    );
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

// ─── 5. verifyPurchase — server-authoritative entitlement check ──────
//
// Gen 2 callable. Runs as `play-publisher@sync-23ce9.iam.gserviceaccount.com`
// (granted "View financial data" in Play Console), so it can hit the Play
// Developer API with ADC — no key file, no Secret Manager lookup.
//
// Why this exists: the device's local BillingClient.queryPurchasesAsync()
// reads the Play Store app's per-device cache, which lags refunds by 24h+
// for Console-admin refunds (real users on the in-app refund path
// propagate within minutes — different pipeline). The Play Developer API
// reads Google's authoritative purchase ledger, which reflects refunds
// far faster. The Android client calls this function after every Layer 1
// query and treats the response as authoritative, falling back to its
// last cached server result only when the function call itself fails.
//
// Closes the refund-lag bug. Does NOT close the decompile-and-forge bug
// (attacker could just skip the function call) — that needs an
// App-Check-gated Firestore entitlement doc as a follow-up.
//
// App Check is enforced; a fresh debug install must have a registered
// debug token in Firebase Console → App Check → Apps.

const PACKAGE_NAME = 'com.techadvantage.budgetrak';

// Subscription states where the user is currently entitled. CANCELED means
// the user turned off auto-renew but the current paid period is still
// running — we honor that until expiryTime passes.
const SUB_ENTITLED_STATES = new Set([
  'SUBSCRIPTION_STATE_ACTIVE',
  'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
  'SUBSCRIPTION_STATE_CANCELED',
]);

// Lazy-init: keeps cold-start cost off non-billing function invocations.
let _androidpublisher = null;
function androidpublisher() {
  if (_androidpublisher) return _androidpublisher;
  const auth = new google.auth.GoogleAuth({
    scopes: ['https://www.googleapis.com/auth/androidpublisher'],
  });
  _androidpublisher = google.androidpublisher({ version: 'v3', auth });
  return _androidpublisher;
}

exports.verifyPurchase = onCall(
  {
    region: 'us-central1',
    enforceAppCheck: true,
    serviceAccount: 'play-publisher@sync-23ce9.iam.gserviceaccount.com',
  },
  async (request) => {
    const { purchaseToken, productId, productType } = request.data || {};

    if (typeof purchaseToken !== 'string' || purchaseToken.length === 0) {
      throw new HttpsError('invalid-argument', 'purchaseToken required');
    }
    if (typeof productId !== 'string' || productId.length === 0) {
      throw new HttpsError('invalid-argument', 'productId required');
    }
    if (productType !== 'inapp' && productType !== 'subs') {
      throw new HttpsError('invalid-argument', "productType must be 'inapp' or 'subs'");
    }

    const ap = androidpublisher();

    try {
      if (productType === 'inapp') {
        const res = await ap.purchases.products.get({
          packageName: PACKAGE_NAME,
          productId,
          token: purchaseToken,
        });
        const d = res.data || {};
        // purchaseState: 0=Purchased, 1=Canceled, 2=Pending
        const verified = d.purchaseState === 0;
        return {
          verified,
          productType: 'inapp',
          purchaseState: d.purchaseState ?? null,
          acknowledgementState: d.acknowledgementState ?? null,
          purchaseTimeMillis: d.purchaseTimeMillis
            ? Number(d.purchaseTimeMillis)
            : null,
          orderId: d.orderId ?? null,
        };
      }

      // SUBS — use subscriptionsv2 (current API; v1 is deprecated).
      const res = await ap.purchases.subscriptionsv2.get({
        packageName: PACKAGE_NAME,
        token: purchaseToken,
      });
      const d = res.data || {};
      const state = d.subscriptionState || null;
      const lineItem = Array.isArray(d.lineItems) && d.lineItems.length > 0
        ? d.lineItems[0]
        : null;
      // expiryTime is ISO-8601 (e.g. "2026-06-10T15:49:30Z")
      const expiryTimeStr = lineItem ? lineItem.expiryTime : null;
      const expiryTimeMillis = expiryTimeStr
        ? Date.parse(expiryTimeStr)
        : null;

      const stateValid = state && SUB_ENTITLED_STATES.has(state);
      const notExpired = expiryTimeMillis
        ? expiryTimeMillis > Date.now()
        : false;
      const verified = stateValid && notExpired;

      return {
        verified,
        productType: 'subs',
        subscriptionState: state,
        expiryTimeMillis,
        orderId: d.latestOrderId ?? null,
      };
    } catch (e) {
      // Play returns 410 GONE for tokens that have been refunded/canceled
      // and garbage-collected from the active store. Definitive
      // "not entitled" — return verified:false rather than throwing.
      const status = (e.response && e.response.status) || e.code || null;
      if (status === 410) {
        return {
          verified: false,
          productType,
          purchaseState: null,
          reason: 'GONE',
        };
      }
      // 404 NOT FOUND can occur if the package/product/token is invalid for
      // this Play Console account. Surface as not-entitled rather than 5xx
      // — protects against a fake token attempt.
      if (status === 404) {
        return {
          verified: false,
          productType,
          purchaseState: null,
          reason: 'NOT_FOUND',
        };
      }
      console.error(
        `verifyPurchase failed (productType=${productType}, productId=${productId}): ${e.message} status=${status}`
      );
      throw new HttpsError('internal', `Play API error: ${e.message}`);
    }
  }
);
