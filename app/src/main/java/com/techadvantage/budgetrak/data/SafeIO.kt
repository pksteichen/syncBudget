package com.techadvantage.budgetrak.data

import android.content.Context
import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Utilities for crash-safe file I/O and defensive JSON parsing.
 * Used by all repositories to prevent data loss from partial writes,
 * corrupt records, or invalid numeric values.
 */
object SafeIO {

    private const val TAG = "SafeIO"

    /** Per-file locks to prevent concurrent writes to the same file.
     *  Protects against race conditions between user actions, sync merges,
     *  and widget updates all writing to the same repository file. */
    private val fileLocks = mutableMapOf<String, ReentrantLock>()
    private fun lockFor(fileName: String): ReentrantLock = synchronized(fileLocks) {
        fileLocks.getOrPut(fileName) { ReentrantLock() }
    }

    /**
     * Atomically write data to an internal file.  Writes to a temp file
     * first, then renames — prevents partial writes from corrupting data
     * if the app crashes or disk fills mid-write.
     */
    fun atomicWrite(context: Context, fileName: String, data: ByteArray) {
        val lock = lockFor(fileName)
        lock.lock()
        try {
            val tmpFile = File(context.filesDir, "$fileName.tmp")
            try {
                tmpFile.writeBytes(data)
                val target = context.getFileStreamPath(fileName)
                if (!tmpFile.renameTo(target)) {
                    // renameTo can fail on some filesystems; fall back to copy
                    tmpFile.inputStream().use { input ->
                        context.openFileOutput(fileName, Context.MODE_PRIVATE).use { output ->
                            input.copyTo(output)
                        }
                    }
                    tmpFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write $fileName: ${e.message}", e)
                tmpFile.delete()
                throw e  // let caller handle (e.g. retry or notify user)
            }
        } finally {
            lock.unlock()
        }
    }

    /** Atomically write a JSONArray to an internal file. */
    fun atomicWriteJson(context: Context, fileName: String, json: JSONArray) {
        atomicWrite(context, fileName, json.toString().toByteArray())
    }

    /** Atomically write a JSONObject to an internal file. */
    fun atomicWriteJson(context: Context, fileName: String, json: JSONObject) {
        atomicWrite(context, fileName, json.toString().toByteArray())
    }

    /** Delegates to [atomicWrite] which already acquires a per-file lock. */
    suspend fun atomicWriteLocked(context: Context, fileName: String, data: ByteArray) {
        atomicWrite(context, fileName, data)
    }

    /** Delegates to [atomicWriteJson] which already acquires a per-file lock. */
    suspend fun atomicWriteJsonLocked(context: Context, fileName: String, json: JSONArray) {
        atomicWriteJson(context, fileName, json)
    }

    /** Delegates to [atomicWriteJson] which already acquires a per-file lock. */
    suspend fun atomicWriteJsonLocked(context: Context, fileName: String, json: JSONObject) {
        atomicWriteJson(context, fileName, json)
    }

    /**
     * Read a JSON array from an internal file, returning empty array on
     * any error (missing file, corrupt JSON, I/O failure).
     */
    fun readJsonArray(context: Context, fileName: String): JSONArray {
        return try {
            val file = context.getFileStreamPath(fileName)
            if (!file.exists()) return JSONArray()
            val text = context.openFileInput(fileName).bufferedReader().use { it.readText() }
            if (text.isBlank()) JSONArray() else JSONArray(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read $fileName: ${e.message}", e)
            JSONArray()
        }
    }

    /**
     * Read a JSON object from an internal file, returning null on error.
     */
    fun readJsonObject(context: Context, fileName: String): JSONObject? {
        return try {
            val file = context.getFileStreamPath(fileName)
            if (!file.exists()) return null
            val text = context.openFileInput(fileName).bufferedReader().use { it.readText() }
            if (text.isBlank()) null else JSONObject(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read $fileName: ${e.message}", e)
            null
        }
    }

    /** Return the double if finite, otherwise the default. Guards against NaN/Infinity. */
    fun safeDouble(value: Double, default: Double = 0.0): Double {
        return if (value.isNaN() || value.isInfinite()) default else value
    }
}
