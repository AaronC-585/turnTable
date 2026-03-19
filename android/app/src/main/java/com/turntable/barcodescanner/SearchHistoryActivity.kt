package com.turntable.barcodescanner

import android.os.Bundle
import android.text.format.DateFormat
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivitySearchHistoryBinding
import java.util.Date
import java.util.Locale

class SearchHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        renderList()
    }

    private fun renderList() {
        val rows = SearchHistoryStore.getAll(this).map { entry ->
            val dt = DateFormat.format("yyyy-MM-dd HH:mm", Date(entry.timestampMs)).toString()
            val barcode = entry.barcode.ifBlank { "-" }
            val query = entry.query.ifBlank { "-" }
            String.format(Locale.US, "%s\nBarcode: %s\nQuery: %s", dt, barcode, query)
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        binding.listHistory.adapter = adapter
        binding.textEmpty.alpha = if (rows.isEmpty()) 1f else 0f
    }
}
