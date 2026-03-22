package com.turntable.barcodescanner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityDonationBinding

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
            BrowserLaunch.openHttpUrl(this, getString(R.string.donate_paypal_url))
        }
        binding.buttonOpenCashApp.setOnClickListener {
            BrowserLaunch.openHttpUrl(this, getString(R.string.donate_cash_app_url))
        }

        binding.textDonationBody.setRichHelp(R.string.donation_body)
    }
}
