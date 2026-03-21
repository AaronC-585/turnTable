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

    /** Very small BBCode strip for wiki / description preview (not a full parser). */
    fun stripBbCodeForPreview(s: String): String {
        if (s.isBlank()) return ""
        var t = s
        t = Regex("\\[img\\].*?\\[/img\\]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(t, "\n[image]\n")
        // [^\]]+ = value up to ]; must escape ] — [^]]+ is invalid in Java and crashes Pattern.compile.
        t = Regex("\\[url=([^\\]]+)]([^[]*?)\\[/url]", RegexOption.IGNORE_CASE).replace(t) { m ->
            val link = m.groupValues[1]
            val label = m.groupValues[2].ifBlank { link }
            "$label ($link)"
        }
        t = t.replace(Regex("\\[/?quote\\]", RegexOption.IGNORE_CASE), "\n")
        t = t.replace(Regex("\\[/?code\\]", RegexOption.IGNORE_CASE), "\n")
        t = t.replace(Regex("\\[/?url]", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("\\[[^\\]]+\\]"), "")
        return t.trim()
    }
}
