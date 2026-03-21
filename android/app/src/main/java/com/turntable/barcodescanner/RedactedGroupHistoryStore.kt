package com.turntable.barcodescanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Recently opened Redacted torrent groups (from [RedactedTorrentGroupActivity]).
 * Same [groupId] is deduped: most recent visit wins.
 */
data class RedactedGroupHistoryEntry(
    val timestampMs: Long,
    val groupId: Int,
    /** Release title */
    val groupName: String,
    /** e.g. artist · year */
    val subtitle: String,
    val coverUrl: String?,
)

object RedactedGroupHistoryStore {
    private const val KEY_JSON = "redacted_group_history_json"
    private const val MAX_ENTRIES = 150

    fun add(
        context: Context,
        groupId: Int,
        groupName: String,
        subtitle: String,
        coverUrl: String?,
    ) {
        if (groupId <= 0) return
        val name = groupName.trim().ifBlank { "—" }
        val sub = subtitle.trim().ifBlank { "—" }
        val cover = coverUrl?.trim()?.takeIf { it.isNotBlank() }
        val current = getAll(context).filter { it.groupId != groupId }.toMutableList()
        current.add(
            0,
            RedactedGroupHistoryEntry(
                timestampMs = System.currentTimeMillis(),
                groupId = groupId,
                groupName = name,
                subtitle = sub,
                coverUrl = cover,
            ),
        )
        save(context, current.take(MAX_ENTRIES))
    }

    fun getAll(context: Context): List<RedactedGroupHistoryEntry> {
        val raw = context
            .getSharedPreferences(SearchPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        RedactedGroupHistoryEntry(
                            timestampMs = o.optLong("timestampMs", 0L),
                            groupId = o.optInt("groupId", 0),
                            groupName = o.optString("groupName", ""),
                            subtitle = o.optString("subtitle", ""),
                            coverUrl = o.optString("coverUrl", "").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }.filter { it.groupId > 0 }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(SearchPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_JSON)
            .apply()
    }

    private fun save(context: Context, entries: List<RedactedGroupHistoryEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject().apply {
                    put("timestampMs", e.timestampMs)
                    put("groupId", e.groupId)
                    put("groupName", e.groupName)
                    put("subtitle", e.subtitle)
                    put("coverUrl", e.coverUrl ?: "")
                },
            )
        }
        context.getSharedPreferences(SearchPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, arr.toString())
            .apply()
    }
}
