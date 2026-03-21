package com.turntable.barcodescanner.redacted

import org.json.JSONObject

/**
 * Freeleech / FL token balance from Redacted [index] JSON (and similar shapes).
 * Used to hide "use FL token" download options when the user has no tokens.
 */
object RedactedFreeleechTokens {

    /**
     * Parses [root] from a successful `index` API call (`status` + optional `response` wrapper).
     */
    fun countFromIndexRoot(successRoot: JSONObject): Int {
        val payload = successRoot.optJSONObject("response") ?: successRoot
        return countFromPayload(payload)
    }

    private fun countFromPayload(payload: JSONObject): Int {
        val u = payload.optJSONObject("userstats")
        val fromStats = countFromUserstats(u)
        if (fromStats > 0) return fromStats
        if (u != null) return fromStats
        for (k in listOf("tokenCount", "tokens")) {
            parseTokenNumber(payload, k)?.let { return it }
        }
        return 0
    }

    /**
     * Best-effort count from a `userstats` object. Missing keys → 0 (do not offer token download).
     */
    fun countFromUserstats(stats: JSONObject?): Int {
        if (stats == null) return 0
        val keys = listOf(
            "tokenCount",
            "tokens",
            "flTokens",
            "freeleechTokens",
            "meritTokens",
        )
        for (k in keys) {
            val n = parseTokenNumber(stats, k)
            if (n != null) return n
        }
        return 0
    }

    private fun parseTokenNumber(o: JSONObject, key: String): Int? {
        if (!o.has(key) || o.isNull(key)) return null
        return when (val raw = o.get(key)) {
            is Int -> raw.coerceAtLeast(0)
            is Long -> raw.toInt().coerceAtLeast(0)
            is Double -> raw.toInt().coerceAtLeast(0)
            is Float -> raw.toInt().coerceAtLeast(0)
            is String -> raw.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
            else -> null
        }
    }
}
