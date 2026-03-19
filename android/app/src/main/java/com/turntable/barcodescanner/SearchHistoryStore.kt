package com.turntable.barcodescanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SearchHistoryEntry(
    val timestampMs: Long,
    val barcode: String,
    val title: String,
    val coverUrl: String?,
)

object SearchHistoryStore {
    private const val KEY_HISTORY_JSON = "search_history_json"
    private const val MAX_ENTRIES = 100

    fun add(context: Context, barcode: String, title: String, coverUrl: String?) {
        val cleanBarcode = barcode.trim()
        val cleanTitle = title.trim()
        val cleanCover = coverUrl?.trim()?.takeIf { it.isNotBlank() }
        if (cleanBarcode.isEmpty() && cleanTitle.isEmpty()) return
        val current = getAll(context).toMutableList()
        current.add(
            0,
            SearchHistoryEntry(
                timestampMs = System.currentTimeMillis(),
                barcode = cleanBarcode,
                title = cleanTitle,
                coverUrl = cleanCover,
            ),
        )
        save(context, current.take(MAX_ENTRIES))
    }

    fun getAll(context: Context): List<SearchHistoryEntry> {
        val raw = context
            .getSharedPreferences(SearchPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY_JSON, null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        SearchHistoryEntry(
                            timestampMs = o.optLong("timestampMs", 0L),
                            barcode = o.optString("barcode", ""),
                            // Backward-compatible migration from older `query` key.
                            title = o.optString("title", o.optString("query", "")),
                            coverUrl = o.optString("coverUrl", "").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, entries: List<SearchHistoryEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject().apply {
                    put("timestampMs", e.timestampMs)
                    put("barcode", e.barcode)
                    put("title", e.title)
                    put("coverUrl", e.coverUrl ?: "")
                },
            )
        }
        context.getSharedPreferences(SearchPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY_JSON, arr.toString())
            .apply()
    }
}
