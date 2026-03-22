package com.turntable.barcodescanner

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Honors [SearchPrefs.downloadOverWifiOnly] for large/binary downloads (app updates, announcement images).
 */
object DownloadNetworkPolicy {

    /**
     * When [wifiOnly] is true, returns true only if the active default network uses Wi‑Fi.
     */
    fun allowsLargeDownload(context: Context, wifiOnly: Boolean): Boolean {
        if (!wifiOnly) return true
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
