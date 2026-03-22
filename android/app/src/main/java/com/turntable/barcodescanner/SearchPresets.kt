package com.turntable.barcodescanner

/**
 * Primary = music info (API only, e.g. MusicBrainz barcode lookup).
 * Secondary = yadg/tracker upload sites (open in browser).
 * @see <a href="https://github.com/SavageCore/yadg-pth-userscript">yadg-pth-userscript</a>
 */
object SearchPresets {
    const val CUSTOM_ID = ""

    data class Preset(val id: String, val name: String, val url: String)

    data class PrimaryApiEntry(
        /** API id / command (e.g. musicbrainz, discogs, theaudiodb). */
        val cmd: String,
        val enabled: Boolean,
        val displayName: String,
    )

    /**
     * Music info sources – API only (no URL opened in browser).
     * Includes “Music Metadata” class providers (e.g. per
     * [Soundcharts music data API overview](https://soundcharts.com/en/blog/music-data-api)).
     */
    val primaryMusicInfoDefault: List<Preset> = listOf(
        Preset("musicbrainz", "MusicBrainz (API)", ""),
        Preset("discogs", "Discogs (API)", ""),
        Preset("theaudiodb", "TheAudioDB (via MB release)", ""),
    )

    /** Yadg/tracker upload sites – opened in browser with artist/title query. */
    val secondaryTrackersDefault: List<Preset> = listOf(
        Preset(CUSTOM_ID, "Custom", ""),
        Preset(
            "red",
            "RED (redacted.sh)",
            "https://redacted.sh/torrents.php?artistname=%artist%&groupname=%album%&action=advanced&searchsubmit=1",
        ),
        Preset("ops", "Orpheus (OPS)", "https://orpheus.network/torrents.php?searchstr=%s"),
        Preset("dic", "DIC", "https://dicmusic.club/torrents.php?searchstr=%s"),
        Preset("d3si", "d3si", "https://d3si.net/torrents.php?searchstr=%s"),
        Preset("db9", "DB9 (DeepBassNine)", "https://deepbassnine.com/torrents.php?searchstr=%s"),
    )

    /** All available primary (music info) APIs – fixed set. */
    fun primaryMusicInfoAvailable(): List<Preset> = primaryMusicInfoDefault

    private fun defaultPrimaryEntries(): List<PrimaryApiEntry> =
        primaryMusicInfoDefault.map { PrimaryApiEntry(it.id, true, it.name) }

    /** Primary API config in user order (cmd + enabled + optional display name override). */
    fun primaryApiEntries(context: android.content.Context): List<PrimaryApiEntry> {
        val saved = parsePrimaryApiListText(SearchPrefs(context).primaryApiListText)
        return saved ?: defaultPrimaryEntries()
    }

    /** Ordered list of enabled primary API command ids. */
    fun primaryApiCmdsOrderedEnabled(context: android.content.Context): List<String> =
        primaryApiEntries(context)
            .filter { it.enabled && it.cmd.isNotBlank() }
            .map { it.cmd }

    fun secondaryTrackers(context: android.content.Context): List<Preset> =
        parseSecondaryListText(SearchPrefs(context).secondaryListText) ?: secondaryTrackersDefault

    fun findPrimaryPresetByCmd(cmd: String): Preset? =
        primaryMusicInfoDefault.find { it.id == cmd }

    fun findSecondaryByUrl(context: android.content.Context, url: String?): Preset? =
        secondaryTrackers(context).find { it.url == url }

    /**
     * Parse primary API list: one line per API.
     *
     * Supported formats (older saves supported):
     *   - `cmd|enabled` (2 parts)
     *   - `cmd|enabled|displayName` (3 parts)
     *
     * Lines starting with # are comments.
     */
    fun parsePrimaryApiListText(text: String?): List<PrimaryApiEntry>? {
        val t = text?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val out = mutableListOf<PrimaryApiEntry>()
        for (raw in t.lines()) {
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) continue
            val parts = line.split("|")
            if (parts.size < 2) continue
            val cmd = parts[0].trim()
            // Blank cmd allowed (in-progress rows in primary API editor)

            val enabledRaw = parts[1].trim()
            val enabled = enabledRaw == "1" || enabledRaw.equals("true", ignoreCase = true)

            val nameRaw =
                if (parts.size >= 3) parts.subList(2, parts.size).joinToString("|").trim() else ""
            val defaultName = findPrimaryPresetByCmd(cmd)?.name ?: cmd
            val displayName = if (nameRaw.isBlank()) defaultName else nameRaw
            out.add(PrimaryApiEntry(cmd, enabled, displayName))
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** Serialize primary API config to `cmd|enabled|displayName` lines. */
    fun serializePrimaryApiList(entries: List<PrimaryApiEntry>): String =
        entries.joinToString("\n") { e ->
            val name = e.displayName.replace("\n", " ").trim()
            "${e.cmd}|${if (e.enabled) 1 else 0}|$name"
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

    /** Serialize secondary list to "id|name|url" lines (URL may contain |). */
    fun serializeSecondaryList(presets: List<Preset>): String =
        presets.joinToString("\n") { "${it.id}|${it.name}|${it.url}" }
}
