package com.turntable.barcodescanner.redacted

import org.json.JSONArray
import org.json.JSONObject

/**
 * Formats Redacted [index] `response` for the home screen: all [userstats] keys plus related fields.
 */
object RedactedHomeStatsFormatter {

    fun formatStatsBlock(resp: JSONObject?): String {
        if (resp == null) return ""
        val lines = mutableListOf<String>()
        val id = resp.optInt("id", -1)
        if (id >= 0) lines.add("id: $id")
        resp.optString("api_version").takeIf { it.isNotBlank() }?.let { lines.add("api_version: $it") }

        resp.optJSONObject("notifications")?.let { n ->
            val keys = n.keys().asSequence().toList().sorted()
            for (k in keys) {
                lines.add("notifications.$k: ${formatLeaf(n.get(k))}")
            }
        }

        val stats = resp.optJSONObject("userstats")
        if (stats != null) {
            val keys = stats.keys().asSequence().toList().sorted()
            for (k in keys) {
                lines.add("$k: ${formatLeaf(stats.get(k))}")
            }
        } else {
            // Some API shapes may expose stats at top level — include other primitive fields
            val skip = setOf("username", "id", "api_version", "notifications", "userstats")
            val rest = resp.keys().asSequence().filter { it !in skip }.sorted().toList()
            for (k in rest) {
                val v = resp.get(k)
                if (v is JSONObject || v is JSONArray) continue
                lines.add("$k: ${formatLeaf(v)}")
            }
        }
        return lines.joinToString("\n")
    }

    /** Best-effort avatar URL from [user] action response object. */
    fun avatarUrlFromUserResponse(resp: JSONObject?): String? {
        if (resp == null) return null
        listOf("avatar", "Avatar").forEach { key ->
            resp.optString(key).takeIf { it.isNotBlank() }?.let { return it }
        }
        // Nested shapes seen on some Gazelle forks
        resp.optJSONObject("personal")?.optString("avatar")?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun formatLeaf(value: Any?): String = when (value) {
        null -> "—"
        is String -> value
        is Number, is Boolean -> value.toString()
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        else -> value.toString()
    }
}
