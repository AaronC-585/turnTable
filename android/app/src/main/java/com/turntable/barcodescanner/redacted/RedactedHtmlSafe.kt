package com.turntable.barcodescanner.redacted

import org.json.JSONObject

/**
 * Makes untrusted API / network strings safer before showing in the UI (TextView, dialogs, Toasts)
 * or before passing HTML to [androidx.core.text.HtmlCompat.fromHtml].
 */
object RedactedHtmlSafe {

    private val scriptBlock =
        Regex("""<script\b[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE)
    private val styleBlock =
        Regex("""<style\b[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE)
    private val onEventAttr =
        Regex("""\s+on\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)""", RegexOption.IGNORE_CASE)
    private val javascriptHrefOrSrc =
        Regex(
            """\s+(href|src)\s*=\s*(["'])\s*javascript:[^"']*\2""",
            RegexOption.IGNORE_CASE,
        )
    private val vbscriptHrefOrSrc =
        Regex(
            """\s+(href|src)\s*=\s*(["'])\s*vbscript:[^"']*\2""",
            RegexOption.IGNORE_CASE,
        )

    /**
     * Best-effort removal of XSS-prone constructs before [androidx.core.text.HtmlCompat.fromHtml].
     * Does not guarantee full HTML safety; prefer stripping scripts, inline handlers, and dangerous URLs.
     */
    fun sanitizeHtmlForTextView(html: String): String {
        if (html.isBlank()) return html
        var s = scriptBlock.replace(html, "")
        s = styleBlock.replace(s, "")
        s = onEventAttr.replace(s, "")
        s = javascriptHrefOrSrc.replace(s, "")
        s = vbscriptHrefOrSrc.replace(s, "")
        return s
    }

    /**
     * Strips bidirectional-override and most C0 control characters from plain text (JSON dumps, errors,
     * forum text). Keeps TAB / LF / CR. Does not HTML-encode (TextView plain text is not HTML).
     */
    fun safePlainTextForUi(s: String): String {
        if (s.isEmpty()) return s
        return buildString(s.length) {
            for (ch in s) {
                when (ch) {
                    '\u202E', '\u202D', '\u2066', '\u2067', '\u2068', '\u2069', '\u200E', '\u200F' -> Unit
                    else -> {
                        val code = ch.code
                        if (code < 32 && ch != '\n' && ch != '\r' && ch != '\t') {
                            Unit
                        } else {
                            append(ch)
                        }
                    }
                }
            }
        }
    }

    /** Pretty-printed JSON safe for monospace [android.widget.TextView] display. */
    fun safeJsonPretty(o: JSONObject?): String =
        safePlainTextForUi(o?.toString(2).orEmpty())
}
