package com.turntable.barcodescanner

import android.util.Base64
import com.turntable.barcodescanner.debug.OutgoingUrlInterceptor
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal [Transmission RPC](https://github.com/transmission/transmission/blob/main/docs/rpc-spec.md):
 * session id (409 handshake), then `torrent-add` with base64 `metainfo`.
 *
 * Reference implementation: [transmission/transmission](https://github.com/transmission/transmission).
 */
class TransmissionRpcClient(
    private val rpcUrl: String,
    private val username: String,
    private val password: String,
) {

    private var sessionId: String? = null

    private val http: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(OutgoingUrlInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    init {
        require(rpcUrl.isNotEmpty()) { "Invalid Transmission RPC URL" }
    }

    fun addTorrentFromBytes(torrentBytes: ByteArray): Result<Unit> {
        if (torrentBytes.isEmpty()) {
            return Result.failure(IllegalStateException("Empty .torrent data"))
        }
        val b64 = Base64.encodeToString(torrentBytes, Base64.NO_WRAP)
        val args = JSONObject().put("metainfo", b64)
        return jsonRpc("torrent-add", args).mapCatching { o ->
            val result = o.optString("result", "")
            check(result == "success") { result.ifBlank { "RPC error" } }
        }
    }

    private fun jsonRpc(method: String, arguments: JSONObject): Result<JSONObject> {
        val payload = JSONObject()
            .put("method", method)
            .put("arguments", arguments)
        val json = payload.toString()
        val media = JSON_MEDIA
        var lastErr: Exception? = null
        repeat(5) {
            try {
                val req = buildRequest(json, media)
                http.newCall(req).execute().use { resp ->
                    when (resp.code) {
                        409 -> {
                            val sid = resp.header("X-Transmission-Session-Id")?.trim()
                            if (!sid.isNullOrEmpty()) sessionId = sid
                            return@repeat
                        }
                        !in 200..299 -> {
                            val text = resp.body?.string().orEmpty()
                            return Result.failure(
                                IllegalStateException("HTTP ${resp.code}: ${text.take(300)}"),
                            )
                        }
                        else -> {
                            val text = resp.body?.string().orEmpty()
                            return try {
                                Result.success(JSONObject(text))
                            } catch (e: Exception) {
                                Result.failure(IllegalStateException("Invalid JSON: ${text.take(200)}"))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        return Result.failure(lastErr ?: IllegalStateException("Transmission RPC failed (session?)"))
    }

    private fun buildRequest(json: String, media: okhttp3.MediaType): Request {
        val b = Request.Builder()
            .url(rpcUrl)
            .post(json.toRequestBody(media))
        sessionId?.let { b.header("X-Transmission-Session-Id", it) }
        val u = username.trim()
        if (u.isNotEmpty() || password.isNotEmpty()) {
            b.header("Authorization", Credentials.basic(u, password))
        }
        return b.build()
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /**
         * Normalizes to `http(s)://host:port/transmission/rpc`.
         */
        fun normalizeRpcUrl(raw: String?): String? {
            var t = raw?.trim().orEmpty()
            if (t.isEmpty()) return null
            t = t.replace("\r", "").replace("\n", "")
            if (!t.startsWith("http://", ignoreCase = true) &&
                !t.startsWith("https://", ignoreCase = true)
            ) {
                t = "http://$t"
            }
            t = t.trimEnd('/')
            if (t.endsWith("/transmission/rpc", ignoreCase = true)) {
                return t
            }
            return "$t/transmission/rpc"
        }

        fun fromPrefs(prefs: SearchPrefs): TransmissionRpcClient? {
            val raw = prefs.transmissionRpcUrl?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val url = normalizeRpcUrl(raw) ?: return null
            return TransmissionRpcClient(
                rpcUrl = url,
                username = prefs.transmissionUsername?.trim().orEmpty(),
                password = prefs.transmissionPassword.orEmpty(),
            )
        }
    }
}
