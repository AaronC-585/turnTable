package com.turntable.barcodescanner

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityRedactedDetailBinding
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import org.json.JSONObject

class RedactedAccountActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = RedactedUiHelper.requireApi(this) ?: return
        val binding = ActivityRedactedDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = getString(R.string.redacted_account)

        binding.progress.visibility = View.VISIBLE
        Thread {
            val result = api.index()
            runOnUiThread {
                binding.progress.visibility = View.GONE
                binding.textBody.text = when (result) {
                    is RedactedResult.Failure -> result.message
                    is RedactedResult.Success -> formatIndex(result.response)
                    else -> getString(R.string.redacted_unexpected)
                }
            }
        }.start()
    }

    private fun formatIndex(resp: JSONObject?): String {
        if (resp == null) return "{}"
        return buildString {
            appendLine("username: ${resp.optString("username")}")
            appendLine("id: ${resp.optInt("id")}")
            appendLine("api_version: ${resp.optString("api_version")}")
            resp.optJSONObject("notifications")?.let { n ->
                appendLine("notifications.messages: ${n.optInt("messages")}")
                appendLine("notifications.notifications: ${n.optInt("notifications")}")
            }
            resp.optJSONObject("userstats")?.let { u ->
                appendLine("uploaded: ${u.optLong("uploaded")}")
                appendLine("downloaded: ${u.optLong("downloaded")}")
                appendLine("ratio: ${u.optDouble("ratio")}")
                appendLine("requiredratio: ${u.optDouble("requiredratio")}")
                appendLine("class: ${u.optString("class")}")
            }
            appendLine()
            appendLine(resp.toString(2))
        }
    }
}
