package app.bunmusic.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Whether the phone currently has a way out.
 *
 * `activeNetwork` alone is not enough: a captive-portal Wi-Fi is "connected" and
 * useless. The check is the INTERNET capability, and the caller decides what to
 * do with the answer — this object never caches it, because a cached network
 * state is wrong the moment the lift doors close.
 */
object Network {

    fun online(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    /** True on mobile data, so a download can wait for Wi-Fi. */
    fun metered(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)

    fun label(context: Context): String = when {
        !online(context) -> "Offline"
        metered(context) -> "Mobile data"
        else -> "Wi-Fi"
    }
}
