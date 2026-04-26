package com.techadvantage.budgetrak.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.techadvantage.budgetrak.data.CryptoHelper
import com.techadvantage.budgetrak.data.SafeIO
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

    // Hard upper bound on the LONGEST edge, overriding the min-dim floor when
    // they conflict. A very tall receipt with a 400px short-edge floor would
    // otherwise grow unbounded on the long edge (e.g. a 1080x15000 screenshot
    // would become 400x5555). Gemini accepts up to ~3072px per edge before
    // the SDK starts tiling/resampling in ways we don't control; beyond that
    // we're paying bandwidth + tokens with no OCR benefit. When this cap
    // fires, the short edge necessarily drops below MIN_IMAGE_DIMENSION —
    // that's the right tradeoff for the rare very-tall-receipt case.
    private const val LONG_EDGE_HARD_CAP = 3072
    // Minimum short-edge floor for full-size receipts. A naïve longest-edge cap
    // crushes tall e-receipt screenshots (e.g. 1080x7785 → 139x1000) into an
    // unreadable blur. Applying a 400px floor on the shorter edge keeps them
    // legible (same input → 400x2884) at the cost of slightly larger files.
    private const val MIN_IMAGE_DIMENSION = 400
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
     * Read a URI's contents as JPEG bytes. For image URIs this is the raw file
     * contents; for PDF URIs, the first page is rasterised (white background,
     * ~1500px long edge) and JPEG-encoded at q=95, then fed through the same
     * downstream resize+compress pipeline as any other image.
     */
    private fun readAsJpegBytes(context: Context, uri: Uri): ByteArray? {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val isPdf = mime == "application/pdf" ||
            (mime.isEmpty() && uri.lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true)
        if (!isPdf) {
            return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { page ->
                        // Render at ~1500px long edge; the main pipeline will then
                        // apply its usual 1000px cap + min-dim floor + JPEG target.
                        val longest = maxOf(page.width, page.height).coerceAtLeast(1)
                        val scale = 1500f / longest
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        // PDFs with transparency render with a black background
                        // unless we fill first; receipts are almost always white.
                        android.graphics.Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null,
                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val bos = java.io.ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                        bmp.recycle()
                        bos.toByteArray()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to render PDF: ${e.message}")
            null
        }
    }

    /**
     * Process a photo from URI: save full (no re-encoding if ≤1000px),
     * resize only if needed (100% quality), generate thumbnail.
     * Accepts PDFs (first page is rasterised). Does NOT encrypt —
     * encryption happens at upload time.
     */
    fun processAndSavePhoto(context: Context, uri: Uri): String? {
        return try {
            val rawBytes = readAsJpegBytes(context, uri) ?: return null

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
                atomicWriteBytes(fullFile, rawBytes)
                bitmapForThumb = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    ?: return null
            } else {
                val original = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    ?: return null
                val bitmap = if (needsResize) resizeBitmap(original, MAX_IMAGE_DIMENSION, MIN_IMAGE_DIMENSION) else original
                val area = bitmap.width.toLong() * bitmap.height
                val target = (area * TARGET_BYTES_PER_MEGAPIXEL / 1_000_000L).toInt()
                compressToTargetSize(bitmap, fullFile, target)
                if (bitmap !== original) original.recycle()
                bitmapForThumb = bitmap
            }

            // Save thumbnail atomically (70% is fine for small preview)
            val thumb = resizeBitmap(bitmapForThumb, THUMBNAIL_SIZE)
            val thumbBuf = java.io.ByteArrayOutputStream()
            thumb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, thumbBuf)
            atomicWriteBytes(getThumbFile(context, receiptId), thumbBuf.toByteArray())
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

    /**
     * Overwrite an existing receipt with a new bitmap (e.g. after rotation).
     * Re-runs the same JPEG-to-target-bytes compression as the initial save,
     * regenerates the thumbnail, and re-adds the receiptId to the pending
     * upload queue so Layer 0 drainer re-uploads the new content and peers
     * pick up the rotation via the ledger `contentVersion` bump. Returns
     * true on success.
     */
    fun replaceReceipt(context: Context, receiptId: String, bitmap: Bitmap): Boolean {
        return try {
            val fullFile = getReceiptFile(context, receiptId)

            // Defense-in-depth: `processAndSavePhoto` already clamps stored
            // receipts to [MAX_IMAGE_DIMENSION, MIN_IMAGE_DIMENSION,
            // LONG_EDGE_HARD_CAP], and rotation preserves pixel count, so the
            // invariant holds for the sole legitimate caller today. Re-apply
            // the clamp anyway so a future caller that passes a fresh or
            // oversized bitmap can't slip past the size contract.
            val bounded = resizeBitmap(bitmap, MAX_IMAGE_DIMENSION, MIN_IMAGE_DIMENSION)
            val area = bounded.width.toLong() * bounded.height
            val targetBytes = (area * TARGET_BYTES_PER_MEGAPIXEL / 1_000_000L).toInt()
            compressToTargetSize(bounded, fullFile, targetBytes)

            val thumb = resizeBitmap(bounded, THUMBNAIL_SIZE)
            val thumbBuf = java.io.ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, 70, thumbBuf)
            atomicWriteBytes(getThumbFile(context, receiptId), thumbBuf.toByteArray())
            if (thumb !== bounded) thumb.recycle()
            if (bounded !== bitmap) bounded.recycle()

            // Bump local content version BEFORE queueing so the upload pipeline
            // can distinguish "rotation pending" (local > cloud) from "resume
            // of a partial commit" (local == cloud, our lastEditBy). Without
            // this, a worker cancellation between ledger write and queue
            // removal would cause the next cycle to false-rotate and fan out
            // a flag-clock bump for unchanged content.
            ReceiptSyncManager.bumpLocalContentVersionForRotation(context, receiptId)

            // Requeue for re-upload so peers get the rotated version. No-op
            // for solo users — their drainer is never kicked (see guards in
            // MainViewModel.kickUploadDrainer).
            addToPendingQueue(context, receiptId)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to replace receipt $receiptId: ${e.message}")
            false
        }
    }

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

            // Save full-size atomically
            val fullFile = getReceiptFile(context, receiptId)
            atomicWriteBytes(fullFile, plaintext)

            // Generate thumbnail from saved image — compress to buffer, write atomically
            val bitmap = BitmapFactory.decodeByteArray(plaintext, 0, plaintext.size)
            if (bitmap != null) {
                val thumb = resizeBitmap(bitmap, THUMBNAIL_SIZE)
                val thumbBuf = java.io.ByteArrayOutputStream()
                thumb.compress(Bitmap.CompressFormat.JPEG, 70, thumbBuf)
                atomicWriteBytes(getThumbFile(context, receiptId), thumbBuf.toByteArray())
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

        // Drop pending-upload entries not attached to any transaction. The queue
        // exists to retry cloud uploads for attached receipts; a never-attached
        // entry (e.g. from the prior multi-photo slot-assignment bug) has no
        // useful destination and would otherwise pin the file here forever.
        val pendingBefore = loadPendingUploads(context)
        val stalePending = pendingBefore - allReceiptIds
        if (stalePending.isNotEmpty()) {
            savePendingUploads(context, pendingBefore - stalePending)
            Log.i(TAG, "Cleaned ${stalePending.size} stale pending uploads")
        }
        val pending = pendingBefore - stalePending

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
    fun collectAllReceiptIds(transactions: List<com.techadvantage.budgetrak.data.Transaction>): Set<String> {
        val ids = mutableSetOf<String>()
        for (t in transactions) {
            // Skip tombstoned transactions. Delete flow should null the
            // receiptIdN fields at delete time (MainActivity onDeleteTransaction),
            // but older tombstones from pre-fix builds still carry dangling
            // refs — filter here so processRecovery doesn't chase blobs that
            // were legitimately deleted weeks ago.
            if (t.deleted) continue
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
    fun getReceiptIds(t: com.techadvantage.budgetrak.data.Transaction): List<String> {
        return listOfNotNull(t.receiptId1, t.receiptId2, t.receiptId3, t.receiptId4, t.receiptId5)
    }

    /**
     * Find the next empty slot index (1-5) for a transaction, or null if full.
     */
    fun nextEmptySlot(t: com.techadvantage.budgetrak.data.Transaction): Int? {
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
    fun clearReceiptSlot(t: com.techadvantage.budgetrak.data.Transaction, receiptId: String): com.techadvantage.budgetrak.data.Transaction {
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
     * Atomic write: bytes go to `<target>.tmp` in the same directory, fsync'd
     * via rename. On mid-write crash, the target is either the old version
     * or fully the new one, never half-written.
     */
    private fun atomicWriteBytes(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                // Fallback: copy (some filesystems block renames across security contexts)
                tmp.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                tmp.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    /**
     * Compress a bitmap to a target file size in bytes.
     * Iterates on the ORIGINAL bitmap (never re-compresses compressed data).
     * Starts at Q=92, uses log-linear interpolation from accumulated data points.
     * Writes atomically via temp-file + rename so a crash mid-write can't
     * leave a truncated JPEG on disk.
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

        if (tryQuality(92)) { atomicWriteBytes(outFile, bestBytes!!); return }
        val secondQ = if (samples[0].second > targetBytes) 50 else 98
        if (tryQuality(secondQ)) { atomicWriteBytes(outFile, bestBytes!!); return }

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
            if (tryQuality(predictedQ.coerceIn(20, 100))) { atomicWriteBytes(outFile, bestBytes!!); return }
        }
        atomicWriteBytes(outFile, bestBytes!!)
    }

    /**
     * Resize a bitmap so the longest edge ≤ [maxDimension].
     * If [minDimension] > 0, also ensure the shortest edge stays ≥ [minDimension] —
     * if the longest-edge scaling would push the shortest edge below the floor,
     * we scale less aggressively (so longest exceeds maxDimension, but shortest
     * hits the floor). Never upscales: if the source shortest edge is already
     * below the floor, the original bitmap is returned unchanged.
     *
     * Called with minDimension=0 for thumbnails (small target, floor irrelevant)
     * and minDimension=MIN_IMAGE_DIMENSION for the full-size receipt path.
     */
    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int, minDimension: Int = 0): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longestEdge = maxOf(width, height)
        val shortestEdge = minOf(width, height)
        if (longestEdge <= maxDimension) return bitmap

        val scaleFromLongest = maxDimension.toFloat() / longestEdge
        var scale: Float = if (minDimension <= 0) {
            scaleFromLongest
        } else {
            val shortestAfter = shortestEdge * scaleFromLongest
            when {
                shortestAfter >= minDimension -> scaleFromLongest
                shortestEdge >= minDimension  -> minDimension.toFloat() / shortestEdge
                else -> 1f  // source shortest already below floor; never upscale
            }
        }
        // Hard cap on longest edge: if the min-dim floor would let the long
        // edge exceed LONG_EDGE_HARD_CAP, override with a tighter scale even
        // though it pushes the short edge below minDimension.
        val longestAfter = longestEdge * scale
        if (longestAfter > LONG_EDGE_HARD_CAP) {
            scale = LONG_EDGE_HARD_CAP.toFloat() / longestEdge
        }
        if (scale >= 1f) return bitmap
        val newWidth = (width * scale).toInt().coerceAtLeast(1)
        val newHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
