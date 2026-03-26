package com.turntable.barcodescanner.redacted

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object RedactedCollageSearchParams {

    /** Full `https://redacted.sh/collages.php?…` URL for the same query as the JSON API. */
    fun collagesPhpUrl(params: List<Pair<String, String?>>): String {
        val b = "https://redacted.sh/collages.php".toHttpUrlOrNull()!!.newBuilder()
        for ((k, v) in params) {
            if (v.isNullOrEmpty()) continue
            b.addEncodedQueryParameter(k, v)
        }
        return b.build().toString()
    }
}
