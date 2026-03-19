package com.turntable.barcodescanner

/**
 * Default search presets from yadg-pth-userscript–supported trackers.
 * @see <a href="https://github.com/SavageCore/yadg-pth-userscript">yadg-pth-userscript</a>
 */
object SearchPresets {
    const val CUSTOM_ID = ""

    data class Preset(val id: String, val name: String, val url: String)

    val all: List<Preset> = listOf(
        Preset(CUSTOM_ID, "Custom", ""),
        Preset("red", "RED (redacted.ch)", "https://redacted.ch/torrents.php?searchstr=%s"),
        Preset("ops", "Orpheus (OPS)", "https://orpheus.network/torrents.php?searchstr=%s"),
        Preset("dic", "DIC", "https://dicmusic.club/torrents.php?searchstr=%s"),
        Preset("d3si", "d3si", "https://d3si.net/torrents.php?searchstr=%s"),
        Preset("db9", "DB9 (DeepBassNine)", "https://deepbassnine.com/torrents.php?searchstr=%s"),
        // Music search / discovery
        Preset("discogs", "Discogs", "https://www.discogs.com/search/?q=%s"),
        Preset("musicbrainz", "MusicBrainz", "https://musicbrainz.org/search?query=%s&type=release"),
        Preset("allmusic", "AllMusic", "https://www.allmusic.com/search/all/%s"),
        Preset("lastfm", "Last.fm", "https://www.last.fm/search?q=%s"),
        Preset("bandcamp", "Bandcamp", "https://bandcamp.com/search?q=%s")
    )

    fun findById(id: String): Preset? = all.find { it.id == id }
    fun findByUrl(url: String?): Preset? = all.find { it.url == url }
}
