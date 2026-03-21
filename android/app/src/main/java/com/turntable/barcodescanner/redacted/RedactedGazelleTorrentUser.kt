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
}
