package com.turntable.barcodescanner.redacted

/**
 * Parses Gazelle/Redacted `fileList` strings from [torrentgroup] / [torrent] JSON:
 * `name{{{bytes}}}|||name2{{{bytes2}}}`.
 */
object RedactedGazelleTorrentParse {

    data class ListedFile(val name: String, val sizeBytes: Long)

    fun parseFileList(raw: String?): List<ListedFile> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("|||").mapNotNull { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val open = trimmed.lastIndexOf("{{{")
            val close = trimmed.lastIndexOf("}}}")
            if (open <= 0 || close <= open + 3) {
                return@mapNotNull ListedFile(trimmed, 0L)
            }
            val name = trimmed.substring(0, open)
            val num = trimmed.substring(open + 3, close).toLongOrNull() ?: 0L
            ListedFile(name, num)
        }
    }

    /**
     * Converts common HTML to plain-text equivalents (newlines, bullets, **bold**, *italic*, etc.).
     * Not a full HTML parser; nested edge cases may simplify oddly.
     */
    fun htmlToPlainTextEquivalents(html: String): String {
        if (html.isBlank()) return ""
        var t = html
        // Numeric / hex character references
        t = Regex("""&#(\d{1,7});""").replace(t) { m ->
            val cp = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            if (cp in 0..0x10FFFF) String(Character.toChars(cp)) else m.value
        }
        t = Regex("""&#x([0-9a-fA-F]{1,6});""").replace(t) { m ->
            val cp = m.groupValues[1].toIntOrNull(16) ?: return@replace m.value
            if (cp in 0..0x10FFFF) String(Character.toChars(cp)) else m.value
        }
        // Common named entities
        t = t.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&hellip;", "…")
        // Line / block breaks
        t = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE).replace(t, "\n────────\n")
        t = Regex("""</p>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""<p[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""</div>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""<div[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""</h[1-6]>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""<h[1-6][^>]*>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""</?(?:section|article|header|footer|main|figure|figcaption)>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        // Lists
        t = Regex("""<ul[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""</ul>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""<ol[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""</ol>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""<li[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "\n• ")
        t = Regex("""</li>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        // Links: keep visible text + URL (strip nested tags inside anchor text)
        val linkOpts = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        fun replaceAnchors(input: String, quote: String): String {
            val q = Regex.escape(quote)
            val pat = """<a\s+[^>]*href\s*=\s*$q([^$q]*)$q[^>]*>(.*?)</a>"""
            return Regex(pat, linkOpts).replace(input) { m ->
                val href = m.groupValues[1].trim()
                val inner = m.groupValues[2].replace(Regex("""<[^>]+>"""), "").trim()
                if (inner.isBlank()) href else "$inner ($href)"
            }
        }
        t = replaceAnchors(t, "\"")
        t = replaceAnchors(t, "'")
        t = Regex("""<a\s+[^>]*href\s*=\s*([^\s>]+)[^>]*>(.*?)</a>""", linkOpts).replace(t) { m ->
            val href = m.groupValues[1].trim().trimEnd('>', '/')
            val inner = m.groupValues[2].replace(Regex("""<[^>]+>"""), "").trim()
            if (inner.isBlank()) href else "$inner ($href)"
        }
        // Images
        t = Regex("""<img[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "\n[image]\n")
        // Inline emphasis (markdown-style markers)
        t = Regex("""<(?:b|strong)[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "**")
        t = Regex("""</(?:b|strong)>""", RegexOption.IGNORE_CASE).replace(t, "**")
        t = Regex("""<(?:i|em)[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "*")
        t = Regex("""</(?:i|em)>""", RegexOption.IGNORE_CASE).replace(t, "*")
        t = Regex("""<u[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "_")
        t = Regex("""</u>""", RegexOption.IGNORE_CASE).replace(t, "_")
        t = Regex("""<(?:s|strike|del)[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "~~")
        t = Regex("""</(?:s|strike|del)>""", RegexOption.IGNORE_CASE).replace(t, "~~")
        t = Regex("""<(?:sub|sup)[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "")
        t = Regex("""</(?:sub|sup)>""", RegexOption.IGNORE_CASE).replace(t, "")
        // Code / pre
        t = Regex("""<pre[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""</pre>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("""<code[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "`")
        t = Regex("""</code>""", RegexOption.IGNORE_CASE).replace(t, "`")
        t = Regex("""<blockquote[^>]*>""", RegexOption.IGNORE_CASE).replace(t, "\n> ")
        t = Regex("""</blockquote>""", RegexOption.IGNORE_CASE).replace(t, "\n")
        // Drop any remaining tags
        t = Regex("""<[^>]+>""").replace(t, "")
        // Tidy whitespace
        t = Regex("""[ \t]+\n""").replace(t, "\n")
        t = Regex("""\n{3,}""").replace(t, "\n\n")
        return t.trim()
    }

    /** Very small BBCode strip for wiki / description preview (not a full parser). */
    fun stripBbCodeForPreview(s: String): String {
        if (s.isBlank()) return ""
        var t = htmlToPlainTextEquivalents(s)
        // Gazelle-style BBCode emphasis → same plain-text markers as HTML path
        t = Regex("""\[(/)?b]""", RegexOption.IGNORE_CASE).replace(t, "**")
        t = Regex("""\[(/)?i]""", RegexOption.IGNORE_CASE).replace(t, "*")
        t = Regex("""\[(/)?u]""", RegexOption.IGNORE_CASE).replace(t) { "_" }
        t = Regex("""\[(/)?s]""", RegexOption.IGNORE_CASE).replace(t) { "~~" }
        // [img]…[/img] — DOT_MATCHES_ALL so newlines inside tags match.
        t = Regex("""\[img].*?\[/img]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(t, "\n[image]\n")
        // [url=http://x]label[/url] — label runs until [/url]; [^\]]+ is only for the = attribute (escape ] in class).
        t = Regex("""\[url=([^\]]+)]\s*(.*?)\[/url]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(t) { m ->
            val link = m.groupValues[1]
            val label = m.groupValues[2].ifBlank { link }
            "$label ($link)"
        }
        t = t.replace(Regex("""\[/?quote]""", RegexOption.IGNORE_CASE), "\n")
        t = t.replace(Regex("""\[/?code]""", RegexOption.IGNORE_CASE), "\n")
        // Literal [url] and [/url] — must end with \], not a character class ] (\\[/?url] was only one char after [).
        t = t.replace(Regex("""\[(/)?url]""", RegexOption.IGNORE_CASE), "")
        // Remaining [tag] or [tag=…] (single-bracket tokens only; not a full BBCode tree walk).
        t = t.replace(Regex("""\[[^\]]+]"""), "")
        return t.trim()
    }
}
