package com.turntable.barcodescanner

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SearchPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var searchUrl: String?
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_URL, value) }

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

    companion object {
        const val PREFS_NAME = "search_prefs"
        const val KEY_URL = "search_url"
        const val KEY_METHOD = "method"
        const val KEY_POST_CONTENT_TYPE = "post_content_type"
        const val KEY_POST_BODY = "post_body"
        const val KEY_POST_HEADERS = "post_headers"
        const val METHOD_GET = "GET"
        const val METHOD_POST = "POST"
    }
}
