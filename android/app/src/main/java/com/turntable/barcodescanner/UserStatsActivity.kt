package com.turntable.barcodescanner

import android.os.Bundle
import android.text.format.DateFormat
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityUserStatsBinding
import java.util.Date

class UserStatsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.buttonScan.setOnClickListener {
            startActivity(android.content.Intent(this, MainActivity::class.java))
            finish()
        }
        renderStats()
    }

    private fun renderStats() {
        val rows = SearchHistoryStore.getAll(this)
        val total = rows.size
        val uniqueBarcodes = rows.map { it.barcode.trim() }.filter { it.isNotBlank() }.toSet().size
        val withCover = rows.count { !it.coverUrl.isNullOrBlank() }
        val lastScan = rows.firstOrNull()?.timestampMs
        val lastScanText =
            if (lastScan != null) DateFormat.format("yyyy-MM-dd HH:mm", Date(lastScan)).toString() else "-"

        binding.textTotal.text = getString(R.string.stats_total_scans, total)
        binding.textUniqueBarcodes.text = getString(R.string.stats_unique_barcodes, uniqueBarcodes)
        binding.textWithCover.text = getString(R.string.stats_with_cover, withCover)
        binding.textLastScan.text = getString(R.string.stats_last_scan, lastScanText)
    }
}
