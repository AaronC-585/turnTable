package com.turntable.barcodescanner.redacted

import org.json.JSONObject

/**
 * Passes Gazelle `browse` query params between the search form and results activities.
 */
object RedactedBrowseParamsCodec {

    fun encode(params: List<Pair<String, String?>>): String {
        val o = JSONObject()
        for ((k, v) in params) {
            if (v != null) o.put(k, v)
        }
        return o.toString()
    }

    fun decode(json: String): List<Pair<String, String?>> {
        val o = JSONObject(json)
        val out = mutableListOf<Pair<String, String?>>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (!o.isNull(k)) {
                out.add(k to o.getString(k))
            }
        }
        return out
    }

    /** Replace or insert `page` for pagination. */
    fun withPage(base: List<Pair<String, String?>>, page: Int): List<Pair<String, String?>> {
        val filtered = base.filter { it.first != "page" }
        return filtered + ("page" to page.toString())
    }
}
