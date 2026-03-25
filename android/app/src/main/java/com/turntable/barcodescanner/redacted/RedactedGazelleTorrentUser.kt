package com.turntable.barcodescanner.redacted

import org.json.JSONObject
import java.util.Locale

/**
 * Interprets per-torrent [userStatus] strings from Gazelle/Redacted JSON (e.g. torrent group).
 */
object RedactedGazelleTorrentUser {

    /** True when this torrent is one the user is currently seeding (uploading to peers). */
    fun isUserSeeding(userStatus: String): Boolean {
        val s = userStatus.trim().lowercase(Locale.US)
        if (s.isEmpty()) return false
        if (s.contains("seed")) return true
        if (s.contains("uploading")) return true
        // Some Gazelle builds use short labels
        if (s == "up" || s.startsWith("up ")) return true
        return false
    }

    /** Uses [userStatus] text and optional boolean flags from a torrent JSON object. */
    fun isUserSeedingTorrent(t: JSONObject): Boolean {
        if (t.optBoolean("seeding", false)) return true
        if (t.optBoolean("userSeeding", false)) return true
        return isUserSeeding(t.optString("userStatus"))
    }

    /**
     * True if [obj] is a torrent you seed, or is group-style JSON whose `torrent` / `torrents` array
     * contains at least one such torrent (browse, bookmarks, notifications, etc.).
     */
    fun jsonIndicatesUserSeeding(obj: JSONObject): Boolean {
        if (isUserSeedingTorrent(obj)) return true
        val arr = obj.optJSONArray("torrent") ?: obj.optJSONArray("torrents") ?: return false
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            if (isUserSeedingTorrent(t)) return true
        }
        return false
    }

    /**
     * Uploader account id from a torrent or `user_torrents` row — same fields as
     * [com.turntable.barcodescanner.RedactedTorrentDetailActivity] uses for `torrent`.
     */
    fun uploaderUserIdFromJson(o: JSONObject): Int {
        val uploader = o.optJSONObject("user")
        if (uploader != null) {
            val id = uploader.optInt("id", 0)
            if (id > 0) return id
        }
        return o.optInt("userId", 0)
    }
}
