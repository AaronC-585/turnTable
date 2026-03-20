package com.turntable.barcodescanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import android.text.format.DateFormat
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivitySearchHistoryBinding
import java.net.HttpURLConnection
import java.net.URL
import android.graphics.BitmapFactory
import java.util.Date

class SearchHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchHistoryBinding
    private var lastTapAtMs = 0L
    private var lastTapPos = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        binding.buttonScan.setOnClickListener {
            startActivity(android.content.Intent(this, MainActivity::class.java))
            finish()
        }
        renderList()
    }

    private fun renderList() {
        val rows = SearchHistoryStore.getAll(this)
        val adapter = HistoryAdapter(rows)
        binding.listHistory.adapter = adapter
        binding.textEmpty.alpha = if (rows.isEmpty()) 1f else 0f
        binding.listHistory.setOnItemClickListener { _, _, position, _ ->
            val now = System.currentTimeMillis()
            val doubleTap = position == lastTapPos && (now - lastTapAtMs) <= 450L
            lastTapPos = position
            lastTapAtMs = now
            if (!doubleTap) return@setOnItemClickListener
            val entry = rows[position]
            val i = android.content.Intent(this, SearchActivity::class.java)
                .putExtra(SearchActivity.EXTRA_BARCODE, entry.barcode)
                .putExtra(SearchActivity.EXTRA_PREFILL_SECONDARY_TERMS, entry.title)
                .putExtra(SearchActivity.EXTRA_AUTOSUBMIT, true)
            startActivity(i)
        }
    }

    private inner class HistoryAdapter(
        private val items: List<SearchHistoryEntry>,
    ) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_entry, parent, false)
            val title = row.findViewById<TextView>(R.id.textTitle)
            val barcode = row.findViewById<TextView>(R.id.textBarcode)
            val date = row.findViewById<TextView>(R.id.textDate)
            val cover = row.findViewById<ImageView>(R.id.imageCover)
            val item = items[position]

            title.text = item.title.ifBlank { "-" }
            barcode.text = getString(R.string.history_barcode_format, item.barcode.ifBlank { "-" })
            date.text = DateFormat.format("yyyy-MM-dd HH:mm", Date(item.timestampMs)).toString()
            cover.setImageDrawable(null)

            val url = item.coverUrl
            if (!url.isNullOrBlank()) {
                cover.tag = url
                Thread {
                    val bmp = fetchCover(url)
                    runOnUiThread {
                        if (cover.tag == url) {
                            if (bmp != null) cover.setImageBitmap(bmp) else cover.setImageDrawable(null)
                        }
                    }
                }.start()
            }
            return row
        }
    }

    private fun fetchCover(url: String): android.graphics.Bitmap? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "turnTable/1.0")
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bytes.isEmpty()) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
