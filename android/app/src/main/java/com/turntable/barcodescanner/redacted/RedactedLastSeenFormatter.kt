package com.turntable.barcodescanner.redacted

import android.content.Context
import com.turntable.barcodescanner.R
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Turns Gazelle-style `stats.lastAccess` strings into short "last online … ago" text.
 */
object RedactedLastSeenFormatter {

    private val gazellePatterns: List<SimpleDateFormat> = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US),
        SimpleDateFormat("MMM d yyyy, HH:mm", Locale.US),
        SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.US),
    ).onEach { it.timeZone = TimeZone.getDefault() }

    /**
     * @param lastAccessRaw value from [com.turntable.barcodescanner.redacted.RedactedApiClient.user]
     *        `response.stats.lastAccess`, or null if not yet loaded.
     */
    fun lastOnlineSummary(context: Context, lastAccessRaw: String?): String {
        val raw = lastAccessRaw?.trim().orEmpty()
        if (raw.isEmpty()) {
            return context.getString(R.string.redacted_friends_last_seen_unknown)
        }
        val lower = raw.lowercase(Locale.US)
        when {
            "just now" in lower || raw.isBlank() ->
                return context.getString(R.string.redacted_friends_last_seen_just_now)
            "online" in lower && "now" in lower ->
                return context.getString(R.string.redacted_friends_last_seen_just_now)
        }

        val parsedMs = parseToUtcMillis(raw)
        if (parsedMs != null) {
            val now = System.currentTimeMillis()
            val secAgo = ((now - parsedMs) / 1000L).coerceAtLeast(0L)
            return formatRelativePast(context, secAgo)
        }

        // Site-specific relative text (e.g. "3 days ago") — show as-is with label
        return context.getString(R.string.redacted_friends_last_seen_as_reported, raw)
    }

    private fun parseToUtcMillis(raw: String): Long? {
        for (fmt in gazellePatterns) {
            try {
                return fmt.parse(raw)?.time
            } catch (_: ParseException) {
                continue
            }
        }
        return null
    }

    private fun formatRelativePast(context: Context, secondsAgo: Long): String {
        val s = secondsAgo
        val res = context.resources
        return when {
            s < 60L ->
                res.getQuantityString(
                    R.plurals.redacted_friends_last_online_seconds,
                    s.toInt().coerceAtLeast(1),
                    s.coerceAtLeast(1).toInt(),
                )
            s < 3600L -> {
                val m = (s / 60L).toInt().coerceAtLeast(1)
                res.getQuantityString(R.plurals.redacted_friends_last_online_minutes, m, m)
            }
            s < 86400L -> {
                val h = (s / 3600L).toInt().coerceAtLeast(1)
                res.getQuantityString(R.plurals.redacted_friends_last_online_hours, h, h)
            }
            s < 604800L -> {
                val d = (s / 86400L).toInt().coerceAtLeast(1)
                res.getQuantityString(R.plurals.redacted_friends_last_online_days, d, d)
            }
            s < 2592000L -> {
                val w = (s / 604800L).toInt().coerceAtLeast(1)
                res.getQuantityString(R.plurals.redacted_friends_last_online_weeks, w, w)
            }
            s < 31536000L -> {
                val mo = (s / 2592000L).toInt().coerceAtLeast(1)
                res.getQuantityString(R.plurals.redacted_friends_last_online_months, mo, mo)
            }
            else -> {
                val y = (s / 31536000L).toInt().coerceAtLeast(1)
                res.getQuantityString(R.plurals.redacted_friends_last_online_years, y, y)
            }
        }
    }
}
