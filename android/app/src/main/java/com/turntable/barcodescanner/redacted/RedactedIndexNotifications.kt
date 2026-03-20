package com.turntable.barcodescanner.redacted

import org.json.JSONObject
import java.util.Locale

/**
 * Interprets Redacted [index] `notifications` object for UI + OS alerts.
 * "Active" items: boolean [true], numeric counts [&gt; 0], string "yes" / "1".
 */
object RedactedIndexNotifications {

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
