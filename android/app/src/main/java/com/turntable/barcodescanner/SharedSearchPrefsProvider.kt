package com.turntable.barcodescanner

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Signature-protected access to selected [SearchPrefs] keys for the turnTable Scanner companion.
 * Authority: [AUTHORITY]
 */
class SharedSearchPrefsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: return null
        val prefs = SearchPrefs(ctx)
        val value: String? = when (MATCHER.match(uri)) {
            CODE_API_KEY -> prefs.redactedApiKey
            CODE_BEEP -> if (prefs.beepOnScan) "1" else "0"
            else -> return null
        }
        return MatrixCursor(arrayOf(COLUMN_VALUE)).apply {
            addRow(arrayOf(value ?: ""))
        }
    }

    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/vnd.$AUTHORITY.pref"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        val ctx = context ?: return 0
        val prefs = SearchPrefs(ctx)
        val raw = values?.getAsString(COLUMN_VALUE)
        return when (MATCHER.match(uri)) {
            CODE_API_KEY -> {
                prefs.redactedApiKey = raw?.trim()?.takeIf { it.isNotEmpty() }
                1
            }
            CODE_BEEP -> {
                prefs.beepOnScan = raw == "1" || raw.equals("true", ignoreCase = true)
                1
            }
            else -> 0
        }
    }

    companion object {
        const val AUTHORITY = "com.secondlifetech.turntable.sharedprefs"
        const val COLUMN_VALUE = "value"

        private const val CODE_API_KEY = 1
        private const val CODE_BEEP = 2

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "redacted_api_key", CODE_API_KEY)
            addURI(AUTHORITY, "beep_on_scan", CODE_BEEP)
        }
    }
}
