package com.syncbudget.app.data.sync

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * File-based lock that works across processes (unlike ReentrantLock).
 * Ensures SyncWorker and foreground sync never run concurrently,
 * even if WorkManager runs in a separate process.
 */
class SyncFileLock(context: Context) {

    private val lockFile = File(context.filesDir, "sync.lock")
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    fun tryLock(): Boolean {
        return try {
            val raf = RandomAccessFile(lockFile, "rw")
            channel = raf.channel
            lock = channel?.tryLock()
            if (lock == null) {
                channel?.close()
                channel = null
                false
            } else {
                true
            }
        } catch (_: Exception) {
            channel?.close()
            channel = null
            false
        }
    }

    fun unlock() {
        try { lock?.release() } catch (_: Exception) {}
        try { channel?.close() } catch (_: Exception) {}
        lock = null
        channel = null
    }
}
