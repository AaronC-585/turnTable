package com.turntable.barcodescanner

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * All values that can be substituted into secondary search **GET** URLs and **POST** bodies.
 * [query] is the full “Artist - Album” (or free text) from the search screen.
 */
data class SecondarySearchVariables(
    val barcode: String,
    val notes: String,
    val category: String,
    val query: String,
    val coverUrl: String?,
) {
    private val queryParts: List<String> by lazy {
        splitArtistAlbumQuery(query)
    }

    private val segArtist: String get() = queryParts.getOrNull(0).orEmpty()
    private val segAlbum: String get() = queryParts.getOrNull(1).orEmpty()

    /** First segment unless it is a compilation placeholder (“Various”); then empty. */
    val artist: String get() = if (isPlaceholderCompilationArtist(segArtist)) "" else segArtist

    /** Second segment (album/title after “ - ” / “ — ”). */
    val album: String get() = segAlbum

    /**
     * For `%artist%`: never use “Various*” as the artist token; use empty so trackers get album-only or `%album%`.
     * If the first segment is empty (no delimiter), falls back to full [query] (unchanged legacy).
     */
    fun artistOrQuery(): String {
        if (isPlaceholderCompilationArtist(segArtist)) return ""
        return segArtist.ifBlank { query.trim() }
    }

    /**
     * For `%album%`: when the artist is a compilation placeholder, prefer the title segment only
     * (do not fall back to the full string, which would repeat “Various”).
     */
    fun albumOrQuery(): String {
        if (isPlaceholderCompilationArtist(segArtist)) {
            return segAlbum.ifBlank { "" }
        }
        return segAlbum.ifBlank { query.trim() }
    }

    /**
     * For `%query%`, `%s` (GET), and JSON `%query%` — drops “Various* - ” so the tracker search is album/title only.
     */
    fun queryForTracker(): String {
        if (isPlaceholderCompilationArtist(segArtist)) {
            return segAlbum.ifBlank { "" }
        }
        return query.trim()
    }

    companion object {
        /** True when the credited artist is a non-specific compilation label (do not send to tracker as artist). */
        fun isPlaceholderCompilationArtist(artist: String): Boolean {
            val s = artist.trim().lowercase(Locale.US)
            return when (s) {
                "various", "various artists", "various artist", "va" -> true
                else -> false
            }
        }

        /**
         * Split “Artist - Album” or “Artist — Album” into at most two segments (trimmed).
         */
        fun splitArtistAlbumQuery(raw: String): List<String> {
            val t = raw.trim()
            if (t.isEmpty()) return listOf("")
            val em = t.split(" — ", limit = 2).map { it.trim() }
            if (em.size == 2) return em
            return t.split(" - ", limit = 2).map { it.trim() }
        }
    }
}

/**
 * Substitutes placeholders into secondary URL templates and POST bodies.
 *
 * **GET URL** — values are UTF-8 **application/x-www-form-urlencoded** (safe for query strings).
 *
 * **POST body** — values are **raw** UTF-8 (not URL-encoded). Use *%…_json%* variants inside JSON.
 * Legacy: **%s** in POST still means **barcode**; **$code**, **$notes**, **$category** unchanged.
 */
object SecondarySearchSubstitution {

    private fun enc(s: String): String =
        URLEncoder.encode(s, StandardCharsets.UTF_8.name())

    fun substituteUrl(template: String, v: SecondarySearchVariables): String {
        val q = v.queryForTracker()
        val art = v.artistOrQuery()
        val alb = v.albumOrQuery()
        val cover = v.coverUrl.orEmpty()
        return template
            .replace("%coverurl%", enc(cover))
            .replace("%cover%", enc(cover))
            .replace("%category%", enc(v.category))
            .replace("%barcode%", enc(v.barcode))
            .replace("%notes%", enc(v.notes))
            .replace("%artist%", enc(art))
            .replace("%album%", enc(alb))
            .replace("%query%", enc(q))
            .replace("%s", enc(q))
    }

    fun substitutePostBody(template: String, v: SecondarySearchVariables): String {
        val q = v.queryForTracker()
        val art = v.artistOrQuery()
        val alb = v.albumOrQuery()
        val cover = v.coverUrl.orEmpty()
        // Longer tokens first: %album_json% contains %album%, %cover_json% contains %cover%, etc.
        return template
            .replace("%cover_json%", JSONObject.quote(cover))
            .replace("%category_json%", JSONObject.quote(v.category))
            .replace("%notes_json%", JSONObject.quote(v.notes))
            .replace("%barcode_json%", JSONObject.quote(v.barcode))
            .replace("%query_json%", JSONObject.quote(q))
            .replace("%artist_json%", JSONObject.quote(art))
            .replace("%album_json%", JSONObject.quote(alb))
            .replace("%coverurl%", cover)
            .replace("%category%", v.category)
            .replace("%notes%", v.notes)
            .replace("%barcode%", v.barcode)
            .replace("%query%", q)
            .replace("%artist%", art)
            .replace("%album%", alb)
            .replace("%cover%", cover)
            .replace("%s", v.barcode)
            .replace("\$code", v.barcode)
            .replace("\$notes", v.notes)
            .replace("\$category", v.category)
    }

    /** Ordered list for help UI: placeholder → short description. */
    val urlVariableRows: List<Pair<String, String>> = listOf(
        "%s" to "Search query for tracker (URL-encoded); omits “Various*” as sole artist",
        "%query%" to "Same as %s",
        "%barcode%" to "Scanned barcode (URL-encoded)",
        "%artist%" to "Text before \" - \" / \" — \"; empty if artist is Various*; else segment or full query",
        "%album%" to "Text after delimiter; if Various*, album only (no fallback to full query)",
        "%notes%" to "Notes field (URL-encoded)",
        "%category%" to "Category field (URL-encoded)",
        "%cover%" to "Cover image URL (URL-encoded)",
        "%coverurl%" to "Same as %cover%",
    )

    val postVariableRows: List<Pair<String, String>> = listOf(
        "%s" to "Scanned barcode (legacy POST)",
        "\$code" to "Scanned barcode",
        "\$notes" to "Notes field",
        "\$category" to "Category field",
        "%barcode%" to "Scanned barcode",
        "%query%" to "Tracker query (Various* artist omitted)",
        "%artist%" to "First segment; empty for Various*",
        "%album%" to "Second segment; Various* → album only",
        "%notes%" to "Notes",
        "%category%" to "Category",
        "%cover%" to "Cover image URL",
        "%barcode_json%" to "Barcode as JSON string literal",
        "%query_json%" to "Query as JSON string literal",
        "%artist_json%" to "Artist as JSON string literal",
        "%album_json%" to "Album as JSON string literal",
        "%notes_json%" to "Notes as JSON string literal",
        "%category_json%" to "Category as JSON string literal",
        "%cover_json%" to "Cover URL as JSON string literal",
    )
}
