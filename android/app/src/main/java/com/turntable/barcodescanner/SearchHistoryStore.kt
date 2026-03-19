package com.turntable.barcodescanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SearchHistoryEntry(
    val timestampMs: Long,
    val barcode: String,
    val query: String,
)

object SearchHistoryStore {
    private const val KEY_HISTORY_JSON = "search_history_json"
    private const val MAX_ENTRIES = 100

    fun add(context: Context, barcode: String, query: String) {
        val cleanBarcode = barcode.trim()
        val cleanQuery = query.trim()
        if (cleanBarcode.isEmpty() && cleanQuery.isEmpty()) return
        val current = getAll(context).toMutableList()
        current.add(
            0,
            SearchHistoryEntry(
                timestampMs = System.currentTimeMillis(),
                barcode = cleanBarcode,
                query = cleanQuery,
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
                            query = o.optString("query", ""),
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
                    put("query", e.query)
                },
            )
        }
        context.getSharedPreferences(SearchPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY_JSON, arr.toString())
            .apply()
    }
}
