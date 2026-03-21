package com.turntable.barcodescanner

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.databinding.ActivityDebugEventLogBinding
import com.turntable.barcodescanner.debug.AppEventLog
import com.turntable.barcodescanner.debug.DebugEventLogFormatter
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * Test-only colored event log. Open via **Volume Up** then **shake** (see [debug.DebugShortcutCoordinator]).
 */
class DebugEventLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugEventLogBinding
    private val adapter = LogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugEventLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = getString(R.string.debug_event_log_title)
        binding.toolbar.subtitle = getString(R.string.debug_event_log_subtitle)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val snap = AppEventLog.snapshot()
        adapter.submit(snap)
        binding.textEmpty.visibility = if (snap.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_debug_event_log, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_export_log -> exportAndShare()
            R.id.action_copy_log -> copyAll()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun copyAll() {
        val text = AppEventLog.formatPlainText()
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("turnTable events", text))
        Toast.makeText(this, R.string.debug_event_log_copied, Toast.LENGTH_SHORT).show()
    }

    private fun exportAndShare() {
        val text = AppEventLog.formatPlainText()
        val dir = File(cacheDir, "event_logs").apply { mkdirs() }
        val name = "turntable_events_${DebugEventLogFormatter.formatTimestamp(System.currentTimeMillis()).replace(Regex("[^0-9]"), "")}.txt"
        val file = File(dir, name)
        try {
            FileOutputStream(file).use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.debug_event_log_save_failed, e.message ?: "?"), Toast.LENGTH_LONG).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.debug_event_log_share_subject))
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(send, getString(R.string.debug_event_log_share_title)))
            Toast.makeText(this, getString(R.string.debug_event_log_saved_path, file.absolutePath), Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.debug_event_log_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private inner class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {
        private var items: List<AppEventLog.Entry> = emptyList()

        fun submit(list: List<AppEventLog.Entry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_debug_log_line, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tv = itemView.findViewById<TextView>(R.id.textLine)
            fun bind(e: AppEventLog.Entry) {
                val ts = DebugEventLogFormatter.formatTimestamp(e.timeMs)
                val cat = e.category.label
                val raw = "$ts  $cat  ${e.message}"
                val span = SpannableString(raw)
                val tsEnd = ts.length
                val catEnd = tsEnd + 2 + cat.length
                val tsColor = ContextCompat.getColor(this@DebugEventLogActivity, R.color.debug_log_timestamp)
                val catColor = ContextCompat.getColor(this@DebugEventLogActivity, e.category.colorRes)
                span.setSpan(ForegroundColorSpan(tsColor), 0, tsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(StyleSpan(Typeface.BOLD), tsEnd + 2, catEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(ForegroundColorSpan(catColor), tsEnd + 2, catEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                tv.text = span
            }
        }
    }
}
