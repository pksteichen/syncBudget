package com.techadvantage.budgetrak.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Lightweight network-state checks for code paths that don't have access
 * to `MainViewModel.isNetworkAvailable` (background workers, manager
 * classes constructed without a VM reference).
 *
 * MainViewModel keeps a live `isNetworkAvailable` flag via NetworkCallback
 * and is the preferred read site for foreground UI code. This object
 * matches the same INTERNET-capability check so the two read sites agree,
 * just via a one-shot synchronous query instead of a callback.
 *
 * Fail-open: if the system call throws (missing permission, exotic
 * platform), we assume online — better to attempt the call and let
 * Firebase / Cloud Storage handle the failure than to silently block
 * legitimate operations on a permission edge case.
 */
object NetworkUtils {
    fun isOnline(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        true
    }
}
