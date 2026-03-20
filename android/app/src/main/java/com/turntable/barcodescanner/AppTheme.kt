package com.turntable.barcodescanner

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object AppTheme {
    fun applyPersistentNightMode(context: Context) {
        val mode = SearchPrefs(context.applicationContext).themeMode
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                SearchPrefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                SearchPrefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
