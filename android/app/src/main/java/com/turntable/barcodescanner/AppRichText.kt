package com.turntable.barcodescanner

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import com.turntable.barcodescanner.redacted.RedactedAnnouncementHtml
import com.turntable.barcodescanner.redacted.RedactedHtmlSafe

/**
 * Renders help / long strings as [android.text.Spanned] with clickable links.
 *
 * Supports:
 * - **HTML** fragments (`<a href>`, `<br/>`, …) after sanitization
 * - **BBCode** (`[url]`, `[url=]`, …) via [RedactedAnnouncementHtml.bbToHtml]
 * - **Plain text** with `http://` / `https://` URLs turned into links
 *
 * Note: Android [TextView] does not render RTF; use this + [LinkMovementMethod] for in-app links.
 */
object AppRichText {

    private val bareUrlRegex =
        Regex("""https?://[^\s<>"{}|\\^`\[\]()]+""", RegexOption.IGNORE_CASE)

    private val trailingUrlPunct = """.,);:]'\"»"""

    private fun escapeXmlContent(s: String): String =
        buildString(s.length + 4) {
            for (c in s) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    else -> append(c)
                }
            }
        }

    private fun escapeXmlAttr(s: String): String =
        s.replace("&", "&amp;").replace("\"", "&quot;")

    private fun looksLikeHtmlFragment(s: String): Boolean {
        val t = s.trim()
        if (t.contains("<a ", ignoreCase = true)) return true
        if (t.contains("</a>", ignoreCase = true)) return true
        if (t.contains("<br", ignoreCase = true)) return true
        if (Regex("""<[a-zA-Z][\w:-]*\b""").containsMatchIn(t)) return true
        return false
    }

    private fun plainTextWithUrlsToHtml(s: String): String {
        if (s.isEmpty()) return ""
        val b = StringBuilder()
        var i = 0
        for (m in bareUrlRegex.findAll(s)) {
            b.append(escapeXmlContent(s.substring(i, m.range.first)))
            val full = m.value
            var url = full
            while (url.isNotEmpty() && trailingUrlPunct.contains(url.last())) {
                url = url.dropLast(1)
            }
            val afterUrl = full.substring(url.length)
            if (url.isNotEmpty()) {
                b.append("""<a href="${escapeXmlAttr(url)}">${escapeXmlContent(url)}</a>""")
                b.append(escapeXmlContent(afterUrl))
            } else {
                b.append(escapeXmlContent(full))
            }
            i = m.range.last + 1
        }
        b.append(escapeXmlContent(s.substring(i)))
        return b.toString().replace("\n", "<br/>")
    }

    private fun rawToSanitizedHtml(raw: String): String {
        if (raw.isBlank()) return ""
        val intermediate =
            when {
                raw.contains("[url", ignoreCase = true) ->
                    RedactedAnnouncementHtml.bbToHtml(raw, depth = 0)
                looksLikeHtmlFragment(raw) -> raw
                else -> plainTextWithUrlsToHtml(raw)
            }
        val stripped = RedactedAnnouncementHtml.stripImgTags(intermediate)
        return RedactedHtmlSafe.sanitizeHtmlForTextView(stripped)
    }

    fun toSpanned(raw: String): CharSequence {
        val html = rawToSanitizedHtml(raw)
        if (html.isBlank()) return ""
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    fun applyTo(textView: TextView, raw: String) {
        textView.text = toSpanned(raw)
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.linksClickable = true
    }

    fun applyTo(textView: TextView, @StringRes resId: Int) {
        applyTo(textView, textView.context.getString(resId))
    }

    /**
     * Call after [AlertDialog.show] so link taps work in the message body.
     */
    fun enableLinksInMessage(dialog: AlertDialog) {
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            movementMethod = LinkMovementMethod.getInstance()
            linksClickable = true
        }
    }
}

fun TextView.setRichHelp(@StringRes resId: Int) = AppRichText.applyTo(this, resId)

fun TextView.setRichHelpString(raw: String) = AppRichText.applyTo(this, raw)
