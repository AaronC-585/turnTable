package com.turntable.barcodescanner

/**
 * Compares dotted version strings (e.g. `2026.3.19.1373` vs tag `v2026.3.20.500`).
 * Non-numeric suffixes in a segment are ignored after the leading integer run.
 */
object DottedVersionCompare {

    fun normalizedForCompare(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").trim()

    fun parseParts(version: String): List<Int> {
        val cleaned = normalizedForCompare(version)
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split('.').map { segment ->
            val digits = segment.takeWhile { it.isDigit() }
            digits.toIntOrNull() ?: 0
        }
    }

    /** &gt; 0 if [a] is newer than [b], &lt; 0 if older, 0 if equal. */
    fun compare(a: String, b: String): Int {
        val pa = parseParts(a)
        val pb = parseParts(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }
}
