package com.syncbudget.app.data.sync

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * File-based lock that works across processes (unlike ReentrantLock),
 * combined with an in-process Mutex to prevent coroutine races within
 * the same process (e.g., two LaunchedEffects calling sync concurrently).
 */
class SyncFileLock(context: Context) {

    companion object {
        /** In-process mutex shared across all SyncFileLock instances. */
        private val inProcessMutex = Mutex()
    }

    private val lockFile = File(context.filesDir, "sync.lock")
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    fun tryLock(): Boolean {
        // In-process guard first
        if (!inProcessMutex.tryLock()) return false
        return try {
            val raf = RandomAccessFile(lockFile, "rw")
            channel = raf.channel
            lock = channel?.tryLock()
            if (lock == null) {
                channel?.close()
                channel = null
                inProcessMutex.unlock()
                false
            } else {
                true
            }
        } catch (_: Exception) {
            channel?.close()
            channel = null
            inProcessMutex.unlock()
            false
        }
    }

    fun unlock() {
        try { lock?.release() } catch (_: Exception) {}
        try { channel?.close() } catch (_: Exception) {}
        lock = null
        channel = null
        try { inProcessMutex.unlock() } catch (_: Exception) {}
    }
}
