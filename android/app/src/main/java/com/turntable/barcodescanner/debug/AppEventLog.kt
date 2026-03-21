package com.turntable.barcodescanner.debug

import com.turntable.barcodescanner.R
import java.util.Collections

/**
 * In-memory ring buffer of user-visible events for the test / debug log (vol↑ + shake).
 * Thread-safe; newest entries appended at the end (oldest first in [snapshot]).
 */
object AppEventLog {

    const val MAX_ENTRIES = 1000
    /** Single-line storage cap after newline folding (crash stacks can be large). */
    private const val MAX_MESSAGE_CHARS = 24_000

    enum class Category(val label: String, val colorRes: Int) {
        SCAN("SCAN", R.color.debug_log_scan),
        SEARCH("SEARCH", R.color.debug_log_search),
        REDACTED("REDACTED", R.color.debug_log_redacted),
        SYSTEM("SYSTEM", R.color.debug_log_system),
        ERROR("ERROR", R.color.debug_log_error),
    }

    data class Entry(
        val timeMs: Long,
        val category: Category,
        val message: String,
    )

    private val entries = Collections.synchronizedList(ArrayList<Entry>(512))

    fun log(category: Category, message: String) {
        var line = message.trim().replace("\n", " ↳ ")
        if (line.length > MAX_MESSAGE_CHARS) {
            line = line.take(MAX_MESSAGE_CHARS) + " ↳ …(truncated)"
        }
        if (line.isEmpty()) return
        synchronized(entries) {
            entries.add(Entry(System.currentTimeMillis(), category, line))
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
        }
    }

    /** Copy for UI / export (chronological, oldest first). */
    fun snapshot(): List<Entry> = synchronized(entries) {
        ArrayList(entries)
    }

    fun formatPlainText(): String = buildString {
        for (e in snapshot()) {
            append(DebugEventLogFormatter.formatTimestamp(e.timeMs))
            append(" | ")
            append(e.category.label)
            append(" | ")
            append(e.message)
            append('\n')
        }
    }
}

object DebugEventLogFormatter {
    private val threadLocalFmt = ThreadLocal.withInitial {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
    }

    fun formatTimestamp(ms: Long): String =
        threadLocalFmt.get()!!.format(java.util.Date(ms))
}
