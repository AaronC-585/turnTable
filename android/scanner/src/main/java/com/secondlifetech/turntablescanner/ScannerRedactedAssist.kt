package com.secondlifetech.turntablescanner

import android.net.Uri
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Minimal Redacted `browse` client for barcode → artist/album resolve. */
object ScannerRedactedAssist {

    data class Hit(val artist: String, val album: String)

    private val http = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun firstHit(apiKey: String, barcode: String): Hit? {
        val q = barcode.trim()
        if (q.isEmpty() || apiKey.isBlank()) return null
        val url = "https://redacted.sh/ajax.php".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("action", "browse")
            .addQueryParameter("searchstr", q)
            .addQueryParameter("page", "1")
            .addQueryParameter("group_results", "1")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", apiKey.trim())
            .header("User-Agent", "turnTableScanner/1.0 (Android)")
            .get()
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val root = JSONObject(resp.body?.string().orEmpty())
                if (root.optString("status") != "success") return null
                val body = root.optJSONObject("response") ?: return null
                val arr = body.optJSONArray("results") ?: return null
                if (arr.length() == 0) return null
                val o = arr.optJSONObject(0) ?: return null
                val artist = o.optString("artist").trim()
                val album = o.optString("groupName").trim()
                if (artist.isEmpty() && album.isEmpty()) return null
                Hit(artist, album)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun turnTableSearchUri(barcode: String, artist: String?, album: String?): Uri {
        val b = Uri.Builder()
            .scheme("turntable")
            .authority("search")
            .appendQueryParameter("barcode", barcode)
        val a = artist?.trim().orEmpty()
        val al = album?.trim().orEmpty()
        if (a.isNotEmpty()) b.appendQueryParameter("artist", a)
        if (al.isNotEmpty()) b.appendQueryParameter("album", al)
        return b.build()
    }
}
