package com.turntable.barcodescanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import com.turntable.barcodescanner.redacted.RedactedAnnouncementHtml
import com.turntable.barcodescanner.redacted.RedactedAvatarLoader
import com.turntable.barcodescanner.redacted.RedactedHtmlEntities
import com.turntable.barcodescanner.redacted.RedactedHtmlSafe
import kotlin.math.max
import kotlin.math.min

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
    private fun withPreferredBrowserSpans(textView: TextView, source: CharSequence): CharSequence {
        val spanned = source as? Spanned ?: return source
        val out = SpannableStringBuilder(spanned)
        val spans = out.getSpans(0, out.length, URLSpan::class.java)
        for (span in spans) {
            val start = out.getSpanStart(span)
            val end = out.getSpanEnd(span)
            val flags = out.getSpanFlags(span)
            out.removeSpan(span)
            val url = span.url
            out.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        BrowserLaunch.openHttpUrl(textView.context, url)
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = true
                    }
                },
                start,
                end,
                flags,
            )
        }
        return out
    }


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

    /** True when [raw] looks like Gazelle-style BBCode (not only `[url]`). */
    private val bbCodeTagRegex =
        Regex("""\[(/?)(url|b|i|u|s|quote|code|img|list|size|color|\*)""", RegexOption.IGNORE_CASE)

    private fun looksLikeBbCode(raw: String): Boolean {
        if (!raw.contains('[')) return false
        if (raw.contains("[img=", ignoreCase = true)) return true
        return bbCodeTagRegex.containsMatchIn(raw)
    }

    /** For clipboard / export: HTML fragment (tags, entities). */
    fun isLikelyHtml(s: String): Boolean = looksLikeHtmlFragment(s)

    /** For clipboard / export: BBCode (wiki body, descriptions). */
    fun isLikelyBbCode(s: String): Boolean = looksLikeBbCode(s)

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
        val isHtml = looksLikeHtmlFragment(raw)
        // Do not decode entities on raw HTML before sanitize — could turn &lt; into markup.
        val plainOrBb = if (isHtml) raw else RedactedHtmlEntities.decodeCharacterReferences(raw)
        val intermediate =
            when {
                isHtml -> raw
                plainOrBb.contains("[url", ignoreCase = true) || looksLikeBbCode(plainOrBb) ->
                    RedactedAnnouncementHtml.bbToHtml(plainOrBb, depth = 0)
                else -> plainTextWithUrlsToHtml(plainOrBb)
            }
        return RedactedHtmlSafe.sanitizeHtmlForTextView(intermediate)
    }

    /**
     * [Html.ImageGetter] that loads Redacted / http(s) images with auth + disk cache (see [RedactedAvatarLoader]).
     */
    private class RichTextImageGetter(private val textView: TextView) : Html.ImageGetter {
        override fun getDrawable(source: String?): Drawable {
            val raw = source?.trim().orEmpty()
            val loadUrl = RedactedAnnouncementHtml.absolutizeImgSrcForFetch(raw)
            val density = textView.resources.displayMetrics.density
            val screenW = textView.resources.displayMetrics.widthPixels
            val maxW = (
                textView.width.takeIf { it > 0 }
                    ?: (screenW * 0.92f).toInt()
                ).coerceIn(1, screenW)
            val placeholderH = (72 * density).toInt().coerceAtLeast(1)
            val d = AsyncInlineImageDrawable()
            if (loadUrl == null) {
                d.setBounds(0, 0, 0, 0)
                return d
            }
            d.setBounds(0, 0, maxW, placeholderH)
            val apiKey = SearchPrefs(textView.context).redactedApiKey?.trim().orEmpty()
            Thread {
                val bmp = RedactedAvatarLoader.loadBitmapCached(
                    context = textView.context,
                    rawUrl = loadUrl,
                    apiKey = apiKey,
                    maxSidePx = 1440,
                )
                textView.post {
                    if (bmp == null) {
                        d.setBounds(0, 0, 0, 0)
                    } else {
                        val scale = min(1f, maxW.toFloat() / bmp.width.toFloat())
                        val tw = max(1, (bmp.width * scale).toInt())
                        val th = max(1, (bmp.height * scale).toInt())
                        d.setBounds(0, 0, tw, th)
                        d.setLoadedBitmap(bmp)
                    }
                    refreshTextViewForInlineDrawables(textView)
                }
            }.start()
            return d
        }
    }

    private fun refreshTextViewForInlineDrawables(tv: TextView) {
        val t = tv.text
        tv.text = null
        tv.text = t
    }

    private class AsyncInlineImageDrawable : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8E8E8.toInt() }
        private var bitmap: Bitmap? = null

        fun setLoadedBitmap(b: Bitmap) {
            bitmap = b
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val b = bitmap
            if (b != null && !b.isRecycled) {
                canvas.drawBitmap(b, null, bounds, paint)
            } else {
                canvas.drawRect(bounds, paint)
            }
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun toSpanned(textView: TextView, raw: String): CharSequence {
        val html = rawToSanitizedHtml(raw)
        if (html.isBlank()) return ""
        return HtmlCompat.fromHtml(
            html,
            HtmlCompat.FROM_HTML_MODE_COMPACT,
            RichTextImageGetter(textView),
            null,
        )
    }

    fun applyTo(textView: TextView, raw: String) {
        textView.text = withPreferredBrowserSpans(textView, toSpanned(textView, raw))
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
