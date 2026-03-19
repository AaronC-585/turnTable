package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivitySearchBinding

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val barcode = intent.getStringExtra(EXTRA_BARCODE).orEmpty()
        binding.editBarcode.setText(barcode)

        binding.buttonSubmit.setOnClickListener { submit(barcode) }
    }

    private fun submit(barcode: String) {
        val prefs = SearchPrefs(this)
        val url = prefs.searchUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(this, R.string.configure_search_url, Toast.LENGTH_LONG).show()
            return
        }

        val notes = binding.editNotes.text?.toString().orEmpty()
        val category = binding.editCategory.text?.toString().orEmpty()

        when (prefs.method) {
            SearchPrefs.METHOD_GET -> {
                val fullUrl = buildGetUrl(url, barcode, notes, category)
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fullUrl)).apply {
                    prefs.browserPackage?.let { pkg -> setPackage(pkg) }
                }
                try {
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    if (prefs.browserPackage != null) {
                        intent.setPackage(null)
                        try {
                            startActivity(intent)
                            finish()
                        } catch (_: Exception) {
                            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            SearchPrefs.METHOD_POST -> {
                doPost(url, barcode, notes, category, prefs)
            }
        }
    }

    private fun buildGetUrl(base: String, barcode: String, notes: String, category: String): String {
        fun enc(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
        var u = base.replace("%s", barcode)
        if (u == base && !base.contains("?")) u = "$base?code=${enc(barcode)}"
        else if (u == base) u = "$base&code=${enc(barcode)}"
        if (notes.isNotBlank()) u += "&notes=${enc(notes)}"
        if (category.isNotBlank()) u += "&category=${enc(category)}"
        return u
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
