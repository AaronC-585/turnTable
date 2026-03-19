package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivitySearchBinding
import org.json.JSONObject
import java.net.URL

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val barcode = intent.getStringExtra(EXTRA_BARCODE).orEmpty()
        binding.editBarcode.setText(barcode)

        val hasSecondary = !SearchPrefs(this).secondarySearchUrl.isNullOrBlank()
        binding.secondarySearchTermsContainer.visibility = if (hasSecondary) View.VISIBLE else View.GONE

        binding.buttonSubmit.setOnClickListener { submit(barcode) }
    }

    private fun submit(barcode: String) {
        val prefs = SearchPrefs(this)
        val notes = binding.editNotes.text?.toString().orEmpty()
        val category = binding.editCategory.text?.toString().orEmpty()

        when (prefs.method) {
            SearchPrefs.METHOD_GET -> {
                val secondaryUrl = prefs.secondarySearchUrl?.takeIf { it.isNotBlank() }
                if (secondaryUrl.isNullOrBlank()) {
                    Toast.makeText(this, R.string.configure_secondary_url, Toast.LENGTH_LONG).show()
                    return
                }
                val secondaryPkg = prefs.secondaryBrowserPackage
                val secondaryQuery = binding.editSecondarySearchTerms.text?.toString()?.trim()
                if (prefs.secondarySearchAutoFromMusicBrainz) {
                    fetchPrimaryInfoAndOpenSecondary(barcode, secondaryUrl, secondaryPkg, prefs)
                } else if (!secondaryQuery.isNullOrBlank()) {
                    openSecondaryUrl(secondaryUrl, secondaryQuery, secondaryPkg)
                    finish()
                } else {
                    Toast.makeText(this, R.string.secondary_no_artist_title, Toast.LENGTH_SHORT).show()
                }
            }
            SearchPrefs.METHOD_POST -> {
                val url = prefs.secondarySearchUrl
                if (url.isNullOrBlank()) {
                    Toast.makeText(this, R.string.configure_secondary_url, Toast.LENGTH_LONG).show()
                    return
                }
                doPost(url, barcode, notes, category, prefs)
            }
        }
    }

    private fun openInBrowser(url: String, pkg: String?, onDone: (success: Boolean) -> Unit) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            pkg?.let { setPackage(it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            onDone(true)
        } catch (_: Exception) {
            val playStoreUrl = KnownBrowsers.findByPackage(pkg)?.playStoreUrl
                ?: "https://play.google.com/store/apps/details?id=${pkg?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""}"
            val displayName = KnownBrowsers.findByPackage(pkg)?.name ?: pkg ?: ""
            if (!pkg.isNullOrBlank()) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(playStoreUrl)))
                    Toast.makeText(this, getString(R.string.browser_open_play_store, displayName.ifBlank { pkg }), Toast.LENGTH_SHORT).show()
                    onDone(false)
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.could_not_open_link, Toast.LENGTH_SHORT).show()
                    onDone(false)
                }
            } else {
                try {
                    intent.setPackage(null)
                    startActivity(intent)
                    onDone(true)
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.could_not_open_link, Toast.LENGTH_SHORT).show()
                    onDone(false)
                }
            }
        }
    }

    private fun openSecondaryUrl(secondaryUrl: String, query: String, pkg: String?) {
        fun enc(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
        val url = secondaryUrl.replace("%s", enc(query))
        openInBrowser(url, pkg) { finish() }
    }

    private fun fetchPrimaryInfoAndOpenSecondary(
        barcode: String,
        secondaryUrl: String,
        pkg: String?,
        prefs: SearchPrefs
    ) {
        val apiId = prefs.primaryMusicInfoApiId ?: "musicbrainz"
        Thread {
            val query = try {
                when (apiId) {
                    "discogs" -> fetchDiscogsArtistTitle(barcode)
                    else -> fetchMusicBrainzArtistTitle(barcode)
                }
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (!query.isNullOrBlank()) {
                    openSecondaryUrl(secondaryUrl, query, pkg)
                } else {
                    val manual = binding.editSecondarySearchTerms.text?.toString()?.trim()
                    if (!manual.isNullOrBlank()) {
                        openSecondaryUrl(secondaryUrl, manual, pkg)
                    } else {
                        Toast.makeText(this, R.string.secondary_no_artist_title, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }.start()
    }

    private fun fetchMusicBrainzArtistTitle(barcode: String): String? {
        val apiUrl =
            "https://musicbrainz.org/ws/2/release/?query=barcode:${java.net.URLEncoder.encode(barcode, "UTF-8")}&fmt=json&limit=1"
        val conn = URL(apiUrl).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "turnTable/1.0 (https://github.com/turntable)")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return parseMusicBrainzArtistTitle(json)
    }

    private fun fetchDiscogsArtistTitle(barcode: String): String? {
        val apiUrl = "https://api.discogs.com/database/search?barcode=${java.net.URLEncoder.encode(barcode, "UTF-8")}&type=release"
        val conn = URL(apiUrl).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "turnTable/1.0")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return parseDiscogsArtistTitle(json)
    }

    private fun parseMusicBrainzArtistTitle(json: String): String? {
        return try {
            val root = JSONObject(json)
            val releases = root.optJSONArray("releases") ?: return null
            if (releases.length() == 0) return null
            val first = releases.getJSONObject(0)
            val title = first.optString("title", "").trim()
            val artistCredit = first.optJSONArray("artist-credit") ?: return null
            val artist = StringBuilder()
            for (i in 0 until artistCredit.length()) {
                val o = artistCredit.getJSONObject(i)
                artist.append(o.optString("name", ""))
                if (i < artistCredit.length() - 1) {
                    artist.append(o.optString("joinphrase", " "))
                }
            }
            val artistStr = artist.toString().trim()
            if (artistStr.isBlank() && title.isBlank()) null else "$artistStr - $title"
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDiscogsArtistTitle(json: String): String? {
        return try {
            val root = JSONObject(json)
            val results = root.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val first = results.getJSONObject(0)
            val title = first.optString("title", "").trim()
            if (title.isBlank()) return null
            title
        } catch (_: Exception) {
            null
        }
    }

    private fun doPost(
        url: String,
        barcode: String,
        notes: String,
        category: String,
        prefs: SearchPrefs
    ) {
        Thread {
            try {
                val body = (prefs.postBody ?: """{"code":"$barcode"}""")
                    .replace("%s", barcode)
                    .replace("\$code", barcode)
                    .replace("\$notes", notes)
                    .replace("\$category", category)
                val contentType = prefs.postContentType ?: "application/json"
                val headers = prefs.postHeaders?.lines()?.filter { it.contains(":") }
                    ?.associate { line ->
                        val i = line.indexOf(':')
                        line.substring(0, i).trim() to line.substring(i + 1).trim()
                    } ?: emptyMap()

                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", contentType)
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                runOnUiThread {
                    Toast.makeText(
                        this,
                        if (code in 200..299) "Sent ($code)" else "Response: $code",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()
    }

    companion object {
        const val EXTRA_BARCODE = "barcode"
    }
}
