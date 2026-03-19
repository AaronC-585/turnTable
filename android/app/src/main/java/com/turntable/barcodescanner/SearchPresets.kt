package com.turntable.barcodescanner

/**
 * Primary = music info (API only, e.g. MusicBrainz barcode lookup).
 * Secondary = yadg/tracker upload sites (open in browser).
 * @see <a href="https://github.com/SavageCore/yadg-pth-userscript">yadg-pth-userscript</a>
 */
object SearchPresets {
    const val CUSTOM_ID = ""

    data class Preset(val id: String, val name: String, val url: String)

    /** Music info sources – API only (no URL opened in browser). */
    val primaryMusicInfoDefault: List<Preset> = listOf(
        Preset("musicbrainz", "MusicBrainz (API)", ""),
        Preset("discogs", "Discogs (API)", ""),
    )

    /** Yadg/tracker upload sites – opened in browser with artist/title query. */
    val secondaryTrackersDefault: List<Preset> = listOf(
        Preset(CUSTOM_ID, "Custom", ""),
        Preset("red", "RED (redacted.sh)", "https://redacted.sh/torrents.php?searchstr=%s"),
        Preset("ops", "Orpheus (OPS)", "https://orpheus.network/torrents.php?searchstr=%s"),
        Preset("dic", "DIC", "https://dicmusic.club/torrents.php?searchstr=%s"),
        Preset("d3si", "d3si", "https://d3si.net/torrents.php?searchstr=%s"),
        Preset("db9", "DB9 (DeepBassNine)", "https://deepbassnine.com/torrents.php?searchstr=%s"),
    )

    fun primaryMusicInfo(context: android.content.Context): List<Preset> =
        parsePrimaryListText(SearchPrefs(context).primaryListText) ?: primaryMusicInfoDefault

    fun secondaryTrackers(context: android.content.Context): List<Preset> =
        parseSecondaryListText(SearchPrefs(context).secondaryListText) ?: secondaryTrackersDefault

    fun findPrimaryById(context: android.content.Context, id: String): Preset? =
        primaryMusicInfo(context).find { it.id == id }

    fun findSecondaryByUrl(context: android.content.Context, url: String?): Preset? =
        secondaryTrackers(context).find { it.url == url }

    /**
     * CLI-style format, one entry per line:
     *   id|name
     * Lines starting with # are comments.
     */
    fun parsePrimaryListText(text: String?): List<Preset>? {
        val t = text?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val out = mutableListOf<Preset>()
        for (raw in t.lines()) {
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) continue
            val parts = line.split("|")
            if (parts.size < 2) continue
            val id = parts[0].trim()
            val name = parts[1].trim()
            if (id.isBlank() || name.isBlank()) continue
            out.add(Preset(id, name, ""))
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /**
     * CLI-style format, one entry per line:
     *   id|name|urlTemplate
     * urlTemplate should contain %s where the query will be inserted.
     * Lines starting with # are comments.
     */
    fun parseSecondaryListText(text: String?): List<Preset>? {
        val t = text?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val out = mutableListOf<Preset>()
        for (raw in t.lines()) {
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) continue
            val parts = line.split("|")
            if (parts.size < 3) continue
            val id = parts[0].trim()
            val name = parts[1].trim()
            val url = parts.subList(2, parts.size).joinToString("|").trim()
            if (id.isBlank() || name.isBlank() || url.isBlank()) continue
            out.add(Preset(id, name, url))
        }
        return out.takeIf { it.isNotEmpty() }
    }
}
