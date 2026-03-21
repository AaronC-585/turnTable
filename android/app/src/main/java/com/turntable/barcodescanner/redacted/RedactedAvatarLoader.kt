package com.turntable.barcodescanner.redacted

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.turntable.barcodescanner.debug.OutgoingUrlInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Loads tracker avatar images; adds [Authorization] for redacted.sh URLs (same as API).
 */
object RedactedAvatarLoader {

    private val http = OkHttpClient.Builder()
        .addInterceptor(OutgoingUrlInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * @param rawUrl from API (absolute or site-relative)
     * @return decoded bitmap or null
     */
    fun loadBitmap(rawUrl: String?, apiKey: String, maxSidePx: Int = 512): Bitmap? {
        if (rawUrl.isNullOrBlank()) return null
        val url = if (rawUrl.startsWith("http", ignoreCase = true)) {
            rawUrl.trim()
        } else {
            "https://redacted.sh/${rawUrl.trim().trimStart('/')}"
        }
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "turnTable/1.0 (Android)")
                .apply {
                    if (url.contains("redacted.sh", ignoreCase = true)) {
                        header("Authorization", apiKey.trim())
                    }
                }
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                decodeSampled(bytes, maxSidePx)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampled(bytes: ByteArray, maxSidePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxSidePx || bounds.outHeight / sample > maxSidePx) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
