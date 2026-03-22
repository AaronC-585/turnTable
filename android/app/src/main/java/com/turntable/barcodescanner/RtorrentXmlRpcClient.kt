package com.turntable.barcodescanner

import android.net.Uri
import android.util.Base64
import com.turntable.barcodescanner.debug.OutgoingUrlInterceptor
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * rTorrent [XML-RPC](https://github.com/rakshasa/rtorrent/wiki/RPC-Setup-XMLRPC) over HTTP
 * (e.g. nginx `scgi_pass` to rTorrent’s SCGI socket). Adds torrents via `load.raw_start`:
 * empty target string plus raw `.torrent` bytes as XML-RPC base64.
 *
 * Reference client: [rakshasa/rtorrent](https://github.com/rakshasa/rtorrent).
 */
class RtorrentXmlRpcClient(
    private val xmlRpcUrl: String,
    private val username: String,
    private val password: String,
) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(OutgoingUrlInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    init {
        require(xmlRpcUrl.isNotEmpty()) { "Invalid rTorrent XML-RPC URL" }
    }

    fun addTorrentFromBytes(torrentBytes: ByteArray): Result<Unit> {
        if (torrentBytes.isEmpty()) {
            return Result.failure(IllegalStateException("Empty .torrent data"))
        }
        val b64 = Base64.encodeToString(torrentBytes, Base64.NO_WRAP)
        val payload = buildLoadRawStartRequest(b64)
        return runCatching {
            val req = Request.Builder()
                .url(xmlRpcUrl)
                .post(payload.toByteArray(Charsets.UTF_8).toRequestBody(XML_MEDIA))
            val u = username.trim()
            if (u.isNotEmpty() || password.isNotEmpty()) {
                req.header("Authorization", Credentials.basic(u, password))
            }
            http.newCall(req.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code}: ${text.take(300)}")
                }
                parseMethodResponse(text).getOrThrow()
            }
        }
    }

    private fun parseMethodResponse(xml: String): Result<Unit> {
        if (xml.contains("<fault>", ignoreCase = true)) {
            val msg = extractFaultString(xml) ?: "XML-RPC fault"
            return Result.failure(IllegalStateException(msg))
        }
        if (!xml.contains("<methodResponse", ignoreCase = true)) {
            return Result.failure(IllegalStateException("Invalid XML-RPC response"))
        }
        return Result.success(Unit)
    }

    private fun extractFaultString(xml: String): String? {
        val idx = xml.indexOf("<name>faultString</name>", ignoreCase = true)
        if (idx < 0) return null
        val after = xml.indexOf("<string>", idx)
        if (after < 0) return null
        val start = after + "<string>".length
        val end = xml.indexOf("</string>", start)
        if (end < start) return null
        return xml.substring(start, end)
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    companion object {
        private val XML_MEDIA = "text/xml; charset=utf-8".toMediaType()

        /**
         * Normalizes to an HTTP URL ending with `/RPC2` when no path is given (common SCGI→HTTP mapping).
         */
        fun normalizeXmlRpcUrl(raw: String?): String? {
            var t = raw?.trim().orEmpty()
            if (t.isEmpty()) return null
            t = t.replace("\r", "").replace("\n", "")
            if (!t.startsWith("http://", ignoreCase = true) &&
                !t.startsWith("https://", ignoreCase = true)
            ) {
                t = "http://$t"
            }
            t = t.trimEnd('/')
            val uri = Uri.parse(t)
            val path = uri.path
            if (path.isNullOrBlank() || path == "/") {
                return "$t/RPC2"
            }
            return t
        }

        fun fromPrefs(prefs: SearchPrefs): RtorrentXmlRpcClient? {
            val raw = prefs.rtorrentXmlRpcUrl?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val url = normalizeXmlRpcUrl(raw) ?: return null
            return RtorrentXmlRpcClient(
                xmlRpcUrl = url,
                username = prefs.rtorrentUsername?.trim().orEmpty(),
                password = prefs.rtorrentPassword.orEmpty(),
            )
        }
    }
}

private fun buildLoadRawStartRequest(base64Torrent: String): String {
    return buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<methodCall>")
        append("<methodName>load.raw_start</methodName>")
        append("<params>")
        append("<param><value><string></string></value></param>")
        append("<param><value><base64>")
        append(base64Torrent)
        append("</base64></value></param>")
        append("</params>")
        append("</methodCall>")
    }
}
