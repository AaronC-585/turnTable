package com.turntable.barcodescanner.redacted

import android.text.format.DateUtils
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Relative time for Gazelle-style request timestamps from JSON. */
object RedactedRequestTime {

    private val patterns = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US),
    ).onEach { it.timeZone = TimeZone.getDefault() }

    fun relative(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return "—"
        for (fmt in patterns) {
            try {
                val ms = fmt.parse(t)?.time ?: continue
                return DateUtils.getRelativeTimeSpanString(
                    ms,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString()
            } catch (_: ParseException) {
                continue
            }
        }
        return t
    }
}
