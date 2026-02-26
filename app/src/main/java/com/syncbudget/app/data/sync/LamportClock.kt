package com.syncbudget.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicLong

class LamportClock(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lamport_clock", Context.MODE_PRIVATE)

    private val counter = AtomicLong(prefs.getLong("clock", 0L))

    val value: Long get() = counter.get()

    @Synchronized
    fun tick(): Long {
        // Re-read from prefs to pick up any changes from SyncWorker
        val prefsVal = prefs.getLong("clock", 0L)
        val current = maxOf(counter.get(), prefsVal)
        val newVal = current + 1
        counter.set(newVal)
        prefs.edit().putLong("clock", newVal).commit()
        return newVal
    }

    @Synchronized
    fun merge(remoteClock: Long) {
        val prefsVal = prefs.getLong("clock", 0L)
        val current = maxOf(counter.get(), prefsVal)
        val newVal = maxOf(current, remoteClock) + 1
        counter.set(newVal)
        prefs.edit().putLong("clock", newVal).commit()
    }

    @Synchronized
    fun reset() {
        counter.set(0L)
        prefs.edit().putLong("clock", 0L).commit()
    }
}
