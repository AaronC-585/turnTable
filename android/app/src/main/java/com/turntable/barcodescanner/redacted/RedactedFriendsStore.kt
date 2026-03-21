package com.turntable.barcodescanner.redacted

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Locally saved Redacted "friends" (bookmarked users). The public JSON API does not expose
 * site friend lists; users add people via [RedactedApiClient.userSearch].
 */
object RedactedFriendsStore {

    private const val PREFS = "redacted_friends"
    private const val KEY_ENTRIES = "entries_json"

    data class Entry(
        val userId: Int,
        val username: String,
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<Entry> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optInt("userId", o.optInt("id"))
                    if (id <= 0) continue
                    val name = o.optString("username").trim()
                    add(Entry(userId = id, username = name.ifBlank { "#$id" }))
                }
            }.sortedBy { it.username.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        val seen = mutableSetOf<Int>()
        for (e in entries) {
            if (e.userId <= 0 || e.userId in seen) continue
            seen.add(e.userId)
            arr.put(
                JSONObject().apply {
                    put("userId", e.userId)
                    put("username", e.username)
                },
            )
        }
        prefs(context).edit { putString(KEY_ENTRIES, arr.toString()) }
    }

    fun add(context: Context, entry: Entry): Boolean {
        if (entry.userId <= 0) return false
        val current = load(context).toMutableList()
        if (current.any { it.userId == entry.userId }) return false
        current.add(entry)
        save(context, current)
        return true
    }

    fun remove(context: Context, userId: Int) {
        save(context, load(context).filter { it.userId != userId })
    }
}
