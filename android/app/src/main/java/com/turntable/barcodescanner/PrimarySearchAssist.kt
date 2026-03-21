package com.turntable.barcodescanner

import org.json.JSONObject
import com.turntable.barcodescanner.debug.OutgoingUrlLog
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Result of a primary (barcode) lookup: text for secondary search + optional cover art URL.
 * Cover logic inspired by yadg-style userscripts (Discogs schema, MusicBrainz → Cover Art Archive).
 *
 * **MusicBrainz** — [MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API): JSON via `fmt=json`
 * and/or `Accept: application/json`, meaningful `User-Agent`, and **at most one request per second**
 * to `musicbrainz.org` (rate limiting).
 *
 * **Discogs** — [Discogs API](https://www.discogs.com/developers): User-Agent, optional token,
 * database search + release resource.
 *
 * **Music Metadata** (see e.g. [Soundcharts API guide](https://soundcharts.com/en/blog/music-data-api)):
 * [TheAudioDB](https://www.theaudiodb.com/) v2 **`lookup/album_mb/{mbid}`** with **`X-API-KEY`** reuses a
 * **cached** MusicBrainz barcode search so only one MB request runs per lookup when chaining
 * MB-derived providers.
 */
data class PrimaryLookupResult(
    val searchQuery: String,
    val coverImageUrl: String?,
)

object PrimarySearchAssist {

    /** Cover Art Archive and other non–MusicBrainz-WS HTTP */
    private const val UA_TURNTABLE = "turnTable/1.0 (https://github.com/turntable)"

    /**
     * MusicBrainz requires a meaningful User-Agent (see API FAQ / rate limiting).
     * Format: application name, version, and contact (URL or email).
     * @see <a href="https://musicbrainz.org/doc/MusicBrainz_API">MusicBrainz API</a>
     */
    private const val UA_MUSICBRAINZ = "turnTable/1.0 ( https://github.com/turntable )"

    private const val MB_WS2 = "https://musicbrainz.org/ws/2"

    /** Minimum interval between requests to musicbrainz.org (API policy). */
    private const val MUSICBRAINZ_MIN_INTERVAL_MS = 1000L

    private val musicBrainzRateLock = Any()
    /** Time when the last MusicBrainz web service request was *started* (for 1 req/s cap). */
    private var lastMusicBrainzRequestStartMs: Long = 0L

    /**
     * Discogs requires a descriptive User-Agent (RFC 1945 style), e.g. AppName/version +URL.
     * @see <a href="https://www.discogs.com/developers#page:home,header:0">Discogs API — Home</a>
     */
    private const val UA_DISCOGS = "turnTable/1.0 +https://github.com/turntable"

    private const val DISCOGS_API = "https://api.discogs.com"

    private val mbSearchCacheLock = Any()
    private var mbSearchCacheBarcode: String? = null
    private var mbSearchCacheJson: String? = null

    /** Call when starting a new barcode lookup so a previous scan’s MB JSON is not reused. */
    fun clearMusicBrainzSearchCache() {
        synchronized(mbSearchCacheLock) {
            mbSearchCacheBarcode = null
            mbSearchCacheJson = null
        }
    }

    /**
     * Single MusicBrainz barcode search per [barcode] per lookup session (cached for TheAudioDB).
     */
    private fun getOrFetchMusicBrainzBarcodeSearchJson(barcode: String): String? {
        synchronized(mbSearchCacheLock) {
            if (mbSearchCacheBarcode == barcode && mbSearchCacheJson != null) {
                return mbSearchCacheJson
            }
        }
        val enc = URLEncoder.encode(barcode, Charsets.UTF_8.name())
        val apiUrl = "$MB_WS2/release?query=barcode:$enc&fmt=json&limit=1&offset=0"
        val json = httpGetMusicBrainz(apiUrl) ?: return null
        synchronized(mbSearchCacheLock) {
            mbSearchCacheBarcode = barcode
            mbSearchCacheJson = json
        }
        return json
    }

    fun fetchMusicBrainz(barcode: String): PrimaryLookupResult? {
        val json = getOrFetchMusicBrainzBarcodeSearchJson(barcode) ?: return null
        val parsed = parseMusicBrainzRelease(json) ?: return null
        val cover = fetchCoverArtArchiveRelease(parsed.releaseMbid)
        return PrimaryLookupResult(parsed.searchQuery, cover)
    }

    /**
     * TheAudioDB v2 album by MusicBrainz **release** id:
     * `GET /api/v2/json/lookup/album_mb/{mbid}` with **`X-API-KEY`** ([docs](https://www.theaudiodb.com/free_music_api)).
     */
    fun fetchTheAudioDb(barcode: String, apiKey: String? = null): PrimaryLookupResult? {
        val mbJson = getOrFetchMusicBrainzBarcodeSearchJson(barcode) ?: return null
        val mbid = extractMusicBrainzReleaseMbidFromSearchJson(mbJson) ?: return null
        val path = "lookup/album_mb/${TheAudioDbApi.pathEncode(mbid)}"
        val body = TheAudioDbApi.get(path, apiKey) ?: return null
        return parseTheAudioDbAlbumMbResponse(body)
    }

    private fun extractMusicBrainzReleaseMbidFromSearchJson(json: String): String? {
        return try {
            val root = JSONObject(json)
            val releases = root.optJSONArray("releases") ?: return null
            if (releases.length() == 0) return null
            releases.getJSONObject(0).optString("id", "").trim().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTheAudioDbAlbumMbResponse(json: String): PrimaryLookupResult? {
        return try {
            val a = extractTheAudioDbAlbumJson(json) ?: return null
            val artist = a.optString("strArtist", "").trim()
            val album = a.optString("strAlbum", "").trim()
            if (artist.isBlank() && album.isBlank()) return null
            val query = when {
                artist.isNotBlank() && album.isNotBlank() ->
                    if (SecondarySearchVariables.isPlaceholderCompilationArtist(artist)) album else "$artist - $album"
                album.isNotBlank() -> album
                else -> artist
            }
            val cover = a.optString("strAlbumThumb", "").ifBlank {
                a.optString("strAlbumCDart", "")
            }.ifBlank { a.optString("strAlbumSpine", "") }.takeIf { it.isNotBlank() }
            PrimaryLookupResult(query, cover)
        } catch (_: Exception) {
            null
        }
    }

    /** v1/v2-style album payload: `album` array or object, optional `data` wrapper. */
    private fun extractTheAudioDbAlbumJson(json: String): JSONObject? {
        return try {
            val root = JSONObject(json)
            extractAlbumFromTheAudioDbNode(root)
                ?: root.optJSONObject("data")?.let { extractAlbumFromTheAudioDbNode(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAlbumFromTheAudioDbNode(node: JSONObject): JSONObject? {
        val arr = node.optJSONArray("album")
        if (arr != null && arr.length() > 0) return arr.optJSONObject(0)
        node.optJSONObject("album")?.let { return it }
        val albums = node.optJSONArray("albums")
        if (albums != null && albums.length() > 0) return albums.optJSONObject(0)
        return null
    }

    private fun musicBrainzHeaders(): Map<String, String> = mapOf(
        "User-Agent" to UA_MUSICBRAINZ,
        // JSON: Accept or fmt= — docs say both; we send both.
        "Accept" to "application/json",
    )

    /**
     * MusicBrainz: "never make more than ONE call per second" to the API.
     * @see <a href="https://musicbrainz.org/doc/MusicBrainz_API#Application_rate_limiting_and_identification">Rate limiting</a>
     */
    private fun httpGetMusicBrainz(url: String): String? {
        synchronized(musicBrainzRateLock) {
            val now = System.currentTimeMillis()
            val waitMs = MUSICBRAINZ_MIN_INTERVAL_MS - (now - lastMusicBrainzRequestStartMs)
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            lastMusicBrainzRequestStartMs = System.currentTimeMillis()
        }
        return httpGet(url, musicBrainzHeaders())
    }

    /**
     * @param personalAccessToken optional; `Authorization: Discogs token=…` per API authentication.
     * @see <a href="https://www.discogs.com/developers#page:authentication">Discogs — Authentication</a>
     */
    fun fetchDiscogs(barcode: String, personalAccessToken: String? = null): PrimaryLookupResult? {
        val normDigits = normalizeBarcodeDigits(barcode)
        val enc = URLEncoder.encode(barcode.trim(), Charsets.UTF_8.name())
        val searchUrl =
            "$DISCOGS_API/database/search?barcode=$enc&type=release&page=1&per_page=10"
        val searchJson = httpGet(searchUrl, discogsHeaders(personalAccessToken)) ?: return null
        val candidateIds = discogsSearchReleaseIdsOrdered(searchJson, normDigits)
        if (candidateIds.isEmpty()) return null

        for (releaseId in candidateIds) {
            val releaseJson = httpGet(
                "$DISCOGS_API/releases/$releaseId",
                discogsHeaders(personalAccessToken),
            ) ?: continue
            if (!releaseJsonContainsBarcode(releaseJson, normDigits)) continue
            val query = buildDiscogsReleaseSearchQuery(releaseJson) ?: continue
            val cover = bestDiscogsImageFromReleaseJson(releaseJson)
                ?: discogsThumbFromSearchForId(searchJson, releaseId)
            return PrimaryLookupResult(query, cover)
        }

        val firstId = candidateIds.first()
        val releaseJson = httpGet(
            "$DISCOGS_API/releases/$firstId",
            discogsHeaders(personalAccessToken),
        ) ?: return null
        val query = buildDiscogsReleaseSearchQuery(releaseJson)
            ?: parseDiscogsSearchFirstTitle(searchJson)
            ?: return null
        val cover = bestDiscogsImageFromReleaseJson(releaseJson)
            ?: discogsThumbFromSearchForId(searchJson, firstId)
        return PrimaryLookupResult(query, cover)
    }

    private fun discogsHeaders(personalAccessToken: String?): Map<String, String> {
        val m = linkedMapOf(
            "User-Agent" to UA_DISCOGS,
            // Explicit Discogs API v2 media type.
            "Accept" to "application/vnd.discogs.v2+json",
        )
        personalAccessToken?.trim()?.takeIf { it.isNotEmpty() }?.let { token ->
            m["Authorization"] = "Discogs token=$token"
        }
        return m
    }

    private fun normalizeBarcodeDigits(raw: String): String = raw.filter { it.isDigit() }

    private fun discogsSearchReleaseIdsOrdered(searchJson: String, normDigits: String): List<Long> {
        return try {
            val root = JSONObject(searchJson)
            val results = root.optJSONArray("results") ?: return emptyList()
            val withMatch = mutableListOf<Long>()
            val rest = mutableListOf<Long>()
            for (i in 0 until results.length()) {
                val o = results.getJSONObject(i)
                if (o.optString("type", "") != "release") continue
                val id = o.optLong("id", -1L)
                if (id < 0) continue
                if (normDigits.isNotEmpty() && discogsSearchResultHasBarcode(o, normDigits)) {
                    withMatch.add(id)
                } else {
                    rest.add(id)
                }
            }
            withMatch + rest
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun discogsSearchResultHasBarcode(o: JSONObject, normDigits: String): Boolean {
        if (!o.has("barcode")) return false
        val ar = o.optJSONArray("barcode")
        if (ar != null) {
            for (i in 0 until ar.length()) {
                if (normalizeBarcodeDigits(ar.optString(i, "")) == normDigits) return true
            }
            return false
        }
        val s = o.optString("barcode", "")
        return s.isNotBlank() && normalizeBarcodeDigits(s) == normDigits
    }

    private fun releaseJsonContainsBarcode(releaseJson: String, normDigits: String): Boolean {
        if (normDigits.isEmpty()) return true
        return try {
            val root = JSONObject(releaseJson)
            val ids = root.optJSONArray("identifiers") ?: return true
            for (i in 0 until ids.length()) {
                val id = ids.getJSONObject(i)
                val type = id.optString("type", "").lowercase()
                if (type.contains("barcode")) {
                    if (normalizeBarcodeDigits(id.optString("value", "")) == normDigits) return true
                }
            }
            false
        } catch (_: Exception) {
            true
        }
    }

    private fun buildDiscogsReleaseSearchQuery(releaseJson: String): String? {
        return try {
            val root = JSONObject(releaseJson)
            val title = root.optString("title", "").trim()
            if (title.isBlank()) return null
            val sort = root.optString("artists_sort", "").trim()
            if (sort.isNotBlank()) {
                return if (SecondarySearchVariables.isPlaceholderCompilationArtist(sort)) title else "$sort - $title"
            }
            val artists = root.optJSONArray("artists")
            if (artists != null && artists.length() > 0) {
                val names = StringBuilder()
                for (i in 0 until artists.length()) {
                    if (i > 0) names.append(", ")
                    names.append(artists.getJSONObject(i).optString("name", ""))
                }
                val a = names.toString().trim()
                if (a.isNotBlank()) {
                    return if (SecondarySearchVariables.isPlaceholderCompilationArtist(a)) title else "$a - $title"
                }
            }
            title
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDiscogsSearchFirstTitle(searchJson: String): String? {
        return try {
            val root = JSONObject(searchJson)
            val results = root.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            results.getJSONObject(0).optString("title", "").trim().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun discogsThumbFromSearchForId(searchJson: String, releaseId: Long): String? {
        return try {
            val root = JSONObject(searchJson)
            val results = root.optJSONArray("results") ?: return null
            for (i in 0 until results.length()) {
                val o = results.getJSONObject(i)
                if (o.optLong("id", -1L) != releaseId) continue
                val cover = o.optString("cover_image", "").takeIf { it.isNotBlank() }
                val thumb = o.optString("thumb", "").takeIf { it.isNotBlank() }
                return cover ?: thumb
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun bestDiscogsImageFromReleaseJson(releaseJson: String): String? {
        return try {
            val root = JSONObject(releaseJson)
            val images = root.optJSONArray("images") ?: return null
            if (images.length() == 0) return null
            var primaryUri: String? = null
            var bestW = -1
            var bestUri: String? = null
            for (i in 0 until images.length()) {
                val img = images.getJSONObject(i)
                val uri = img.optString("uri", "").ifBlank { img.optString("resource_url", "") }
                if (uri.isBlank()) continue
                if (img.optBoolean("primary", false) || img.optString("type", "") == "primary") {
                    primaryUri = uri
                }
                val w = img.optInt("width", 0)
                if (w >= bestW) {
                    bestW = w
                    bestUri = uri
                }
            }
            primaryUri ?: bestUri
                ?: images.getJSONObject(0).optString("uri", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private data class MbParse(val searchQuery: String, val releaseMbid: String)
    private fun parseMusicBrainzRelease(json: String): MbParse? {
        return try {
            val root = JSONObject(json)
            val releases = root.optJSONArray("releases") ?: return null
            if (releases.length() == 0) return null
            val first = releases.getJSONObject(0)
            val releaseMbid = first.optString("id", "").trim()
            if (releaseMbid.isBlank()) return null
            val title = first.optString("title", "").trim()
            val artistCredit = first.optJSONArray("artist-credit") ?: return null
            val artist = StringBuilder()
            for (i in 0 until artistCredit.length()) {
                val o = artistCredit.getJSONObject(i)
                artist.append(o.optString("name", ""))
                if (i < artistCredit.length() - 1) {
                    artist.append(o.optString("joinphrase", " "))
                }
            }
            val artistStr = artist.toString().trim()
            if (artistStr.isBlank() && title.isBlank()) return null
            val query = when {
                SecondarySearchVariables.isPlaceholderCompilationArtist(artistStr) -> title
                artistStr.isNotBlank() && title.isNotBlank() -> "$artistStr - $title"
                title.isNotBlank() -> title
                else -> artistStr
            }
            MbParse(query, releaseMbid)
        } catch (_: Exception) {
            null
        }
    }

    /** Cover Art Archive JSON API (same source as yadg MusicBrainz branch). */
    private fun fetchCoverArtArchiveRelease(releaseMbid: String): String? {
        val url = "https://coverartarchive.org/release/$releaseMbid/"
        val json = httpGet(
            url,
            mapOf("User-Agent" to UA_TURNTABLE),
        ) ?: return null
        return try {
            val root = JSONObject(json)
            val images = root.optJSONArray("images") ?: return null
            if (images.length() == 0) return null
            images.getJSONObject(0).optString("image", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(url: String, headers: Map<String, String>): String? {
        return try {
            OutgoingUrlLog.log("GET", url)
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return null
            }
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            text
        } catch (_: Exception) {
            null
        }
    }

}
