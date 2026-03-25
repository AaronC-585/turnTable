package com.turntable.barcodescanner

import android.graphics.Color

/** Parse / format user-entered hex colors for settings (e.g. tracker status strip). */
object ColorHexInput {

    fun parseOrNull(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return try {
            Color.parseColor(t)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun formatForDisplay(color: Int): String {
        val a = Color.alpha(color)
        val rgb = color and 0x00FFFFFF
        return if (a == 255) {
            String.format("#%06X", rgb)
        } else {
            String.format("#%02X%06X", a, rgb)
        }
    }
}
