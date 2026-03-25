package com.turntable.barcodescanner.redacted

import org.json.JSONObject
import java.net.URLEncoder

/**
 * Builds HTML for [HtmlCompat.fromHtml] from API `body` (server-rendered) or `bbBody` (BBCode).
 */
object RedactedAnnouncementHtml {

    private val imgTagRegex = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)

    /**
     * `src` values from `<img>` tags in announcement HTML / BBCode output (absolute URLs or site-relative).
     */
    fun extractImageSrcs(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val srcRegex =
            Regex("""<img[^>]+src\s*=\s*["']?([^"'>\s]+)["']?""", RegexOption.IGNORE_CASE)
        return srcRegex.findAll(html)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    /**
     * Turns an `<img src>` value into a full URL for HTTP fetch, or null if [src] is not allowed
     * ([RedactedHtmlSafe.isSafeImageSrcAttribute]).
     */
    fun absolutizeImgSrcForFetch(src: String): String? {
        val s = src.trim()
        if (s.isEmpty() || !RedactedHtmlSafe.isSafeImageSrcAttribute(s)) return null
        return when {
            s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true) -> s
            s.startsWith("//") -> "https:$s"
            else -> "https://redacted.sh/${s.trimStart('/')}"
        }
    }

    /** Removes `<img …>` so [HtmlCompat.fromHtml] in a [android.widget.TextView] does not show broken placeholders. */
    fun stripImgTags(html: String): String =
        if (html.isBlank()) html else imgTagRegex.replace(html, "")

    /** Prefer API `body` (HTML); otherwise convert `bbBody`. Sanitized for [androidx.core.text.HtmlCompat.fromHtml]. */
    fun contentHtml(o: JSONObject): String {
        val body = o.optString("body").trim()
        val raw = if (body.isNotEmpty()) body else bbToHtml(o.optString("bbBody"), depth = 0)
        return RedactedHtmlSafe.sanitizeHtmlForTextView(raw)
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

    private fun urlEncodeQueryComponent(text: String): String =
        URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")

    /** Absolute URL for BBCode `[img]`, `[img=]`, `[url]`, and `[url=]` (http(s), `//`, or Redacted-relative). */
    private fun absolutizeBbImgSrc(raw: String): String {
        val t = raw.trim()
        return when {
            t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) -> t
            t.startsWith("//") -> "https:$t"
            else -> "https://redacted.sh/${t.trimStart('/')}"
        }
    }

    private fun nl2br(s: String): String =
        s.replace("\n", "<br/>")

    /**
     * Best-effort BBCode → HTML for announcements when `body` is empty.
     * Handles common Gazelle tags; nested [quote] up to [depth] levels.
     */
    fun bbToHtml(bb: String, depth: Int): String {
        if (bb.isBlank()) return ""
        if (depth > 4) return escapeXml(bb)
        var t = RedactedHtmlEntities.decodeCharacterReferences(bb.replace("\r\n", "\n"))

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
                val href = absolutizeBbImgSrc(rawHref)
                val label = escapeXml(m.groupValues[2]).ifBlank { escapeXml(href) }
                """<a href="${escapeXmlAttr(href)}">$label</a>"""
            }

        // [url]…[/url] — any href (relative, //, http), same resolution as [url=…]
        t = Regex("""\[url]\s*(.+?)\s*\[/url]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(t) { m ->
                val raw = m.groupValues[1].trim()
                if (raw.isEmpty()) return@replace m.value
                val href = absolutizeBbImgSrc(raw)
                val esc = escapeXmlAttr(href)
                val label = escapeXml(raw)
                """<a href="$esc">$label</a>"""
            }

        // [user]name[/user] → site user search by username (same as web).
        t = Regex("""\[user]\s*(.*?)\s*\[/user]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(t) { m ->
                val name = m.groupValues[1].trim()
                if (name.isEmpty()) return@replace m.value
                val href = "https://redacted.sh/user.php?action=search&username=${urlEncodeQueryComponent(name)}"
                """<a href="${escapeXmlAttr(href)}">${escapeXml(name)}</a>"""
            }

        // [img=WxH]url[/img], [img=url]body[/img], or [img=url] with URL in = (Gazelle) — before plain [img]…[/img].
        t = Regex("""\[img=([^\]]+)]\s*(.+?)\s*\[/img]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(t) { m ->
                val arg = m.groupValues[1].trim()
                val body = m.groupValues[2].trim()
                val isDim = Regex("""^\d+\s*x\s*\d+$""", RegexOption.IGNORE_CASE).matches(arg)
                val argIsUrl = arg.startsWith("http://", ignoreCase = true) ||
                    arg.startsWith("https://", ignoreCase = true) ||
                    arg.startsWith("//", ignoreCase = true)
                val srcRaw = when {
                    isDim -> body
                    argIsUrl -> arg
                    else -> body.ifBlank { arg }
                }
                val esc = escapeXmlAttr(absolutizeBbImgSrc(srcRaw))
                if (isDim) {
                    val dims = arg.lowercase().split('x')
                    val w = dims.getOrNull(0)?.trim()?.toIntOrNull()
                    val h = dims.getOrNull(1)?.trim()?.toIntOrNull()
                    val wh = buildString {
                        if (w != null) append(" width=\"").append(w).append("\"")
                        if (h != null) append(" height=\"").append(h).append("\"")
                    }
                    """<br/><img src="$esc"$wh/><br/>"""
                } else {
                    """<br/><img src="$esc"/><br/>"""
                }
            }

        t = Regex("""\[img]\s*(.+?)\s*\[/img]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(t) {
                val u = escapeXmlAttr(absolutizeBbImgSrc(it.groupValues[1].trim()))
                """<br/><img src="$u"/><br/>"""
            }

        // [img=url] without [/img] — same as [img]url[/img]
        t = Regex("""\[img=([^\]]+)]""", RegexOption.IGNORE_CASE).replace(t) { m ->
            val raw = m.groupValues[1].trim()
            if (Regex("""^\d+\s*x\s*\d+$""", RegexOption.IGNORE_CASE).matches(raw)) {
                m.value
            } else {
                val u = escapeXmlAttr(absolutizeBbImgSrc(raw))
                """<br/><img src="$u"/><br/>"""
            }
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
