package com.turntable.barcodescanner

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.turntable.barcodescanner.databinding.ActivitySearchHistoryBinding
import com.turntable.barcodescanner.debug.OutgoingUrlLog
import com.turntable.barcodescanner.redacted.RedactedAvatarLoader
import com.turntable.barcodescanner.redacted.RedactedExtras
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

class SearchHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchHistoryBinding
    private var lastTapScansAtMs = 0L
    private var lastTapScansPos = -1
    private var lastTapRedactedAtMs = 0L
    private var lastTapRedactedPos = -1

    private var selectedTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_search_history)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_home -> {
                    navigateToHome()
                    true
                }
                R.id.action_clear_history -> {
                    confirmClearHistory()
                    true
                }
                else -> false
            }
        }
        binding.buttonScan.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        binding.historyTabs.addTab(
            binding.historyTabs.newTab().setText(getString(R.string.history_tab_scans)),
        )
        binding.historyTabs.addTab(
            binding.historyTabs.newTab().setText(getString(R.string.history_tab_redacted)),
        )
        binding.historyTabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    selectedTab = tab?.position ?: 0
                    updateTabPanels()
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            },
        )

        renderScansList()
        renderRedactedList()
        updateTabPanels()
    }

    override fun onResume() {
        super.onResume()
        renderScansList()
        renderRedactedList()
    }

    private fun updateTabPanels() {
        val scans = selectedTab == 0
        binding.panelScans.visibility = if (scans) View.VISIBLE else View.GONE
        binding.panelRedacted.visibility = if (scans) View.GONE else View.VISIBLE
        binding.buttonScan.visibility = if (scans) View.VISIBLE else View.GONE
    }

    private fun confirmClearHistory() {
        if (selectedTab == 0) {
            AlertDialog.Builder(this)
                .setTitle(R.string.history_clear)
                .setMessage(R.string.history_clear_confirm)
                .setPositiveButton(R.string.history_clear_confirm_positive) { _, _ ->
                    SearchHistoryStore.clear(this)
                    renderScansList()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.history_clear)
                .setMessage(R.string.history_clear_confirm_redacted)
                .setPositiveButton(R.string.history_clear_confirm_positive) { _, _ ->
                    RedactedGroupHistoryStore.clear(this)
                    renderRedactedList()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun renderScansList() {
        val rows = SearchHistoryStore.getAll(this)
        val adapter = HistoryAdapter(rows)
        binding.listHistory.adapter = adapter
        binding.textEmptyScans.alpha = if (rows.isEmpty()) 1f else 0f
        binding.listHistory.setOnItemClickListener { _, _, position, _ ->
            val now = System.currentTimeMillis()
            val doubleTap = position == lastTapScansPos && (now - lastTapScansAtMs) <= 450L
            lastTapScansPos = position
            lastTapScansAtMs = now
            if (!doubleTap) return@setOnItemClickListener
            val entry = rows[position]
            val i = Intent(this, SearchActivity::class.java)
                .putExtra(SearchActivity.EXTRA_BARCODE, entry.barcode)
                .putExtra(SearchActivity.EXTRA_PREFILL_SECONDARY_TERMS, entry.title)
                .putExtra(SearchActivity.EXTRA_AUTOSUBMIT, true)
            startActivity(i)
        }
    }

    private fun renderRedactedList() {
        val rows = RedactedGroupHistoryStore.getAll(this)
        val adapter = RedactedGroupHistoryAdapter(rows)
        binding.listRedactedHistory.adapter = adapter
        val empty = rows.isEmpty()
        binding.textEmptyRedacted.alpha = if (empty) 1f else 0f
        binding.textEmptyRedacted.text = if (empty) {
            getString(R.string.history_redacted_empty) + "\n\n" + getString(R.string.history_redacted_double_tap_hint)
        } else {
            ""
        }
        binding.listRedactedHistory.setOnItemClickListener { _, _, position, _ ->
            val now = System.currentTimeMillis()
            val doubleTap = position == lastTapRedactedPos && (now - lastTapRedactedAtMs) <= 450L
            lastTapRedactedPos = position
            lastTapRedactedAtMs = now
            if (!doubleTap) return@setOnItemClickListener
            val gid = rows[position].groupId
            if (SearchPrefs(this).redactedApiKey.isNullOrBlank()) {
                android.widget.Toast.makeText(this, R.string.redacted_need_api_key, android.widget.Toast.LENGTH_LONG).show()
                return@setOnItemClickListener
            }
            startActivity(
                Intent(this, RedactedTorrentGroupActivity::class.java)
                    .putExtra(RedactedExtras.GROUP_ID, gid),
            )
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
                    val bmp = fetchCoverHttp(url)
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

    private inner class RedactedGroupHistoryAdapter(
        private val items: List<RedactedGroupHistoryEntry>,
    ) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_entry, parent, false)
            val title = row.findViewById<TextView>(R.id.textTitle)
            val subtitle = row.findViewById<TextView>(R.id.textBarcode)
            val date = row.findViewById<TextView>(R.id.textDate)
            val cover = row.findViewById<ImageView>(R.id.imageCover)
            val item = items[position]

            title.text = item.groupName
            subtitle.text = item.subtitle
            date.text = DateFormat.format("yyyy-MM-dd HH:mm", Date(item.timestampMs)).toString()
            cover.setImageDrawable(null)

            val url = item.coverUrl
            if (!url.isNullOrBlank()) {
                cover.tag = url
                Thread {
                    val bmp = fetchCoverForRedactedUrl(url)
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

    private fun fetchCoverHttp(url: String): Bitmap? {
        return try {
            OutgoingUrlLog.log("GET", url)
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

    /** Thumbnail for Redacted-relative or external cover URLs (matches group screen loading). */
    private fun fetchCoverForRedactedUrl(imageUrl: String): Bitmap? {
        val maxSide = 128
        val key = SearchPrefs(this).redactedApiKey?.trim().orEmpty()
        if (key.isNotEmpty()) {
            RedactedAvatarLoader.loadBitmap(imageUrl, key, maxSidePx = maxSide)?.let { return it }
        }
        val url = when {
            imageUrl.startsWith("http", ignoreCase = true) -> imageUrl.trim()
            imageUrl.startsWith("//") -> "https:${imageUrl.trim()}"
            imageUrl.isBlank() -> return null
            else -> "https://redacted.sh/${imageUrl.trim().trimStart('/')}"
        }
        if (url.contains("redacted.sh", ignoreCase = true)) return null
        return fetchCoverHttp(url)
    }
}
