package com.turntable.barcodescanner

import okhttp3.FormBody
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

/**
 * Minimal [qBittorrent Web API v2](https://github.com/qbittorrent/qBittorrent/wiki/WebUI-API-(qBittorrent-4.1)):
 * cookie login + [torrents/add] with a `.torrent` file body.
 */
class QbittorrentWebClient(
    /** Normalized base, e.g. `http://192.168.1.5:8080` (no `/api/v2`). */
    private val base: String,
    private val username: String,
    private val password: String,
) {

    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    init {
        require(base.isNotEmpty()) { "Invalid qBittorrent base URL" }
    }

    /**
     * Logs in when [username] is non-blank (skipped if both username and password are blank —
     * Web UI auth disabled). Then POSTs the torrent file.
     */
    fun addTorrentFile(filename: String, torrentBytes: ByteArray): Result<Unit> {
        if (torrentBytes.isEmpty()) {
            return Result.failure(IllegalStateException("Empty .torrent data"))
        }
        val login = loginIfNeeded()
        login.onFailure { return Result.failure(it) }

        val body = torrentBytes.toRequestBody("application/x-bittorrent".toMediaTypeOrNull())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("torrents", filename.ifBlank { "torrent.torrent" }, body)
            .build()

        val req = Request.Builder()
            .url("$base/api/v2/torrents/add")
            .post(multipart)
            .build()

        return try {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 403 || resp.code == 401 ->
                        Result.failure(IllegalStateException("qBittorrent refused (HTTP ${resp.code}). Log in in Web UI or check credentials."))
                    !resp.isSuccessful ->
                        Result.failure(IllegalStateException("HTTP ${resp.code}: ${text.take(200)}"))
                    text.equals("Fails.", ignoreCase = true) ->
                        Result.failure(IllegalStateException("qBittorrent rejected the torrent file"))
                    else -> Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loginIfNeeded(): Result<Unit> {
        val u = username.trim()
        val p = password
        if (u.isEmpty() && p.isEmpty()) {
            return Result.success(Unit)
        }
        val form = FormBody.Builder()
            .add("username", u)
            .add("password", p)
            .build()
        val req = Request.Builder()
            .url("$base/api/v2/auth/login")
            .post(form)
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty().trim()
                when {
                    !resp.isSuccessful ->
                        Result.failure(IllegalStateException("Login HTTP ${resp.code}: ${text.take(200)}"))
                    text != "Ok." ->
                        Result.failure(IllegalStateException("Login failed: ${text.ifBlank { "check username/password" }}"))
                    else -> Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /** Trim, strip trailing slash, add http:// if no scheme (LAN). */
        fun normalizeBaseUrl(raw: String?): String? {
            var t = raw?.trim().orEmpty()
            if (t.isEmpty()) return null
            t = t.replace("\r", "").replace("\n", "")
            t = t.trimEnd('/')
            if (!t.startsWith("http://", ignoreCase = true) &&
                !t.startsWith("https://", ignoreCase = true)
            ) {
                t = "http://$t"
            }
            return t.trimEnd('/')
        }

        fun fromPrefs(prefs: SearchPrefs): QbittorrentWebClient? {
            val raw = prefs.qbittorrentBaseUrl?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val base = normalizeBaseUrl(raw) ?: return null
            return QbittorrentWebClient(
                base = base,
                username = prefs.qbittorrentUsername?.trim().orEmpty(),
                password = prefs.qbittorrentPassword.orEmpty(),
            )
        }
    }
}
