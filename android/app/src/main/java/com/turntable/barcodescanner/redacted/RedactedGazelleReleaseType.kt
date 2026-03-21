package com.turntable.barcodescanner.redacted

import android.content.Context
import com.turntable.barcodescanner.R

/**
 * Maps Gazelle [releaseType] integers to the same labels as the browse form
 * ([R.array.redacted_browse_release_type] / [R.array.redacted_browse_release_type_values]).
 */
object RedactedGazelleReleaseType {

    fun label(context: Context, releaseType: Int): String {
        if (releaseType <= 0) {
            return context.getString(R.string.redacted_release_type_other)
        }
        val values = context.resources.getStringArray(R.array.redacted_browse_release_type_values)
        val labels = context.resources.getStringArray(R.array.redacted_browse_release_type)
        val key = releaseType.toString()
        for (i in values.indices) {
            if (values[i] == key) {
                val l = labels.getOrNull(i)?.trim().orEmpty()
                if (l.isNotEmpty()) return l
            }
        }
        return context.getString(R.string.redacted_release_type_named_fmt, releaseType)
    }
}
