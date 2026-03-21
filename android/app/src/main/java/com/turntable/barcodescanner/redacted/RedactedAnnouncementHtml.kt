package com.turntable.barcodescanner.redacted

import org.json.JSONObject

/**
 * Builds HTML for [HtmlCompat.fromHtml] from API `body` (server-rendered) or `bbBody` (BBCode).
 */
object RedactedAnnouncementHtml {

    /** Prefer API `body` (HTML); otherwise convert `bbBody`. */
    fun contentHtml(o: JSONObject): String {
        val body = o.optString("body").trim()
        if (body.isNotEmpty()) return body
        return bbToHtml(o.optString("bbBody"), depth = 0)
    }

    private fun escapeXml(text: String): String = buildString(text.length + 8) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    private fun escapeXmlAttr(text: String): String =
        text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

    private fun nl2br(s: String): String =
        s.replace("\n", "<br/>")

    /**
     * Best-effort BBCode → HTML for announcements when `body` is empty.
     * Handles common Gazelle tags; nested [quote] up to [depth] levels.
     */
    fun bbToHtml(bb: String, depth: Int): String {
        if (bb.isBlank()) return ""
        if (depth > 4) return escapeXml(bb)
        var t = bb.replace("\r\n", "\n")

        // [code]…[/code] — literal, escaped
        t = Regex("""\[code](.*?)\[/code]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(t) { "<pre>${escapeXml(it.groupValues[1])}</pre>" }

        // [quote]…[/quote] — inner parsed recursively
        t = Regex("""\[quote](.*?)\[/quote]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(t) { "<blockquote>${bbToHtml(it.groupValues[1], depth + 1)}</blockquote>" }

        // Drop [size=] [color=] wrappers (keep inner text by stripping tags)
        t = Regex("""\[(size|color)=[^\]]+]""", RegexOption.IGNORE_CASE).replace(t, "")
        t = Regex("""\[/(size|color)]""", RegexOption.IGNORE_CASE).replace(t, "")

        t = Regex("""\[(/)?b]""", RegexOption.IGNORE_CASE).replace(t) {
            if (it.groupValues[1] == "/") "</b>" else "<b>"
        }
        t = Regex("""\[(/)?i]""", RegexOption.IGNORE_CASE).replace(t) {
            if (it.groupValues[1] == "/") "</i>" else "<i>"
        }
        t = Regex("""\[(/)?u]""", RegexOption.IGNORE_CASE).replace(t) {
            if (it.groupValues[1] == "/") "</u>" else "<u>"
        }
        t = Regex("""\[(/)?s]""", RegexOption.IGNORE_CASE).replace(t) {
            if (it.groupValues[1] == "/") "</s>" else "<s>"
        }

        t = Regex("""\[url=([^\]]+)]\s*(.*?)\s*\[/url]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(t) { m ->
                val rawHref = m.groupValues[1].trim()
                val href = when {
                    rawHref.startsWith("http://", true) || rawHref.startsWith("https://", true) -> rawHref
                    rawHref.startsWith("//") -> "https:$rawHref"
                    else -> "https://redacted.sh/${rawHref.trimStart('/')}"
                }
                val label = escapeXml(m.groupValues[2]).ifBlank { escapeXml(href) }
                """<a href="${escapeXmlAttr(href)}">$label</a>"""
            }

        t = Regex("""\[url]\s*(https?://[^\[]+?)\s*\[/url]""", RegexOption.IGNORE_CASE)
            .replace(t) {
                val u = it.groupValues[1].trim()
                val esc = escapeXmlAttr(u)
                """<a href="$esc">${escapeXml(u)}</a>"""
            }

        t = Regex("""\[img]\s*(.+?)\s*\[/img]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(t) {
                val u = escapeXmlAttr(it.groupValues[1].trim())
                """<br/><img src="$u"/><br/>"""
            }

        // Lists: [*]item -> bullets (very loose)
        t = Regex("""\[\*]""", RegexOption.IGNORE_CASE).replace(t, "• ")
        t = Regex("""\[/?list]""", RegexOption.IGNORE_CASE).replace(t, "<br/>")

        t = nl2br(t)

        // Strip any remaining lone [tags]
        t = Regex("""\[/[^\]]+]""", RegexOption.IGNORE_CASE).replace(t, "")
        t = Regex("""\[[^\]]+]""").replace(t, "")

        return t
    }
}
