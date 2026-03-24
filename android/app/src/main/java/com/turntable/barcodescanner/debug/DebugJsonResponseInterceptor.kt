package com.turntable.barcodescanner.debug

import android.util.Log
import com.turntable.barcodescanner.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Debug builds only: writes JSON response bodies to Logcat (`TurnTableJson`).
 * Does not consume the response body; uses [Response.peekBody].
 */
object DebugJsonResponseInterceptor : Interceptor {

    private const val TAG = "TurnTableJson"
    private const val MAX_PEEK_BYTES = 512 * 1024L

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!BuildConfig.DEBUG) return response

        val body = response.body ?: return response
        val media = body.contentType()?.toString()?.lowercase() ?: ""
        val peeked = try {
            response.peekBody(MAX_PEEK_BYTES).string()
        } catch (_: Exception) {
            return response
        }
        val t = peeked.trimStart()
        val looksJson =
            media.contains("json") || t.startsWith("{") || t.startsWith("[")
        if (!looksJson || peeked.isEmpty()) return response

        val line = "${response.request.method} ${response.request.url}\n$peeked"
        if (line.length > 8000) {
            var start = 0
            var part = 0
            while (start < line.length) {
                val end = minOf(start + 8000, line.length)
                Log.d(TAG, "[${++part}] ${line.substring(start, end)}")
                start = end
            }
        } else {
            Log.d(TAG, line)
        }
        return response
    }
}
