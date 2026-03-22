package com.turntable.barcodescanner

import android.util.Base64
import com.turntable.barcodescanner.debug.OutgoingUrlInterceptor
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deluge Web [JSON-RPC](https://deluge.readthedocs.io/en/latest/reference/webapi.html) over HTTP,
 * matching the flow used by [jessielw/deluge-web-client](https://github.com/jessielw/deluge-web-client):
 * `auth.login`, `web.connect` to the first host when needed, then `core.add_torrent_file`.
 */
class DelugeWebClient(
    /** POST target, e.g. `http://192.168.1.5:8112/json` (see [normalizeJsonEndpoint]). */
    private val jsonRpcUrl: String,
    private val password: String,
) {

    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .addInterceptor(OutgoingUrlInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val requestId = AtomicInteger(0)

    init {
        require(jsonRpcUrl.isNotEmpty()) { "Invalid Deluge Web URL" }
    }

    fun addTorrentFromBytes(filename: String, torrentBytes: ByteArray): Result<Unit> {
        if (torrentBytes.isEmpty()) {
            return Result.failure(IllegalStateException("Empty .torrent data"))
        }
        return runCatching {
            ensureLoggedInAndConnected()
            val b64 = Base64.encodeToString(torrentBytes, Base64.NO_WRAP)
            val params = JSONArray()
                .put(filename.ifBlank { "torrent.torrent" })
                .put(b64)
                .put(JSONObject())
            val resp = jsonRpc("core.add_torrent_file", params).getOrThrow()
            rpcThrowIfError(resp)
        }
    }

    private fun ensureLoggedInAndConnected() {
        val loginResp = jsonRpc("auth.login", JSONArray().put(password)).getOrThrow()
        rpcThrowIfError(loginResp)

        val connectedResp = jsonRpc("web.connected", JSONArray()).getOrThrow()
        rpcThrowIfError(connectedResp)
        if (connectedResp.optBoolean("result", false)) {
            return
        }

        val hostsResp = jsonRpc("web.get_hosts", JSONArray()).getOrThrow()
        rpcThrowIfError(hostsResp)
        val hosts = hostsResp.optJSONArray("result")
            ?: throw IllegalStateException("No Deluge daemon hosts in Web UI")
        if (hosts.length() == 0) {
            throw IllegalStateException("No Deluge daemon hosts in Web UI")
        }
        val first = hosts.optJSONArray(0) ?: throw IllegalStateException("Invalid host list")
        val hostId = first.optString(0, "").trim()
        if (hostId.isEmpty()) {
            throw IllegalStateException("Missing host id")
        }
        val connectResp = jsonRpc("web.connect", JSONArray().put(hostId)).getOrThrow()
        rpcThrowIfError(connectResp)
    }

    private fun rpcThrowIfError(resp: JSONObject) {
        val err = resp.opt("error")
        if (err != null && err !== JSONObject.NULL) {
            throw IllegalStateException(rpcErrorToMessage(err))
        }
    }

    private fun jsonRpc(method: String, params: JSONArray): Result<JSONObject> {
        val id = requestId.incrementAndGet()
        val body = JSONObject()
            .put("method", method)
            .put("params", params)
            .put("id", id)
        val req = Request.Builder()
            .url(jsonRpcUrl)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("HTTP ${resp.code}: ${text.take(300)}"),
                    )
                }
                Result.success(JSONObject(text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun rpcErrorToMessage(err: Any?): String {
        return when (err) {
            null, JSONObject.NULL -> "Unknown RPC error"
            is JSONObject -> err.optString("message", err.toString())
            else -> err.toString()
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /**
         * Normalizes to a `/json` POST URL (Deluge Web UI), mirroring
         * [deluge-web-client](https://github.com/jessielw/deluge-web-client) `_build_url`.
         */
        fun normalizeJsonEndpoint(raw: String?): String? {
            var t = raw?.trim().orEmpty()
            if (t.isEmpty()) return null
            t = t.replace("\r", "").replace("\n", "")
            if (!t.startsWith("http://", ignoreCase = true) &&
                !t.startsWith("https://", ignoreCase = true)
            ) {
                t = "http://$t"
            }
            if (!t.endsWith("/")) {
                t += "/"
            }
            if (!t.contains("/json", ignoreCase = true)) {
                t += "json/"
            }
            return t.trimEnd('/')
        }

        fun fromPrefs(prefs: SearchPrefs): DelugeWebClient? {
            val raw = prefs.delugeWebUrl?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val url = normalizeJsonEndpoint(raw) ?: return null
            return DelugeWebClient(
                jsonRpcUrl = url,
                password = prefs.delugePassword.orEmpty(),
            )
        }
    }
}
