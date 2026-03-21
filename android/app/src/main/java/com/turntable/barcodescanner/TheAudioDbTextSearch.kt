package com.turntable.barcodescanner

import org.json.JSONArray
import org.json.JSONObject

/**
 * TheAudioDB v2 text search only — **`X-API-KEY`** on every request ([docs](https://www.theaudiodb.com/free_music_api)):
 * `search/artist/{query}`, `search/album/{query}`.
 */
data class TheAudioDbSearchItem(
    /** Line 1 in chooser dialog */
    val title: String,
    /** Line 2 (genre, year, etc.) */
    val subtitle: String?,
    /** Fills secondary / tracker search field */
    val secondarySearchQuery: String,
    val coverImageUrl: String?,
)

object TheAudioDbTextSearch {

    fun searchArtists(apiKey: String?, query: String): List<TheAudioDbSearchItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val body = TheAudioDbApi.get("search/artist/${TheAudioDbApi.pathEncode(q)}", apiKey)
            ?: return emptyList()
        return parseArtistResults(body)
    }

    fun searchAlbums(apiKey: String?, query: String): List<TheAudioDbSearchItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val body = TheAudioDbApi.get("search/album/${TheAudioDbApi.pathEncode(q)}", apiKey)
            ?: return emptyList()
        return parseAlbumResults(body)
    }

    private fun parseArtistResults(json: String): List<TheAudioDbSearchItem> {
        val arr = extractArtistArray(json) ?: return emptyList()
        val out = ArrayList<TheAudioDbSearchItem>()
        for (i in 0 until arr.length()) {
            if (out.size >= 25) break
            val o = arr.optJSONObject(i) ?: continue
            itemFromArtistJson(o)?.let { out.add(it) }
        }
        return out
    }

    private fun parseAlbumResults(json: String): List<TheAudioDbSearchItem> {
        val arr = extractAlbumArray(json) ?: return emptyList()
        val out = ArrayList<TheAudioDbSearchItem>()
        for (i in 0 until arr.length()) {
            if (out.size >= 25) break
            val o = arr.optJSONObject(i) ?: continue
            itemFromAlbumJson(o)?.let { out.add(it) }
        }
        return out
    }

    private fun extractArtistArray(json: String): JSONArray? {
        return try {
            val root = JSONObject(json)
            when {
                root.optJSONArray("artists") != null -> root.getJSONArray("artists")
                root.optJSONArray("artist") != null -> root.getJSONArray("artist")
                root.has("data") -> {
                    val d = root.get("data")
                    when (d) {
                        is JSONArray -> d
                        is JSONObject -> d.optJSONArray("artists")
                            ?: d.optJSONArray("artist")
                            ?: d.optJSONArray("items")
                        else -> null
                    }
                }
                else -> firstJSONArrayWithKey(root, "strArtist")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAlbumArray(json: String): JSONArray? {
        return try {
            val root = JSONObject(json)
            when {
                root.optJSONArray("album") != null -> root.getJSONArray("album")
                root.optJSONArray("albums") != null -> root.getJSONArray("albums")
                root.has("data") -> {
                    val d = root.get("data")
                    when (d) {
                        is JSONArray -> d
                        is JSONObject -> d.optJSONArray("album")
                            ?: d.optJSONArray("albums")
                            ?: d.optJSONArray("items")
                        else -> null
                    }
                }
                else -> firstJSONArrayWithKey(root, "strAlbum")
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Heuristic: first JSONArray whose objects contain [field] */
    private fun firstJSONArrayWithKey(root: JSONObject, field: String): JSONArray? {
        val it = root.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = root.opt(k)
            if (v is JSONArray && v.length() > 0) {
                val first = v.optJSONObject(0) ?: continue
                if (first.has(field)) return v
            }
        }
        return null
    }

    private fun itemFromArtistJson(o: JSONObject): TheAudioDbSearchItem? {
        val name = o.optString("strArtist", "").trim()
            .ifBlank { o.optString("artistName", "").trim() }
            .ifBlank { o.optString("name", "").trim() }
        if (name.isBlank()) return null
        val subtitle = buildString {
            o.optString("strGenre", "").trim().takeIf { it.isNotBlank() }?.let { append(it) }
            o.optString("strCountry", "").trim().takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
        }.takeIf { it.isNotBlank() }
        val thumb = o.optString("strArtistThumb", "").trim()
            .ifBlank { o.optString("strThumb", "").trim() }
            .takeIf { it.isNotBlank() }
        return TheAudioDbSearchItem(
            title = name,
            subtitle = subtitle,
            secondarySearchQuery = if (SecondarySearchVariables.isPlaceholderCompilationArtist(name)) "" else name,
            coverImageUrl = thumb,
        )
    }

    private fun itemFromAlbumJson(o: JSONObject): TheAudioDbSearchItem? {
        val album = o.optString("strAlbum", "").trim()
            .ifBlank { o.optString("album", "").trim() }
        val artist = o.optString("strArtist", "").trim()
            .ifBlank { o.optString("artist", "").trim() }
        if (album.isBlank() && artist.isBlank()) return null
        val title = when {
            artist.isNotBlank() && album.isNotBlank() -> "$artist — $album"
            album.isNotBlank() -> album
            else -> artist
        }
        val year = o.optString("intYearReleased", "").trim()
            .ifBlank { o.optString("yearReleased", "").trim() }
        val subtitle = year.takeIf { it.isNotBlank() }
        val query = when {
            artist.isNotBlank() && album.isNotBlank() &&
                !SecondarySearchVariables.isPlaceholderCompilationArtist(artist) -> "$artist - $album"
            album.isNotBlank() -> album
            !SecondarySearchVariables.isPlaceholderCompilationArtist(artist) && artist.isNotBlank() -> artist
            else -> album
        }
        val thumb = o.optString("strAlbumThumb", "").trim()
            .ifBlank { o.optString("strAlbumThumbHQ", "").trim() }
            .takeIf { it.isNotBlank() }
        return TheAudioDbSearchItem(
            title = title,
            subtitle = subtitle,
            secondarySearchQuery = query,
            coverImageUrl = thumb,
        )
    }
}
