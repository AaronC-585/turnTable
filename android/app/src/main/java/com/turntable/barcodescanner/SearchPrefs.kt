package com.turntable.barcodescanner

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SearchPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Primary API list: one line per API, `cmd|enabled|displayName`. Order = try order. */
    var primaryApiListText: String?
        get() = prefs.getString(KEY_PRIMARY_API_LIST, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_PRIMARY_API_LIST, value) }

    /** Optional override for the secondary (tracker) list, edited as text in Settings. */
    var secondaryListText: String?
        get() = prefs.getString(KEY_SECONDARY_LIST_TEXT, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_SECONDARY_LIST_TEXT, value) }

    var method: String
        get() = prefs.getString(KEY_METHOD, METHOD_GET) ?: METHOD_GET
        set(value) = prefs.edit { putString(KEY_METHOD, value) }

    var postContentType: String?
        get() = prefs.getString(KEY_POST_CONTENT_TYPE, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_POST_CONTENT_TYPE, value) }

    var postBody: String?
        get() = prefs.getString(KEY_POST_BODY, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_POST_BODY, value) }

    var postHeaders: String?
        get() = prefs.getString(KEY_POST_HEADERS, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_POST_HEADERS, value) }

    /** Package for opening external http(s) links in a browser; null = system default. */
    var secondaryBrowserPackage: String?
        get() = prefs.getString(KEY_SECONDARY_BROWSER_PACKAGE, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_SECONDARY_BROWSER_PACKAGE, value) }

    /** Secondary (tracker) search URL. Used for GET (open in browser) and POST. */
    var secondarySearchUrl: String?
        get() = prefs.getString(KEY_SECONDARY_URL, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_SECONDARY_URL, value) }

    /** If true, fetch release info from MusicBrainz by barcode and use for secondary search. */
    var secondarySearchAutoFromMusicBrainz: Boolean
        get() = prefs.getBoolean(KEY_SECONDARY_AUTO_MUSICBRAINZ, false)
        set(value) = prefs.edit { putBoolean(KEY_SECONDARY_AUTO_MUSICBRAINZ, value) }

    /** If true, play a beep when a barcode is successfully scanned. */
    var beepOnScan: Boolean
        get() = prefs.getBoolean(KEY_BEEP_ON_SCAN, true)
        set(value) = prefs.edit { putBoolean(KEY_BEEP_ON_SCAN, value) }

    /** If true, short haptic when a barcode is successfully scanned. */
    var hapticOnScan: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ON_SCAN, false)
        set(value) = prefs.edit { putBoolean(KEY_HAPTIC_ON_SCAN, value) }

    /**
     * UI theme: [THEME_LIGHT], [THEME_DARK], or [THEME_FOLLOW_SYSTEM] (default).
     * Applied at app start and when saved in Settings (see [AppTheme]).
     */
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_FOLLOW_SYSTEM) ?: THEME_FOLLOW_SYSTEM
        set(value) = prefs.edit { putString(KEY_THEME_MODE, value) }

    /**
     * TheAudioDB API key: sent as **`X-API-KEY`** on every v2 request (barcode lookup + text search).
     * Default in code is `123` if empty; v2 often requires a premium key from theaudiodb.com.
     */
    var theAudioDbApiKey: String?
        get() = prefs.getString(KEY_THEAUDIODB_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_THEAUDIODB_API_KEY, value) }

    /** Optional Redacted API key for tracker-side integrations. */
    var redactedApiKey: String?
        get() = prefs.getString(KEY_REDACTED_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_REDACTED_API_KEY, value) }

    /**
     * Optional SAF tree URI ([Intent.ACTION_OPEN_DOCUMENT_TREE]) where downloaded `.torrent`
     * files are saved. Null/blank = use app cache + system share sheet (default).
     */
    var redactedTorrentDownloadTreeUri: String?
        get() = prefs.getString(KEY_REDACTED_TORRENT_DOWNLOAD_TREE_URI, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) {
                remove(KEY_REDACTED_TORRENT_DOWNLOAD_TREE_URI)
            } else {
                putString(KEY_REDACTED_TORRENT_DOWNLOAD_TREE_URI, value)
            }
        }

    /**
     * Serialized snapshot of [index] `notifications` JSON for deduplicating OS alerts.
     */
    var lastRedactedNotificationsSnapshot: String
        get() = prefs.getString(KEY_REDACTED_NOTIF_SNAPSHOT, "") ?: ""
        set(value) = prefs.edit { putString(KEY_REDACTED_NOTIF_SNAPSHOT, value) }

    /**
     * qBittorrent Web UI base URL, e.g. `http://192.168.1.5:8080` (no `/api/v2` path).
     * See [QbittorrentWebClient].
     */
    var qbittorrentBaseUrl: String?
        get() = prefs.getString(KEY_QBT_BASE_URL, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_QBT_BASE_URL)
            else putString(KEY_QBT_BASE_URL, value.trim())
        }

    var qbittorrentUsername: String?
        get() = prefs.getString(KEY_QBT_USERNAME, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_QBT_USERNAME)
            else putString(KEY_QBT_USERNAME, value.trim())
        }

    /** Stored like other app secrets (device backup may include it). */
    var qbittorrentPassword: String?
        get() = prefs.getString(KEY_QBT_PASSWORD, null)
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_QBT_PASSWORD)
            else putString(KEY_QBT_PASSWORD, value)
        }

    /** True when a usable Web UI base URL is set (credentials optional if auth is off). */
    fun isQbittorrentConfigured(): Boolean =
        QbittorrentWebClient.normalizeBaseUrl(qbittorrentBaseUrl) != null

    companion object {
        const val PREFS_NAME = "search_prefs"
        const val KEY_PRIMARY_API_LIST = "primary_api_list"
        const val KEY_METHOD = "method"
        const val KEY_POST_CONTENT_TYPE = "post_content_type"
        const val KEY_POST_BODY = "post_body"
        const val KEY_POST_HEADERS = "post_headers"
        const val KEY_SECONDARY_URL = "secondary_search_url"
        const val KEY_SECONDARY_LIST_TEXT = "secondary_list_text"
        const val KEY_SECONDARY_BROWSER_PACKAGE = "secondary_browser_package"
        const val KEY_SECONDARY_AUTO_MUSICBRAINZ = "secondary_auto_musicbrainz"
        const val KEY_BEEP_ON_SCAN = "beep_on_scan"
        const val KEY_HAPTIC_ON_SCAN = "haptic_on_scan"
        const val KEY_THEME_MODE = "theme_mode"

        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_FOLLOW_SYSTEM = "system"
        const val KEY_THEAUDIODB_API_KEY = "theaudiodb_api_key"
        const val KEY_REDACTED_API_KEY = "redacted_api_key"
        const val KEY_REDACTED_TORRENT_DOWNLOAD_TREE_URI = "redacted_torrent_download_tree_uri"
        const val KEY_REDACTED_NOTIF_SNAPSHOT = "redacted_notifications_snapshot"
        const val KEY_QBT_BASE_URL = "qbittorrent_base_url"
        const val KEY_QBT_USERNAME = "qbittorrent_username"
        const val KEY_QBT_PASSWORD = "qbittorrent_password"
        const val METHOD_GET = "GET"
        const val METHOD_POST = "POST"
    }
}
