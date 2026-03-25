package com.turntable.barcodescanner

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.redacted.RedactedIncomingUrlRouter

/**
 * Handles [Intent.ACTION_VIEW] on `redacted.sh` / `www.redacted.sh` and [Intent.ACTION_SEND] text that
 * contains a Redacted URL, then routes into the appropriate in-app screen.
 */
class RedactedLinkDispatchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data
                if (data != null) {
                    if (!RedactedIncomingUrlRouter.startFromUri(this, data)) {
                        startActivity(fallbackHome())
                    }
                }
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!RedactedIncomingUrlRouter.startFromSharedText(this, text)) {
                    finish()
                    return
                }
            }
            else -> { }
        }
        finish()
    }

    private fun fallbackHome(): Intent =
        Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
}
