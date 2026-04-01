package com.syncbudget.app.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.syncbudget.app.data.CryptoHelper
import com.syncbudget.app.data.SafeIO
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Manages receipt photos locally: capture, downsize, encrypt/decrypt,
 * store, thumbnails, and the pending upload queue.
 */
object ReceiptManager {

    private const val TAG = "ReceiptManager"
    private const val MAX_IMAGE_DIMENSION = 1000
    private const val THUMBNAIL_SIZE = 200
    private const val TARGET_BYTES_PER_MEGAPIXEL = 250 * 1024  // 250KB per 1M pixels
    private const val PENDING_QUEUE_FILE = "pending_receipt_uploads.json"
    private const val RECEIPTS_DIR = "receipts"
    private const val THUMBS_DIR = "receipt_thumbs"

    // ── Pending Upload Queue ────────────────────────────────────

    private val pendingQueueLock = Any()

    fun loadPendingUploads(context: Context): MutableSet<String> {
        synchronized(pendingQueueLock) {
            return try {
                val file = File(context.filesDir, PENDING_QUEUE_FILE)
                if (!file.exists()) return mutableSetOf()
                val json = JSONArray(file.readText())
                (0 until json.length()).mapTo(mutableSetOf()) { json.getString(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load pending uploads: ${e.message}")
                mutableSetOf()
            }
        }
    }

    fun savePendingUploads(context: Context, pendingIds: Set<String>) {
        synchronized(pendingQueueLock) {
            try {
                val json = JSONArray()
                pendingIds.forEach { json.put(it) }
                SafeIO.atomicWriteJson(context, PENDING_QUEUE_FILE, json)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save pending uploads: ${e.message}")
            }
        }
    }

    fun addToPendingQueue(context: Context, receiptId: String) {
        synchronized(pendingQueueLock) {
            val pending = loadPendingUploadsInternal(context)
            pending.add(receiptId)
            savePendingUploadsInternal(context, pending)
        }
    }

    fun removeFromPendingQueue(context: Context, receiptId: String) {
        synchronized(pendingQueueLock) {
            val pending = loadPendingUploadsInternal(context)
            pending.remove(receiptId)
            savePendingUploadsInternal(context, pending)
        }
    }

    private fun loadPendingUploadsInternal(context: Context): MutableSet<String> {
        return try {
            val file = File(context.filesDir, PENDING_QUEUE_FILE)
            if (!file.exists()) return mutableSetOf()
            val json = JSONArray(file.readText())
            (0 until json.length()).mapTo(mutableSetOf()) { json.getString(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load pending uploads: ${e.message}")
            mutableSetOf()
        }
    }

    private fun savePendingUploadsInternal(context: Context, pendingIds: Set<String>) {
        try {
            val json = JSONArray()
            pendingIds.forEach { json.put(it) }
            SafeIO.atomicWriteJson(context, PENDING_QUEUE_FILE, json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save pending uploads: ${e.message}")
        }
    }

    // ── Receipt File Management ─────────────────────────────────

    fun generateReceiptId(): String = UUID.randomUUID().toString()

    fun getReceiptDir(context: Context): File {
        val dir = File(context.filesDir, RECEIPTS_DIR)
        dir.mkdirs()
        return dir
    }

    fun getThumbDir(context: Context): File {
        val dir = File(context.filesDir, THUMBS_DIR)
        dir.mkdirs()
        return dir
    }

    fun getReceiptFile(context: Context, receiptId: String): File =
        File(getReceiptDir(context), "$receiptId.jpg")

    fun getThumbFile(context: Context, receiptId: String): File =
        File(getThumbDir(context), "$receiptId.jpg")

    fun hasLocalFile(context: Context, receiptId: String): Boolean =
        getReceiptFile(context, receiptId).exists()

    /**
     * Process a photo from URI: save full (no re-encoding if ≤1000px),
     * resize only if needed (100% quality), generate thumbnail.
     * Does NOT encrypt — encryption happens at upload time.
     */
    fun processAndSavePhoto(context: Context, uri: Uri): String? {
        return try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null

            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

            val needsResize = opts.outWidth > MAX_IMAGE_DIMENSION || opts.outHeight > MAX_IMAGE_DIMENSION
            val receiptId = generateReceiptId()
            val fullFile = getReceiptFile(context, receiptId)

            val pixelArea = opts.outWidth.toLong() * opts.outHeight
            val targetBytes = (pixelArea * TARGET_BYTES_PER_MEGAPIXEL / 1_000_000L).toInt()
            val bitmapForThumb: android.graphics.Bitmap
            if (!needsResize && rawBytes.size <= targetBytes) {
                fullFile.writeBytes(rawBytes)
                bitmapForThumb = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    ?: return null
            } else {
                val original = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    ?: return null
                val bitmap = if (needsResize) resizeBitmap(original, MAX_IMAGE_DIMENSION) else original
                val area = bitmap.width.toLong() * bitmap.height
                val target = (area * TARGET_BYTES_PER_MEGAPIXEL / 1_000_000L).toInt()
                compressToTargetSize(bitmap, fullFile, target)
                if (bitmap !== original) original.recycle()
                bitmapForThumb = bitmap
            }

            // Save thumbnail (70% is fine for small preview)
            val thumb = resizeBitmap(bitmapForThumb, THUMBNAIL_SIZE)
            getThumbFile(context, receiptId).outputStream().use { out ->
                thumb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
            }
            if (thumb !== bitmapForThumb) bitmapForThumb.recycle()

            receiptId
        } catch (e: Exception) {
            Log.w(TAG, "Failed to process image: ${e.message}")
            null
        }
    }

    /**
     * Process a photo from camera capture temp file URI.
     */
    fun processAndSaveFromCamera(context: Context, tempUri: Uri): String? =
        processAndSavePhoto(context, tempUri)

    fun loadThumbnail(context: Context, receiptId: String): Bitmap? {
        return try {
            val file = getThumbFile(context, receiptId)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) { null }
    }

    fun loadFullImage(context: Context, receiptId: String): Bitmap? {
        return try {
            val file = getReceiptFile(context, receiptId)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) { null }
    }

    /**
     * Delete local receipt file + thumbnail.
     */
    fun deleteLocalReceipt(context: Context, receiptId: String) {
        getReceiptFile(context, receiptId).delete()
        getThumbFile(context, receiptId).delete()
    }

    /**
     * Full receipt cleanup: local file + thumbnail + Cloud Storage + ledger entry.
     * Call when a user explicitly removes a receipt from a transaction.
     */
    suspend fun deleteReceiptFull(context: Context, receiptId: String) {
        // Local cleanup
        deleteLocalReceipt(context, receiptId)
        removeFromPendingQueue(context, receiptId)
        // Cloud cleanup (groupId from prefs)
        val groupId = context.getSharedPreferences("sync_engine", android.content.Context.MODE_PRIVATE)
            .getString("groupId", null) ?: return
        try {
            ImageLedgerService.deleteFromCloud(groupId, receiptId)
        } catch (_: Exception) {}
        try {
            ImageLedgerService.deleteLedgerEntry(groupId, receiptId)
        } catch (_: Exception) {}
        try {
            ImageLedgerService.bumpFlagClock(groupId)
        } catch (_: Exception) {}
    }

    // ── Encryption for Cloud Upload ─────────────────────────────

    /**
     * Read receipt file, encrypt with the sync key, return encrypted bytes.
     */
    fun encryptForUpload(context: Context, receiptId: String, key: ByteArray): ByteArray? {
        return try {
            val file = getReceiptFile(context, receiptId)
            if (!file.exists()) return null
            val plaintext = file.readBytes()
            CryptoHelper.encryptWithKey(plaintext, key)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encrypt receipt $receiptId: ${e.message}")
            null
        }
    }

    /**
     * Decrypt downloaded receipt data, save to local storage + generate thumbnail.
     */
    fun decryptAndSave(context: Context, receiptId: String, encryptedData: ByteArray, key: ByteArray): Boolean {
        return try {
            val plaintext = CryptoHelper.decryptWithKey(encryptedData, key)

            // Save full-size
            val fullFile = getReceiptFile(context, receiptId)
            fullFile.writeBytes(plaintext)

            // Generate thumbnail from saved image
            val bitmap = BitmapFactory.decodeByteArray(plaintext, 0, plaintext.size)
            if (bitmap != null) {
                val thumb = resizeBitmap(bitmap, THUMBNAIL_SIZE)
                getThumbFile(context, receiptId).outputStream().use { out ->
                    thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
                }
                if (thumb !== bitmap) bitmap.recycle()
            }

            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt receipt $receiptId: ${e.message}")
            false
        }
    }

    // ── Storage Size ────────────────────────────────────────────

    /**
     * Calculate total size of local receipt storage (receipts + thumbnails).
     */
    fun getTotalStorageBytes(context: Context): Long {
        val receiptsSize = getReceiptDir(context).listFiles()?.sumOf { it.length() } ?: 0L
        val thumbsSize = getThumbDir(context).listFiles()?.sumOf { it.length() } ?: 0L
        return receiptsSize + thumbsSize
    }

    // ── Orphan Scan ─────────────────────────────────────────────

    /**
     * Delete local receipt files that are not referenced by any transaction.
     */
    fun cleanOrphans(context: Context, allReceiptIds: Set<String>) {
        val receiptDir = getReceiptDir(context)
        val thumbDir = getThumbDir(context)
        val pending = loadPendingUploads(context)

        receiptDir.listFiles()?.forEach { file ->
            val id = file.nameWithoutExtension
            if (id !in allReceiptIds && id !in pending) {
                file.delete()
                File(thumbDir, file.name).delete()
                Log.d(TAG, "Cleaned orphan receipt: $id")
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Collect all receiptIds referenced by transactions (non-null slots).
     */
    fun collectAllReceiptIds(transactions: List<com.syncbudget.app.data.Transaction>): Set<String> {
        val ids = mutableSetOf<String>()
        for (t in transactions) {
            t.receiptId1?.let { ids.add(it) }
            t.receiptId2?.let { ids.add(it) }
            t.receiptId3?.let { ids.add(it) }
            t.receiptId4?.let { ids.add(it) }
            t.receiptId5?.let { ids.add(it) }
        }
        return ids
    }

    /**
     * Get the list of receiptIds for a transaction (non-null only).
     */
    fun getReceiptIds(t: com.syncbudget.app.data.Transaction): List<String> {
        return listOfNotNull(t.receiptId1, t.receiptId2, t.receiptId3, t.receiptId4, t.receiptId5)
    }

    /**
     * Find the next empty slot index (1-5) for a transaction, or null if full.
     */
    fun nextEmptySlot(t: com.syncbudget.app.data.Transaction): Int? {
        if (t.receiptId1 == null) return 1
        if (t.receiptId2 == null) return 2
        if (t.receiptId3 == null) return 3
        if (t.receiptId4 == null) return 4
        if (t.receiptId5 == null) return 5
        return null
    }

    /**
     * Clear a specific receiptId from whichever slot it occupies on a transaction.
     * Returns the updated transaction, or the original if the receiptId wasn't found.
     */
    fun clearReceiptSlot(t: com.syncbudget.app.data.Transaction, receiptId: String): com.syncbudget.app.data.Transaction {
        return when (receiptId) {
            t.receiptId1 -> t.copy(receiptId1 = null)
            t.receiptId2 -> t.copy(receiptId2 = null)
            t.receiptId3 -> t.copy(receiptId3 = null)
            t.receiptId4 -> t.copy(receiptId4 = null)
            t.receiptId5 -> t.copy(receiptId5 = null)
            else -> t
        }
    }

    /**
     * Compress a bitmap to a target file size in bytes.
     * Iterates on the ORIGINAL bitmap (never re-compresses compressed data).
     * Starts at Q=92, uses log-linear interpolation from accumulated data points.
     */
    private fun compressToTargetSize(bitmap: Bitmap, outFile: File, targetBytes: Int) {
        val minTarget = (targetBytes * 0.9).toInt()
        val maxTarget = (targetBytes * 1.1).toInt()
        val samples = mutableListOf<Pair<Int, Int>>()
        var bestBytes: ByteArray? = null
        var bestDistance = Int.MAX_VALUE

        fun tryQuality(q: Int): Boolean {
            if (samples.any { it.first == q }) return false
            val buf = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, q, buf)
            val size = buf.size()
            samples.add(q to size)
            val dist = kotlin.math.abs(size - targetBytes)
            if (dist < bestDistance) { bestDistance = dist; bestBytes = buf.toByteArray() }
            return size in minTarget..maxTarget
        }

        if (tryQuality(92)) { outFile.writeBytes(bestBytes!!); return }
        val secondQ = if (samples[0].second > targetBytes) 50 else 98
        if (tryQuality(secondQ)) { outFile.writeBytes(bestBytes!!); return }

        for (round in 0 until 3) {
            val below = samples.filter { it.second <= targetBytes }.maxByOrNull { it.second }
            val above = samples.filter { it.second > targetBytes }.minByOrNull { it.second }
            val predictedQ = if (below != null && above != null) {
                val lnT = kotlin.math.ln(targetBytes.toDouble())
                val lnL = kotlin.math.ln(below.second.toDouble())
                val lnH = kotlin.math.ln(above.second.toDouble())
                val d = lnH - lnL
                if (d > 0.001) (((lnT - lnL) / d) * (above.first - below.first) + below.first).toInt()
                else (below.first + above.first) / 2
            } else {
                val s = samples.sortedBy { it.first }
                (s.last().first * (targetBytes.toDouble() / s.last().second)).toInt()
            }
            if (tryQuality(predictedQ.coerceIn(20, 100))) { outFile.writeBytes(bestBytes!!); return }
        }
        outFile.writeBytes(bestBytes!!)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
