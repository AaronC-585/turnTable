package com.turntable.barcodescanner.redacted

import org.json.JSONArray

/**
 * Uses the Redacted `browse` API to suggest artist/album text (and optional cover path) for the Search screen.
 */
object RedactedSearchAssist {

    data class FirstBrowseHit(
        val secondaryTerms: String,
        /** Raw `cover` from JSON (URL or site-relative path). */
        val coverPathOrUrl: String?,
    )

    /**
     * First grouped browse hit formatted for the secondary search field, or null if none / error.
     */
    fun firstHit(apiKey: String, searchStr: String): FirstBrowseHit? {
        val q = searchStr.trim()
        if (q.isEmpty()) return null
        val api = RedactedApiClient(apiKey)
        val result = api.browse(
            listOf(
                "searchstr" to q,
                "page" to "1",
                "group_results" to "1",
            ),
        )
        val resp = when (result) {
            is RedactedResult.Success -> result.response ?: return null
            else -> return null
        }
        val arr: JSONArray = resp.optJSONArray("results") ?: return null
        if (arr.length() == 0) return null
        val o = arr.optJSONObject(0) ?: return null
        val artist = o.optString("artist").trim()
        val groupName = o.optString("groupName").trim()
        val terms = formatSecondaryTerms(artist, groupName)
        if (terms.isEmpty()) return null
        val cover = o.optString("cover").trim().takeIf { it.isNotEmpty() }
        return FirstBrowseHit(terms, cover)
    }

    fun formatSecondaryTerms(artist: String, groupName: String): String {
        val a = artist.trim()
        val g = groupName.trim()
        return when {
            a.isNotEmpty() && g.isNotEmpty() -> "$a - $g"
            g.isNotEmpty() -> g
            a.isNotEmpty() -> a
            else -> ""
        }
    }

    /** Absolute URL for cover assist / preview (matches [RedactedAvatarLoader] rules). */
    fun absoluteCoverUrl(raw: String): String {
        val t = raw.trim()
        if (t.startsWith("http", ignoreCase = true)) return t
        val path = t.trimStart('/')
        return "https://redacted.sh/$path"
    }
}
