package com.turntable.barcodescanner

import android.net.Uri

/** Parses `turntable://search?barcode=&artist=&album=` deep links. */
object TurnTableSearchDeepLink {
    const val SCHEME = "turntable"
    const val HOST_SEARCH = "search"

    data class Parsed(
        val barcode: String,
        val artist: String,
        val album: String,
        /** True when artist and/or album arrived from the scanner (skip barcode prefetch). */
        val hasResolvedRelease: Boolean,
    ) {
        val secondaryTerms: String
            get() = when {
                artist.isNotEmpty() && album.isNotEmpty() -> "$artist - $album"
                album.isNotEmpty() -> album
                artist.isNotEmpty() -> artist
                else -> ""
            }
    }

    fun parse(uri: Uri?): Parsed? {
        if (uri == null) return null
        if (uri.scheme != SCHEME) return null
        if (uri.host != HOST_SEARCH) return null
        val barcode = uri.getQueryParameter("barcode")?.trim().orEmpty()
        val artist = uri.getQueryParameter("artist")?.trim().orEmpty()
        val album = uri.getQueryParameter("album")?.trim().orEmpty()
        if (barcode.isEmpty() && artist.isEmpty() && album.isEmpty()) return null
        return Parsed(
            barcode = barcode,
            artist = artist,
            album = album,
            hasResolvedRelease = artist.isNotEmpty() || album.isNotEmpty(),
        )
    }
}
