package com.turntable.barcodescanner.redacted

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context

/**
 * Puts RTF on the clipboard with `text/rtf` so desktop Word / LibreOffice receive **real hyperlinks**.
 * [plainFallback] is included as `text/plain` MIME for apps that ignore RTF.
 */
object RedactedRtfClipboard {

    fun copyRtf(context: Context, clipLabel: String, rtfDocument: String, plainFallback: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val desc = ClipDescription(
            clipLabel,
            arrayOf("text/rtf", ClipDescription.MIMETYPE_TEXT_PLAIN),
        )
        val clip = ClipData(desc, ClipData.Item(rtfDocument))
        clip.addItem(ClipData.Item(plainFallback))
        cm.setPrimaryClip(clip)
    }
}
