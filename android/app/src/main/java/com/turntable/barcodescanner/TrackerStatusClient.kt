package com.turntable.barcodescanner

import androidx.annotation.DrawableRes
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * [red.trackerstatus.info](https://red.trackerstatus.info/) public status API.
 * Icons match the legacy REDacted client: blue = up, red = down.
 */
object TrackerStatusClient {

    const val STATUS_PAGE_URL = "https://red.trackerstatus.info/"
    private const val API_URL = "https://red.trackerstatus.info/api/all/"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    enum class Service(
        val jsonKey: String,
        @DrawableRes val iconOk: Int,
        @DrawableRes val iconBad: Int,
    ) {
        WEBSITE(
            "Website",
            R.drawable.tracker_status_ie_ok,
            R.drawable.tracker_status_ie_bad,
        ),
        TRACKER_HTTP(
            "TrackerHTTP",
            R.drawable.tracker_status_http_ok,
            R.drawable.tracker_status_http_bad,
        ),
        TRACKER_HTTPS(
            "TrackerHTTPS",
            R.drawable.tracker_status_https_ok,
            R.drawable.tracker_status_https_bad,
        ),
        IRC(
            "IRC",
            R.drawable.tracker_status_irc_ok,
            R.drawable.tracker_status_irc_bad,
        ),
        IRC_TORRENT_ANNOUNCER(
            "IRCTorrentAnnouncer",
            R.drawable.tracker_status_announcer_ok,
            R.drawable.tracker_status_announcer_bad,
        ),
        IRC_USER_IDENTIFIER(
            "IRCUserIdentifier",
            R.drawable.tracker_status_userid_ok,
            R.drawable.tracker_status_userid_bad,
        ),
    }

    data class Row(
        val service: Service,
        val ok: Boolean,
        val latency: String,
    )

    fun fetch(): Result<List<Row>> {
        val req = Request.Builder()
            .url(API_URL)
            .header("User-Agent", "turnTable/1.0 (Android)")
            .build()
        return try {
            http.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val rows = Service.entries.map { svc ->
                    val obj = json.optJSONObject(svc.jsonKey)
                    val statusStr = obj?.optString("Status").orEmpty()
                    val ok = statusStr == "1" ||
                        statusStr.equals("true", ignoreCase = true)
                    val latency = obj?.optString("Latency")
                        ?.takeIf { it.isNotBlank() }
                        ?: "—"
                    Row(svc, ok, latency)
                }
                Result.success(rows)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
