package com.turntable.barcodescanner.redacted

import org.json.JSONObject
import java.util.Locale

/**
 * Interprets Redacted [index] `notifications` object for UI + OS alerts.
 * "Active" items: boolean [true], numeric counts [&gt; 0], string "yes" / "1".
 */
object RedactedIndexNotifications {

    /**
     * Unread PM count from [index] `notifications` (Gazelle-style `messages` field).
     * Returns 0 if missing or unparsable.
     */
    fun unreadPrivateMessageCount(notifications: JSONObject?): Int {
        if (notifications == null || notifications.length() == 0) return 0
        for (key in listOf("messages", "newMessages", "unreadMessages", "inbox")) {
            val n = parseNonNegativeCount(notifications, key)
            if (n != null) return n
        }
        return 0
    }

    private fun parseNonNegativeCount(obj: JSONObject, key: String): Int? {
        if (!obj.has(key)) return null
        return when (val v = obj.opt(key)) {
            null -> null
            JSONObject.NULL -> null
            is Number -> v.toInt().coerceAtLeast(0)
            is Boolean -> if (v) 1 else 0
            is String -> {
                val t = v.trim()
                when {
                    t.equals("yes", ignoreCase = true) || t == "1" -> 1
                    else -> t.toIntOrNull()?.coerceAtLeast(0)
                }
            }
            else -> null
        }
    }

    data class Evaluation(
        /** Any field indicates unread / new activity. */
        val shouldNotify: Boolean,
        val reasons: List<String>,
        /** Stable string for deduplication. */
        val snapshot: String,
    )

    fun evaluate(notifications: JSONObject?): Evaluation {
        if (notifications == null || notifications.length() == 0) {
            return Evaluation(false, emptyList(), "")
        }
        val reasons = mutableListOf<String>()
        val keys = notifications.keys().asSequence().sorted().toList()
        for (k in keys) {
            when (val v = notifications.opt(k)) {
                null -> {}
                is Boolean -> if (v) reasons.add("${humanize(k)}: yes")
                is Number -> if (v.toDouble() > 0.0) reasons.add("${humanize(k)}: $v")
                is String -> {
                    val t = v.trim()
                    if (t.equals("yes", ignoreCase = true) || t == "1") {
                        reasons.add("${humanize(k)}: $t")
                    }
                }
                else -> {}
            }
        }
        val snapshot = buildString {
            for (k in keys) {
                append(k)
                append('=')
                append(notifications.opt(k)?.toString() ?: "")
                append(';')
            }
        }
        return Evaluation(reasons.isNotEmpty(), reasons, snapshot)
    }

    private fun humanize(key: String): String =
        key.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}
