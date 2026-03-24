package com.syncbudget.app.data.sync

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Low-level Firestore operations for the native-doc sync system.
 *
 * Each synced record lives at:
 *   groups/{groupId}/{collection}/{docId}
 *
 * This service handles writes, batch writes, reads, and snapshot listeners.
 * It does NOT handle encryption — that is EncryptedDocSerializer's job.
 */
object FirestoreDocService {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val TAG = "FirestoreDocService"
    private const val OP_TIMEOUT_MS = 30_000L

    // ── paths ───────────────────────────────────────────────────────────

    private fun collectionRef(groupId: String, collection: String) =
        db.collection("groups").document(groupId).collection(collection)

    private fun docRef(groupId: String, collection: String, docId: String) =
        collectionRef(groupId, collection).document(docId)

    // ── single-doc write ────────────────────────────────────────────────

    /**
     * Write (or overwrite) a single document.
     * [data] should come from EncryptedDocSerializer.xxxToDoc().
     */
    suspend fun writeDoc(
        groupId: String,
        collection: String,
        docId: String,
        data: Map<String, Any>
    ) {
        withTimeout(OP_TIMEOUT_MS) {
            docRef(groupId, collection, docId).set(data).await()
        }
    }

    // ── batch write (for migration) ─────────────────────────────────────

    /**
     * Write multiple documents in batches of up to 500 (Firestore limit).
     * Each pair is (docId, data).
     */
    suspend fun writeBatch(
        groupId: String,
        collection: String,
        docs: List<Pair<String, Map<String, Any>>>
    ) {
        for (chunk in docs.chunked(500)) {
            val batch = db.batch()
            for ((docId, data) in chunk) {
                batch.set(docRef(groupId, collection, docId), data)
            }
            withTimeout(OP_TIMEOUT_MS) {
                batch.commit().await()
            }
        }
    }

    // ── read all docs in a collection ───────────────────────────────────

    /**
     * Read every document in a collection. Used for migration verification
     * and initial bootstrap when listeners haven't populated local state yet.
     */
    suspend fun readAllDocs(
        groupId: String,
        collection: String
    ): List<DocumentSnapshot> {
        return withTimeout(OP_TIMEOUT_MS) {
            collectionRef(groupId, collection).get().await().documents
        }
    }

    // ── snapshot listeners ──────────────────────────────────────────────

    /**
     * Listen for changes in a collection (transactions, categories, etc.).
     * Fires immediately with current state, then on every data change.
     * Echo prevention is handled by FirestoreDocSync's recentPushes set.
     */
    fun listenToCollection(
        groupId: String,
        collection: String,
        onDocumentChange: (List<DocumentChange>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return collectionRef(groupId, collection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listener error on $collection", error)
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    onDocumentChange(snapshot.documentChanges)
                }
            }
    }

    /**
     * Listen for changes to a single document (e.g. sharedSettings/current).
     */
    fun listenToDocument(
        groupId: String,
        collection: String,
        docId: String,
        onChange: (DocumentSnapshot) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return docRef(groupId, collection, docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listener error on $collection/$docId", error)
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    onChange(snapshot)
                }
            }
    }

    // ── delete ──────────────────────────────────────────────────────────

    /**
     * Hard-delete a document. Used for cleanup, NOT for normal record deletion
     * (which uses tombstones via deleted=true).
     */
    suspend fun deleteDoc(groupId: String, collection: String, docId: String) {
        withTimeout(OP_TIMEOUT_MS) {
            docRef(groupId, collection, docId).delete().await()
        }
    }
}
