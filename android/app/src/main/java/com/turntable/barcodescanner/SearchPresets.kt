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
    val primaryMusicInfo: List<Preset> = listOf(
        Preset("musicbrainz", "MusicBrainz (API)", ""),
    )

    /** Yadg/tracker upload sites – opened in browser with artist/title query. */
    val secondaryTrackers: List<Preset> = listOf(
        Preset(CUSTOM_ID, "Custom", ""),
        Preset("red", "RED (redacted.sh)", "https://redacted.sh/torrents.php?searchstr=%s"),
        Preset("ops", "Orpheus (OPS)", "https://orpheus.network/torrents.php?searchstr=%s"),
        Preset("dic", "DIC", "https://dicmusic.club/torrents.php?searchstr=%s"),
        Preset("d3si", "d3si", "https://d3si.net/torrents.php?searchstr=%s"),
        Preset("db9", "DB9 (DeepBassNine)", "https://deepbassnine.com/torrents.php?searchstr=%s"),
    )

    fun findPrimaryById(id: String): Preset? = primaryMusicInfo.find { it.id == id }
    fun findSecondaryByUrl(url: String?): Preset? = secondaryTrackers.find { it.url == url }
}
