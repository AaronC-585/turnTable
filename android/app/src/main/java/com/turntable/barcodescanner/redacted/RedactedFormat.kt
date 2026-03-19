package com.turntable.barcodescanner.redacted

import java.util.Locale
import java.util.concurrent.TimeUnit

object RedactedFormat {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        var v = bytes.toDouble().coerceAtLeast(1.0)
        var u = 0
        while (v >= 1024 && u < units.lastIndex) {
            v /= 1024.0
            u++
        }
        return if (u == 0) {
            "${bytes} B"
        } else {
            String.format(Locale.US, "%.2f %s", v, units[u])
        }
    }

    fun formatRatio(r: Double): String = String.format(Locale.US, "%.2f", r)

    /** Rough human duration from seconds (e.g. password age). */
    fun formatDurationSeconds(seconds: Long): String {
        if (seconds <= 0) return "—"
        val y = TimeUnit.SECONDS.toDays(seconds) / 365
        val mo = (TimeUnit.SECONDS.toDays(seconds) % 365) / 30
        val parts = mutableListOf<String>()
        if (y > 0) parts.add("$y year${if (y != 1L) "s" else ""}")
        if (mo > 0) parts.add("$mo month${if (mo != 1L) "s" else ""}")
        if (parts.isEmpty()) {
            val d = TimeUnit.SECONDS.toDays(seconds)
            if (d > 0) return "$d day${if (d != 1L) "s" else ""}"
        }
        return parts.joinToString(", ").ifBlank { "${seconds}s" }
    }
}
