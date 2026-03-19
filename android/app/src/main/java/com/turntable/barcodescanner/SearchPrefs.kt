package com.turntable.barcodescanner

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SearchPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Primary = music info API only (e.g. MusicBrainz). Id from SearchPresets.primaryMusicInfo. */
    var primaryMusicInfoApiId: String?
        get() = prefs.getString(KEY_PRIMARY_API_ID, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_PRIMARY_API_ID, value) }

    /** Optional override for the primary (music info) list, edited as text in Settings. */
    var primaryListText: String?
        get() = prefs.getString(KEY_PRIMARY_LIST_TEXT, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_PRIMARY_LIST_TEXT, value) }

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

    /** Package for opening secondary (tracker) links in browser; null = default. */
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

    companion object {
        const val PREFS_NAME = "search_prefs"
        const val KEY_PRIMARY_API_ID = "primary_music_info_api_id"
        const val KEY_PRIMARY_LIST_TEXT = "primary_list_text"
        const val KEY_METHOD = "method"
        const val KEY_POST_CONTENT_TYPE = "post_content_type"
        const val KEY_POST_BODY = "post_body"
        const val KEY_POST_HEADERS = "post_headers"
        const val KEY_SECONDARY_URL = "secondary_search_url"
        const val KEY_SECONDARY_LIST_TEXT = "secondary_list_text"
        const val KEY_SECONDARY_BROWSER_PACKAGE = "secondary_browser_package"
        const val KEY_SECONDARY_AUTO_MUSICBRAINZ = "secondary_auto_musicbrainz"
        const val KEY_BEEP_ON_SCAN = "beep_on_scan"
        const val METHOD_GET = "GET"
        const val METHOD_POST = "POST"
    }
}
