package com.secondlifetech.turntablescanner

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

/**
 * Reads shared prefs owned by turnTable via signature-protected ContentProvider.
 * Authority: [AUTHORITY] (must match turnTable's [SharedSearchPrefsProvider]).
 */
object SharedTurnTablePrefs {
    const val AUTHORITY = "com.secondlifetech.turntable.sharedprefs"
    private val URI_API_KEY: Uri = Uri.parse("content://$AUTHORITY/redacted_api_key")
    private val URI_BEEP: Uri = Uri.parse("content://$AUTHORITY/beep_on_scan")

    fun redactedApiKey(context: Context): String? {
        return queryString(context, URI_API_KEY)
    }

    fun beepOnScan(context: Context): Boolean {
        val v = queryString(context, URI_BEEP) ?: return true
        return v.equals("1", ignoreCase = true) || v.equals("true", ignoreCase = true)
    }

    fun setRedactedApiKey(context: Context, value: String?) {
        val cv = ContentValues().apply { put("value", value) }
        context.contentResolver.update(URI_API_KEY, cv, null, null)
    }

    private fun queryString(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf("value"), null, null, null)?.use { c: Cursor ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
