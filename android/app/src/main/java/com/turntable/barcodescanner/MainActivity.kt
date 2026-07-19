package com.turntable.barcodescanner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Legacy in-app scanner entry. Opens the companion Scanner app via deep link and finishes.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CompanionScannerLauncher.open(this)
        finish()
    }
}
