package com.turntable.barcodescanner

import androidx.annotation.DrawableRes
import com.turntable.barcodescanner.debug.OutgoingUrlInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * [red.trackerstatus.info](https://red.trackerstatus.info/) public status API.
 * Icons are vector drawables; [AppBottomBars] tints **green** when up, **red** when down.
 */
object TrackerStatusClient {

    const val STATUS_PAGE_URL = "https://red.trackerstatus.info/"
    private const val API_URL = "https://red.trackerstatus.info/api/all/"

    private val http = OkHttpClient.Builder()
        .addInterceptor(OutgoingUrlInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    enum class Service(
        val jsonKey: String,
        @DrawableRes val icon: Int,
    ) {
        WEBSITE("Website", R.drawable.tracker_status_website),
        TRACKER_HTTP("TrackerHTTP", R.drawable.tracker_status_http),
        TRACKER_HTTPS("TrackerHTTPS", R.drawable.tracker_status_https),
        IRC("IRC", R.drawable.tracker_status_irc),
        IRC_TORRENT_ANNOUNCER("IRCTorrentAnnouncer", R.drawable.tracker_status_announcer),
        IRC_USER_IDENTIFIER("IRCUserIdentifier", R.drawable.tracker_status_userid),
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
