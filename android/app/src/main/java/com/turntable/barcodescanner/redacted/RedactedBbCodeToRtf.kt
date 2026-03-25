package com.turntable.barcodescanner.redacted

import android.text.Spanned
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat
import java.net.URLEncoder
import java.util.Locale

/**
 * Converts Gazelle-style BBCode to an RTF fragment (no document wrapper) or full document via [bbToRtfDocument].
 * Hyperlinks use standard Word-style `HYPERLINK` fields so pasted RTF keeps **clickable links** in Word, LibreOffice, etc.
 *
 * [htmlToRtfDocument] converts sanitized HTML (e.g. site-rendered wiki) to RTF with URL hyperlinks.
 */
object RedactedBbCodeToRtf {

    private val imgTag = Regex("""\[img]\s*(.+?)\s*\[/img]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val imgSizedTag = Regex("""\[img=([^\]]+)]\s*(.+?)\s*\[/img]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val imgLegacyUrlTag = Regex("""\[img=([^\]]+)]""", RegexOption.IGNORE_CASE)
    private val codeTag = Regex("""\[code]\s*(.*?)\s*\[/code]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val preTag = Regex("""\[pre]\s*(.*?)\s*\[/pre]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val phpTag = Regex("""\[php]\s*(.*?)\s*\[/php]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val plainTag = Regex("""\[plain]\s*(.*?)\s*\[/plain]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val quoteTag = Regex("""\[quote]\s*(.*?)\s*\[/quote]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val quoteNamedTag = Regex("""\[quote=([^\]]+)]\s*(.*?)\s*\[/quote]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val alignTag = Regex("""\[align=(left|right|center)]\s*(.*?)\s*\[/align]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val hideTag = Regex("""\[(hide|spoiler)]\s*(.*?)\s*\[/\1]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val hideNamedTag = Regex("""\[(hide|spoiler)=([^\]]+)]\s*(.*?)\s*\[/\1]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val matureTag = Regex("""\[mature=([^\]]+)]\s*(.*?)\s*\[/mature]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val importantTag = Regex("""\[important]\s*(.*?)\s*\[/important]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val padTag = Regex("""\[pad=([^\]]+)]\s*(.*?)\s*\[/pad]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val sizeTag = Regex("""\[size=([0-9]{1,2})]\s*(.*?)\s*\[/size]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val headingEqTag = Regex("""(?m)^(={2,4})\s*(.+?)\s*\1$""")
    private val artistTag = Regex("""\[artist]\s*(.*?)\s*\[/artist]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val userTag = Regex("""\[user]\s*(.*?)\s*\[/user]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val torrentTag = Regex("""\[torrent]\s*(.*?)\s*\[/torrent]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ruleTag = Regex("""\[rule]\s*(.*?)\s*\[/rule]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val texTag = Regex("""\[tex]\s*(.*?)\s*\[/tex]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val wikiTag = Regex("""\[\[([^\]]+)]]""")
    private val urlNamed = Regex("""\[url=([^\]]+)]\s*(.*?)\s*\[/url]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val urlBare = Regex("""\[url]\s*(.+?)\s*\[/url]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

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

    private fun urlEncodeComponent(text: String): String =
        URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")

    private fun imageMarker(url: String, widthPx: Int? = null, heightPx: Int? = null): String {
        val w = widthPx?.coerceIn(1, 1000)
        val h = heightPx?.coerceIn(1, 1000)
        val dim = when {
            w != null && h != null -> " ${w}x$h"
            w != null -> " ${w}px"
            else -> ""
        }
        return "\\line ${escapeRtfPlain("[Image$dim: $url]")}\\line "
    }

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
        if (depth > 4) return escapeRtfPlain(RedactedHtmlEntities.decodeCharacterReferences(bb))
        var t = RedactedHtmlEntities.decodeCharacterReferences(bb.replace("\r\n", "\n"))
        val plainStash = mutableListOf<String>()
        fun stashPlain(rtf: String): String {
            plainStash += rtf
            return "\u0007RTFPLAIN${plainStash.lastIndex}\u0007"
        }

        // [n] inside tags prevents triggering on site; normalize by removing marker.
        t = t.replace("[n]", "", ignoreCase = true)

        t = plainTag.replace(t) { m ->
            stashPlain(escapeRtfPlain(m.groupValues[1]))
        }

        t = codeTag.replace(t) { m ->
            val inner = escapeRtfPlain(m.groupValues[1])
            "{\\f1\\fs22 $inner\\f0\\fs24}"
        }
        t = preTag.replace(t) { m ->
            val inner = escapeRtfPlain(m.groupValues[1])
            "{\\f1\\fs22 $inner\\f0\\fs24}"
        }
        t = phpTag.replace(t) { m ->
            val inner = escapeRtfPlain(m.groupValues[1])
            "{\\f1\\fs22 $inner\\f0\\fs24}"
        }

        t = alignTag.replace(t) { m ->
            val mode = m.groupValues[1].lowercase(Locale.ROOT)
            val inner = bbToRtf(m.groupValues[2], depth + 1)
            val align = when (mode) {
                "center" -> "\\qc "
                "right" -> "\\qr "
                else -> "\\ql "
            }
            "{${align}$inner\\ql }"
        }

        t = quoteNamedTag.replace(t) { m ->
            val authorRaw = m.groupValues[1].trim()
            val author = authorRaw.substringBefore('|').trim()
            val inner = bbToRtf(m.groupValues[2], depth + 1)
            "{\\li284\\i ${escapeRtfPlain("$author wrote:")}\\line $inner\\i0\\li0}"
        }

        t = quoteTag.replace(t) { m ->
            "{\\li284\\i ${bbToRtf(m.groupValues[1], depth + 1)}\\i0\\li0}"
        }

        t = hideNamedTag.replace(t) { m ->
            val title = m.groupValues[2].trim().ifBlank { "Hidden text" }
            val inner = bbToRtf(m.groupValues[3], depth + 1)
            "{\\b ${escapeRtfPlain("$title:")}\\b0 \\line $inner}"
        }
        t = hideTag.replace(t) { m ->
            val inner = bbToRtf(m.groupValues[2], depth + 1)
            "{\\b ${escapeRtfPlain("Hidden text:")}\\b0 \\line $inner}"
        }
        t = matureTag.replace(t) { m ->
            val desc = m.groupValues[1].trim().ifBlank { "Mature content" }
            val inner = bbToRtf(m.groupValues[2], depth + 1)
            "{\\b ${escapeRtfPlain("Mature content: $desc")}\\b0 \\line $inner}"
        }

        t = padTag.replace(t) { m ->
            val dims = m.groupValues[1].split('|').map { it.trim().toIntOrNull() ?: 0 }
            val leftPx = dims.getOrNull(3)?.coerceAtLeast(0) ?: 0
            val liTwips = leftPx * 15
            val inner = bbToRtf(m.groupValues[2], depth + 1)
            "{\\li$liTwips $inner\\li0 }"
        }

        t = importantTag.replace(t) { m ->
            "{\\b\\ul ${bbToRtf(m.groupValues[1], depth + 1)}\\ulnone\\b0 }"
        }

        t = sizeTag.replace(t) { m ->
            val n = m.groupValues[1].toIntOrNull()?.coerceIn(1, 10) ?: 2
            val fs = (12 + n * 3).coerceIn(14, 48)
            val inner = bbToRtf(m.groupValues[2], depth + 1)
            "{\\fs$fs $inner\\fs24 }"
        }

        t = headingEqTag.replace(t) { m ->
            val markerCount = m.groupValues[1].length
            val fs = when (markerCount) {
                2 -> 40
                3 -> 34
                else -> 30
            }
            val inner = bbToRtf(m.groupValues[2], depth + 1)
            "\\line {\\b\\fs$fs $inner\\fs24\\b0}\\line "
        }

        // Accept both [color] and [colour], but keep style-neutral RTF for max compatibility.
        t = Regex("""\[(size|color|colour)=[^\]]+]""", RegexOption.IGNORE_CASE).replace(t, "")
        t = Regex("""\[/(size|color|colour)]""", RegexOption.IGNORE_CASE).replace(t, "")

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
            val raw = m.groupValues[1].trim()
            if (raw.isEmpty()) return@replace m.value
            val href = normalizeGazelleUrl(raw)
            rtfHyperlinkField(href, escapeRtfPlain(raw))
        }

        t = artistTag.replace(t) { m ->
            val name = m.groupValues[1].trim()
            val href = "https://redacted.sh/artist.php?artistname=${urlEncodeComponent(name)}"
            rtfHyperlinkField(href, escapeRtfPlain(name))
        }
        t = userTag.replace(t) { m ->
            val name = m.groupValues[1].trim()
            val href = "https://redacted.sh/user.php?action=search&search=${urlEncodeComponent(name)}"
            rtfHyperlinkField(href, escapeRtfPlain(name))
        }
        t = torrentTag.replace(t) { m ->
            val raw = m.groupValues[1].trim()
            val href = if (raw.all { it.isDigit() } && raw.isNotEmpty()) {
                "https://redacted.sh/torrents.php?id=$raw"
            } else {
                normalizeGazelleUrl(raw)
            }
            rtfHyperlinkField(href, escapeRtfPlain(raw))
        }
        t = ruleTag.replace(t) { m ->
            val rule = m.groupValues[1].trim()
            val anchor = rule.removePrefix("h")
            val href = "https://redacted.sh/rules.php#${urlEncodeComponent(anchor)}"
            rtfHyperlinkField(href, escapeRtfPlain(anchor))
        }
        t = texTag.replace(t) { m ->
            "{\\i ${escapeRtfPlain(m.groupValues[1])}\\i0}"
        }
        t = wikiTag.replace(t) { m ->
            val name = m.groupValues[1].trim()
            val href = "https://redacted.sh/wiki.php?action=article&name=${urlEncodeComponent(name)}"
            rtfHyperlinkField(href, escapeRtfPlain(name))
        }

        t = imgSizedTag.replace(t) { m ->
            val arg = m.groupValues[1].trim()
            val body = m.groupValues[2].trim()
            val asLegacyUrl = arg.startsWith("http://", true) || arg.startsWith("https://", true)
            if (asLegacyUrl) {
                imageMarker(arg)
            } else {
                val dims = arg.lowercase(Locale.ROOT).split('x')
                val w = dims.getOrNull(0)?.trim()?.toIntOrNull()
                val h = dims.getOrNull(1)?.trim()?.toIntOrNull()
                imageMarker(body, w, h)
            }
        }
        t = imgTag.replace(t) { m ->
            val u = m.groupValues[1].trim()
            imageMarker(u)
        }
        t = imgLegacyUrlTag.replace(t) { m ->
            val raw = m.groupValues[1].trim()
            // Leave bare `[img=WxH]` (needs `…[/img]` body) untouched; final pass strips unknown tags.
            if (Regex("""^\d+\s*x\s*\d+$""", RegexOption.IGNORE_CASE).matches(raw)) {
                return@replace m.value
            }
            val u = when {
                raw.startsWith("http://", true) || raw.startsWith("https://", true) -> raw
                raw.startsWith("//") -> "https:$raw"
                else -> normalizeGazelleUrl(raw)
            }
            imageMarker(u)
        }

        t = Regex("""\[hr]""", RegexOption.IGNORE_CASE).replace(t, "\\line ________________________________\\line ")
        t = Regex("""\[\*]""", RegexOption.IGNORE_CASE).replace(t, "\\u8226? ")
        t = Regex("""\[#]""", RegexOption.IGNORE_CASE).replace(t, "1. ")
        t = Regex("""\[/?list]""", RegexOption.IGNORE_CASE).replace(t, "\\line ")

        t = t.replace("\n", "\\line ")

        t = Regex("""\[/[^\]]+]""", RegexOption.IGNORE_CASE).replace(t, "")
        t = Regex("""\[[^\]]+]""").replace(t, "")

        plainStash.forEachIndexed { idx, v ->
            t = t.replace("\u0007RTFPLAIN$idx\u0007", v)
        }

        return t
    }

    /** Wrap an RTF fragment in a minimal document (font table: Arial + Courier for [code]). */
    fun wrapDocument(innerFragment: String): String =
        "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0\\fswiss\\fcharset0 Arial;}{\\f1\\fmodern\\fcharset0 Courier New;}}\\f0\\fs24\n$innerFragment\n}"

    /** Full RTF document with font table (Arial + Courier for [code]). */
    fun bbToRtfDocument(bb: String): String = wrapDocument(bbToRtf(bb, 0))

    /**
     * HTML (e.g. wiki `body`) → RTF with [URLSpan] hyperlinks. Strips images; sanitizes like [RedactedHtmlSafe] / TextView.
     */
    fun htmlToRtfDocument(html: String): String {
        if (html.isBlank()) return wrapDocument("")
        val spanned = HtmlCompat.fromHtml(
            RedactedHtmlSafe.sanitizeHtmlForTextView(RedactedAnnouncementHtml.stripImgTags(html)),
            HtmlCompat.FROM_HTML_MODE_COMPACT,
        )
        return wrapDocument(spannedToRtfFragment(spanned))
    }

    private fun spannedToRtfFragment(spanned: Spanned): String {
        val spans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
        if (spans.isEmpty()) return escapeRtfPlain(spanned.toString())
        val sb = StringBuilder()
        var pos = 0
        for (span in spans.sortedWith(compareBy<URLSpan> { spanned.getSpanStart(it) }.thenByDescending { spanned.getSpanEnd(it) })) {
            val st = spanned.getSpanStart(span)
            val en = spanned.getSpanEnd(span)
            if (st < pos) continue
            if (st > pos) sb.append(escapeRtfPlain(spanned.subSequence(pos, st).toString()))
            val url = span.url ?: ""
            val label = escapeRtfPlain(spanned.subSequence(st, en).toString())
            sb.append(rtfHyperlinkField(url, label))
            pos = en
        }
        if (pos < spanned.length) sb.append(escapeRtfPlain(spanned.subSequence(pos, spanned.length).toString()))
        return sb.toString()
    }
}
