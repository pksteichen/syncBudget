const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

/**
 * Cascade cleanup when a group document is deleted (by TTL or manually).
 *
 * Firestore TTL deletes the group doc after 90 days of inactivity, but does
 * NOT auto-delete subcollections, RTDB presence, or Cloud Storage files.
 * This function handles all of that.
 *
 * App-initiated dissolution (deleteGroup() in FirestoreService.kt) already
 * cleans up everything before deleting the group doc, so this function
 * primarily handles TTL-triggered deletions.
 */
exports.cleanupGroupData = functions.firestore
  .document('groups/{groupId}')
  .onDelete(async (snap, context) => {
    const groupId = context.params.groupId;
    const db = admin.firestore();
    const groupRef = db.collection('groups').doc(groupId);

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
          const batch = db.batch();
          for (const doc of docs) {
            batch.delete(doc);
          }
          await batch.commit();
        }
      } while (docs.length >= 500);
    }

    // Delete RTDB presence nodes for entire group
    try {
      await admin.database().ref(`groups/${groupId}`).remove();
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
