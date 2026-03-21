package com.turntable.barcodescanner.redacted

import org.json.JSONObject

/**
 * Groups torrents on a release page by **pressing / edition** (Gazelle remaster tuple + media),
 * matching the site `torrent_details` edition headers.
 */
object RedactedGazelleEdition {

    /** Stable key: torrents sharing this belong to one edition block. */
    fun groupKey(t: JSONObject): String {
        val rem = t.optBoolean("remastered", false)
        return listOf(
            if (rem) "1" else "0",
            t.optInt("remasterYear", 0).toString(),
            t.optString("remasterTitle").trim(),
            t.optString("remasterRecordLabel").trim(),
            t.optString("remasterCatalogueNumber").trim(),
            t.optString("media").trim(),
        ).joinToString("\u0001")
    }

    fun sortYear(group: JSONObject, t: JSONObject): Int {
        val gy = group.optInt("year", 0)
        return if (t.optBoolean("remastered", false) && t.optInt("remasterYear", 0) > 0) {
            t.optInt("remasterYear")
        } else {
            gy
        }
    }

    /**
     * One line like the site edition row, e.g. `2024 / Shady / Aftermath / Interscope / cat / Vinyl`.
     */
    fun buildEditionHeaderTitle(group: JSONObject, representative: JSONObject): String {
        val gy = group.optInt("year", 0)
        val gLabel = group.optString("recordLabel").trim()
        val gCat = group.optString("catalogueNumber").trim()
        val rem = representative.optBoolean("remastered", false)
        val y = if (rem && representative.optInt("remasterYear", 0) > 0) {
            representative.optInt("remasterYear")
        } else {
            gy
        }
        val label = representative.optString("remasterRecordLabel").trim().ifBlank { gLabel }
        val cat = representative.optString("remasterCatalogueNumber").trim().ifBlank { gCat }
        val remTitle = representative.optString("remasterTitle").trim()
        val media = representative.optString("media").trim()
        return buildList {
            if (y > 0) add(y.toString())
            if (label.isNotEmpty()) add(label)
            if (cat.isNotEmpty()) add(cat)
            if (remTitle.isNotEmpty()) add(remTitle)
            if (media.isNotEmpty()) add(media)
        }.joinToString(" / ").ifBlank { "—" }
    }

    /**
     * @return ordered list of edition buckets, each a non-empty list of torrent JSON objects.
     */
    fun groupTorrentsByEdition(group: JSONObject, torrents: List<JSONObject>): List<List<JSONObject>> {
        if (torrents.isEmpty()) return emptyList()
        val byKey = LinkedHashMap<String, MutableList<JSONObject>>()
        for (t in torrents) {
            val k = groupKey(t)
            byKey.getOrPut(k) { mutableListOf() }.add(t)
        }
        val buckets = byKey.values.toMutableList()
        buckets.sortWith { a, b ->
            val ta = a.first()
            val tb = b.first()
            val ya = sortYear(group, ta)
            val yb = sortYear(group, tb)
            if (ya != yb) return@sortWith ya.compareTo(yb)
            groupKey(ta).compareTo(groupKey(tb))
        }
        for (bucket in buckets) {
            bucket.sortWith { a, b ->
                val fa = a.optString("format").compareTo(b.optString("format"))
                if (fa != 0) return@sortWith fa
                val ea = a.optString("encoding").compareTo(b.optString("encoding"))
                if (ea != 0) return@sortWith ea
                a.optLong("size").compareTo(b.optLong("size"))
            }
        }
        return buckets
    }
}
