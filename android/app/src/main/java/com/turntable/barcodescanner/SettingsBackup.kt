package com.turntable.barcodescanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes app preference XML files to a single JSON document for user-driven backup
 * (e.g. save to Downloads or cloud via SAF).
 */
object SettingsBackup {

    const val SCHEMA_VERSION = 1
    const val ROOT_KEY = "turntableSettingsBackup"

    /** Preference file names included in export (search_prefs holds SearchPrefs + history JSON). */
    private val STORES = listOf(
        SearchPrefs.PREFS_NAME,
        UpdatePrefs.PREFS_NAME,
    )

    fun buildJson(context: Context): JSONObject {
        val root = JSONObject()
        root.put(ROOT_KEY, SCHEMA_VERSION)
        root.put("exportedAtMs", System.currentTimeMillis())
        root.put("packageName", context.packageName)
        val stores = JSONObject()
        for (name in STORES) {
            stores.put(name, preferenceFileToJson(context, name))
        }
        root.put("preferenceStores", stores)
        return root
    }

    private fun preferenceFileToJson(context: Context, prefsName: String): JSONObject {
        val p = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val o = JSONObject()
        for ((key, value) in p.all.entries.sortedBy { it.key }) {
            when (value) {
                null -> o.put(key, JSONObject.NULL)
                is Boolean -> o.put(key, value)
                is Int -> o.put(key, value)
                is Long -> o.put(key, value)
                is Float -> o.put(key, value.toDouble())
                is String -> o.put(key, value)
                is Set<*> -> {
                    val arr = JSONArray()
                    value.filterIsInstance<String>().sorted().forEach { arr.put(it) }
                    o.put(key, arr)
                }
                else -> o.put(key, value.toString())
            }
        }
        return o
    }
}
