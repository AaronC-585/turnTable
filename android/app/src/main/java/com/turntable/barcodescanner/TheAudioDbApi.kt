package com.turntable.barcodescanner

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * TheAudioDB **v2** JSON API only: every request uses
 * `https://www.theaudiodb.com/api/v2/json/...` with header **`X-API-KEY`**.
 *
 * @see [TheAudioDB API docs](https://www.theaudiodb.com/free_music_api)
 */
object TheAudioDbApi {

    const val V2_JSON_BASE = "https://www.theaudiodb.com/api/v2/json"

    private const val UA = "turnTable/1.0 (https://github.com/turntable)"

    /** Value sent as `X-API-KEY`; defaults to public test key `123` if unset (v2 may require premium). */
    fun effectiveKey(apiKey: String?): String =
        apiKey?.trim()?.takeIf { it.isNotEmpty() } ?: "123"

    fun headers(key: String): Map<String, String> = mapOf(
        "User-Agent" to UA,
        "Accept" to "application/json",
        "X-API-KEY" to key,
    )

    /** Path segment for v2 URLs (e.g. artist name, album title, MusicBrainz id). */
    fun pathEncode(raw: String): String =
        URLEncoder.encode(raw.trim(), Charsets.UTF_8.name()).replace("+", "%20")

    /**
     * GET [V2_JSON_BASE]/[path] with [headers]. [path] must not start with `/`.
     * @return response body on success; null if HTTP error, network failure, or JSON error [Message].
     */
    fun get(path: String, apiKey: String?): String? {
        val key = effectiveKey(apiKey)
        val normalized = path.trimStart('/')
        val url = "$V2_JSON_BASE/$normalized"
        val (code, body) = httpGetWithCode(url, headers(key))
        if (code !in 200..299 || body.isNullOrBlank()) return null
        if (looksLikeErrorMessagePayload(body)) return null
        return body
    }

    private fun looksLikeErrorMessagePayload(json: String): Boolean {
        return try {
            val msg = JSONObject(json).optString("Message", "")
            msg.isNotBlank() && (
                msg.contains("invalid", ignoreCase = true) ||
                    msg.contains("premium", ignoreCase = true) ||
                    msg.contains("api key", ignoreCase = true)
                )
        } catch (_: Exception) {
            false
        }
    }

    private fun httpGetWithCode(url: String, headers: Map<String, String>): Pair<Int, String?> {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            code to text
        } catch (_: Exception) {
            Pair(-1, null)
        }
    }
}
