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
        val newVal = counter.incrementAndGet()
        prefs.edit().putLong("clock", newVal).apply()
        return newVal
    }

    @Synchronized
    fun merge(remoteClock: Long) {
        val newVal = maxOf(counter.get(), remoteClock) + 1
        counter.set(newVal)
        prefs.edit().putLong("clock", newVal).apply()
    }
}
