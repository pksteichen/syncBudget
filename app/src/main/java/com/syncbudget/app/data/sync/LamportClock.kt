package com.syncbudget.app.data.sync

import android.content.Context
import android.content.SharedPreferences

class LamportClock(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lamport_clock", Context.MODE_PRIVATE)

    private var counter: Long = prefs.getLong("clock", 0L)

    val value: Long get() = counter

    fun tick(): Long {
        counter++
        prefs.edit().putLong("clock", counter).apply()
        return counter
    }

    fun merge(remoteClock: Long) {
        counter = maxOf(counter, remoteClock) + 1
        prefs.edit().putLong("clock", counter).apply()
    }
}
