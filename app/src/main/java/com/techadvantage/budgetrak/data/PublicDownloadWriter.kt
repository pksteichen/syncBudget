package com.techadvantage.budgetrak.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/**
 * Writes files to public Download/ subdirectories with orphan-safe fallback.
 *
 * On a fresh install, direct File API writes succeed and produce canonical filenames.
 * On reinstall, scoped storage refuses File API writes to files left by the previous
 * install ("orphans") with EACCES — the new install doesn't inherit ownership. In that
 * case, MediaStore.insert() auto-suffixes (" (1)", " (2)", ...) around the orphan and
 * the new file is owned by the current install. We cache its actual on-disk path under
 * a `relSubdir/fileName` key so future writes under the same logical name go straight
 * there instead of repeatedly creating fresh `(N)`-suffixed files.
 *
 * Trade-off: a single `(N)` suffix may appear after a reinstall, then stays stable for
 * the life of that install. No system delete-confirmation dialog is ever shown.
 */
object PublicDownloadWriter {

    private const val TAG = "PublicDownloadWriter"
    private const val PREFS = "public_download_writer"

    /**
     * Truncate-write [bytes] to `Download/[relSubdir]/[fileName]`. Returns the actual
     * on-disk File written (may be auto-suffixed); null on hard failure.
     *
     * @param relSubdir e.g. "BudgeTrak/support" — no leading or trailing slashes.
     */
    fun writeBytes(
        context: Context,
        relSubdir: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): File? {
        val cacheKey = "$relSubdir/$fileName"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Tier 1: cached path from a prior MediaStore-fallback success.
        prefs.getString(cacheKey, null)?.let { cachedPath ->
            try {
                val f = File(cachedPath)
                f.writeBytes(bytes)
                return f
            } catch (e: Exception) {
                Log.w(TAG, "Cached path '$cachedPath' no longer writable: ${e.message}")
                prefs.edit().remove(cacheKey).apply()
            }
        }

        // Tier 2: canonical direct write — fast path for fresh installs and own files.
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            relSubdir
        )
        try {
            dir.mkdirs()
            val file = File(dir, fileName)
            file.writeBytes(bytes)
            return file
        } catch (e: Exception) {
            Log.w(TAG, "Direct write failed for $cacheKey: ${e.message} — falling back to MediaStore")
        }

        // Tier 3: MediaStore insert — auto-suffixes around the orphan.
        return try {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/$relSubdir/"
                )
            }
            val uri = resolver.insert(collection, values) ?: run {
                Log.e(TAG, "MediaStore insert returned null for $cacheKey")
                return null
            }
            resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            val resolved = resolveFile(context, uri)
            if (resolved != null) {
                prefs.edit().putString(cacheKey, resolved.absolutePath).apply()
            }
            resolved ?: File(dir, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore write failed for $cacheKey: ${e.message}")
            null
        }
    }

    /**
     * Stream variant for large content (PDFs etc.). Materializes via ByteArrayOutputStream
     * first so the three-tier retry can fall through without re-invoking [produce] mid-stream.
     */
    fun writeStream(
        context: Context,
        relSubdir: String,
        fileName: String,
        mimeType: String,
        produce: (OutputStream) -> Unit
    ): File? {
        val baos = ByteArrayOutputStream()
        produce(baos)
        return writeBytes(context, relSubdir, fileName, mimeType, baos.toByteArray())
    }

    private fun resolveFile(context: Context, uri: Uri): File? = try {
        context.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null
        )?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) File(c.getString(0)) else null
        }
    } catch (e: Exception) {
        null
    }
}
