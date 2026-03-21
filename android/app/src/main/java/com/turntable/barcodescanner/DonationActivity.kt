package com.turntable.barcodescanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityDonationBinding
import com.turntable.barcodescanner.debug.OutgoingUrlLog

class DonationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityDonationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        binding.buttonOpenPayPal.setOnClickListener {
            val u = getString(R.string.donate_paypal_url)
            OutgoingUrlLog.log("VIEW", u)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        }
        binding.buttonOpenCashApp.setOnClickListener {
            val u = getString(R.string.donate_cash_app_url)
            OutgoingUrlLog.log("VIEW", u)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        }
    }
}
