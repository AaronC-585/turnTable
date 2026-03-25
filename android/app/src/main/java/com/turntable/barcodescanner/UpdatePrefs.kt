package com.turntable.barcodescanner

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Throttling and “skip this release” for GitHub update prompts. */
class UpdatePrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastBackgroundCheckWallTimeMs: Long
        get() = prefs.getLong(KEY_LAST_BG_CHECK_MS, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_BG_CHECK_MS, value) }

    /** If equal to [GithubAppUpdateChecker.normalizedTag], background flow won’t prompt again. */
    var skippedReleaseTag: String?
        get() = prefs.getString(KEY_SKIPPED_TAG, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_SKIPPED_TAG)
            else putString(KEY_SKIPPED_TAG, value)
        }

    companion object {
        const val PREFS_NAME = "app_update_prefs"
        private const val KEY_LAST_BG_CHECK_MS = "last_bg_check_wall_ms"
        private const val KEY_SKIPPED_TAG = "skipped_release_tag"
    }
}
