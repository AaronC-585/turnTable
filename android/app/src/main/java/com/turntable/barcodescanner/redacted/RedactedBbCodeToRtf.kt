package com.turntable.barcodescanner.redacted

/**
 * Converts Gazelle-style BBCode to an RTF fragment (no document wrapper) or full document via [bbToRtfDocument].
 * Hyperlinks use standard Word-style `HYPERLINK` fields so pasted RTF keeps **clickable links** in Word, LibreOffice, etc.
 */
object RedactedBbCodeToRtf {

    private val imgTag = Regex("""\[img]\s*(.+?)\s*\[/img]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val codeTag = Regex("""\[code]\s*(.*?)\s*\[/code]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val quoteTag = Regex("""\[quote]\s*(.*?)\s*\[/quote]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val urlNamed = Regex("""\[url=([^\]]+)]\s*(.*?)\s*\[/url]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val urlBare = Regex("""\[url]\s*(https?://[^\[]+?)\s*\[/url]""", RegexOption.IGNORE_CASE)

    private fun normalizeGazelleUrl(rawHref: String): String {
        val t = rawHref.trim()
        return when {
            t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) -> t
            t.startsWith("//") -> "https:$t"
            else -> "https://redacted.sh/${t.trimStart('/')}"
        }
    }

    /** Escape text for RTF (not inside a HYPERLINK quoted URL). */
    fun escapeRtfPlain(text: String): String = buildString(text.length + 16) {
        val iter = text.codePoints().iterator()
        while (iter.hasNext()) {
            val cp = iter.nextInt()
            when (cp) {
                '\\'.code -> append("\\\\")
                '{'.code -> append("\\{")
                '}'.code -> append("\\}")
                '\n'.code -> append("\\line ")
                '\r'.code -> Unit
                else -> if (cp < 128) {
                    appendCodePoint(cp)
                } else if (cp <= 0xFFFF) {
                    val u = if (cp > 0x7FFF) cp - 65536 else cp
                    append("\\u").append(u).append('?')
                } else {
                    for (ch in Character.toChars(cp)) {
                        val v = ch.code
                        val u = if (v > 0x7FFF) v - 65536 else v
                        append("\\u").append(u).append('?')
                    }
                }
            }
        }
    }

    /** Escape a URL for use inside `HYPERLINK "..."` (backslash and double-quote). */
    private fun escapeUrlForHyperlink(url: String): String =
        url.replace("\\", "\\\\").replace("\"", "\\\"")

    /**
     * RTF field: clickable hyperlink. [displayRtfInner] is already valid RTF (e.g. bold or plain escaped runs).
     */
    private fun rtfHyperlinkField(url: String, displayRtfInner: String): String {
        val escUrl = escapeUrlForHyperlink(url)
        return "{\\field{\\*\\fldinst{HYPERLINK \"$escUrl\"}}{\\fldrslt{\\ul $displayRtfInner\\ulnone}}}"
    }

    /**
     * Best-effort BBCode → RTF body (fragment). Matches [RedactedAnnouncementHtml.bbToHtml] tag coverage where practical.
     */
    fun bbToRtf(bb: String, depth: Int = 0): String {
        if (bb.isBlank()) return ""
        if (depth > 4) return escapeRtfPlain(bb)
        var t = bb.replace("\r\n", "\n")

        t = codeTag.replace(t) { m ->
            val inner = escapeRtfPlain(m.groupValues[1])
            "{\\f1\\fs22 $inner\\f0\\fs24}"
        }

        t = quoteTag.replace(t) { m ->
            "{\\li284\\i ${bbToRtf(m.groupValues[1], depth + 1)}\\i0\\li0}"
        }

        t = Regex("""\[(size|color)=[^\]]+]""", RegexOption.IGNORE_CASE).replace(t, "")
        t = Regex("""\[/(size|color)]""", RegexOption.IGNORE_CASE).replace(t, "")

        t = Regex("""\[(/)?b]""", RegexOption.IGNORE_CASE).replace(t) {
            if (it.groupValues[1] == "/") "\\b0 " else "\\b "
        }
        t = Regex("""\[(/)?i]""", RegexOption.IGNORE_CASE).replace(t) {
            if (it.groupValues[1] == "/") "\\i0 " else "\\i "
        }
        t = Regex("""\[(/)?u]""", RegexOption.IGNORE_CASE).replace(t) {
            if (it.groupValues[1] == "/") "\\ulnone " else "\\ul "
        }
        t = Regex("""\[(/)?s]""", RegexOption.IGNORE_CASE).replace(t) {
            if (it.groupValues[1] == "/") "\\strike0 " else "\\strike "
        }

        t = urlNamed.replace(t) { m ->
            val href = normalizeGazelleUrl(m.groupValues[1])
            val labelRaw = m.groupValues[2].trim()
            val displayRtf = if (labelRaw.isEmpty()) {
                escapeRtfPlain(href)
            } else {
                bbToRtf(labelRaw, depth)
            }
            rtfHyperlinkField(href, displayRtf)
        }

        t = urlBare.replace(t) { m ->
            val u = m.groupValues[1].trim()
            rtfHyperlinkField(u, escapeRtfPlain(u))
        }

        t = imgTag.replace(t) { m ->
            val u = m.groupValues[1].trim()
            "\\line ${escapeRtfPlain("[Image: $u]")}\\line "
        }

        t = Regex("""\[\*]""", RegexOption.IGNORE_CASE).replace(t, "\\u8226? ")
        t = Regex("""\[/?list]""", RegexOption.IGNORE_CASE).replace(t, "\\line ")

        t = t.replace("\n", "\\line ")

        t = Regex("""\[/[^\]]+]""", RegexOption.IGNORE_CASE).replace(t, "")
        t = Regex("""\[[^\]]+]""").replace(t, "")

        return t
    }

    /** Wrap an RTF fragment in a minimal document (font table: Arial + Courier for [code]). */
    fun wrapDocument(innerFragment: String): String =
        "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0\\fswiss\\fcharset0 Arial;}{\\f1\\fmodern\\fcharset0 Courier New;}}\\f0\\fs24\n$innerFragment\n}"

    /** Full RTF document with font table (Arial + Courier for [code]). */
    fun bbToRtfDocument(bb: String): String = wrapDocument(bbToRtf(bb, 0))
}
