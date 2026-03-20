package com.turntable.barcodescanner

/**
 * Collapses line breaks from multiline URL text areas so stored URLs stay a single line.
 */
fun CharSequence?.normalizeUrlInput(): String =
    this?.toString()?.trim()
        ?.replace("\r\n", "")
        ?.replace("\n", "")
        ?.replace("\r", "")
        ?.trim()
        .orEmpty()
