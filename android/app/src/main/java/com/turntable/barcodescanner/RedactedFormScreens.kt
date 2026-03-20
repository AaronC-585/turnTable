package com.turntable.barcodescanner

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityRedactedAddCollageBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedGroupEditBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedLogcheckerBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedRiplogBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedSendPmBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedTorrentEditBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedUploadInfoBinding
import com.turntable.barcodescanner.databinding.ActivityRedactedWikiBinding
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import org.json.JSONObject

class RedactedWikiActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = RedactedUiHelper.requireApi(this) ?: return
        val binding = ActivityRedactedWikiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        binding.buttonByName.setOnClickListener {
            val name = binding.editName.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                Toast.makeText(this, R.string.redacted_enter_wiki_name, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.progress.visibility = View.VISIBLE
            Thread {
                val r = api.wiki(name = name)
                runOnUiThread {
                    binding.progress.visibility = View.GONE
                    binding.textBody.text = when (r) {
                        is RedactedResult.Failure -> r.message
                        is RedactedResult.Success -> r.response?.toString(2) ?: ""
                        else -> ""
                    }
                }
            }.start()
        }

        binding.buttonById.setOnClickListener {
            val id = binding.editWikiId.text?.toString()?.toIntOrNull() ?: 0
            if (id <= 0) {
                Toast.makeText(this, R.string.redacted_invalid_id, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.progress.visibility = View.VISIBLE
            Thread {
                val r = api.wiki(id = id)
                runOnUiThread {
                    binding.progress.visibility = View.GONE
                    binding.textBody.text = when (r) {
                        is RedactedResult.Failure -> r.message
                        is RedactedResult.Success -> r.response?.toString(2) ?: ""
                        else -> ""
                    }
                }
            }.start()
        }
    }
}

class RedactedLogcheckerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = RedactedUiHelper.requireApi(this) ?: return
        val binding = ActivityRedactedLogcheckerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        binding.buttonCheck.setOnClickListener {
            val log = binding.editLog.text?.toString().orEmpty()
            if (log.isBlank()) {
                Toast.makeText(this, R.string.redacted_paste_log, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.buttonCheck.isEnabled = false
            Thread {
                val r = api.logcheckerPaste(log)
                runOnUiThread {
                    binding.buttonCheck.isEnabled = true
                    binding.textResult.text = when (r) {
                        is RedactedResult.Failure -> r.message
                        is RedactedResult.Success -> formatLogchecker(r.response)
                        else -> ""
                    }
                }
            }.start()
        }
    }

    private fun formatLogchecker(resp: JSONObject?): String {
        if (resp == null) return ""
        val score = resp.optInt("score", -1)
        val issues = resp.optJSONArray("issues")
        val sb = StringBuilder()
        sb.appendLine("Score: $score").appendLine()
        if (issues != null) {
            for (i in 0 until issues.length()) {
                sb.appendLine("• ${issues.optString(i)}")
            }
        }
        return sb.toString()
    }
}

class RedactedRipLogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = RedactedUiHelper.requireApi(this) ?: return
        val binding = ActivityRedactedRiplogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        binding.buttonLoad.setOnClickListener {
            val tid = binding.editTorrentId.text?.toString()?.toIntOrNull() ?: 0
            val lid = binding.editLogId.text?.toString()?.toIntOrNull() ?: 0
            if (tid <= 0 || lid <= 0) {
                Toast.makeText(this, R.string.redacted_invalid_id, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                val r = api.ripLog(tid, lid)
                runOnUiThread {
                    binding.textBody.text = when (r) {
                        is RedactedResult.Failure -> r.message
                        is RedactedResult.Success -> {
                            val o = r.response
                            if (o == null) {
                                ""
                            } else {
                                val log = o.optString("log")
                                "score: ${o.optInt("score")}\nlogid: ${o.optInt("logid")}\n\n" +
                                    if (log.length > 4000) log.take(4000) + "\n…" else log
                            }
                        }
                        else -> ""
                    }
                }
            }.start()
        }
    }
}

class RedactedGroupEditActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = RedactedUiHelper.requireApi(this) ?: return
        val binding = ActivityRedactedGroupEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        val gid = intent.getIntExtra(RedactedExtras.GROUP_ID, 0)
        if (gid > 0) binding.editGroupId.setText(gid.toString())

        binding.buttonSubmit.setOnClickListener {
            val id = binding.editGroupId.text?.toString()?.toIntOrNull() ?: 0
            val summary = binding.editSummary.text?.toString()?.trim().orEmpty()
            val body = binding.editBody.text?.toString()?.trim().orEmpty()
            if (id <= 0 || summary.isBlank()) {
                Toast.makeText(this, R.string.redacted_invalid_id, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                val r = api.groupEditPost(
                    groupId = id,
                    summary = summary,
                    body = body.takeIf { it.isNotBlank() },
                )
                runOnUiThread {
                    binding.textResult.text = when (r) {
                        is RedactedResult.Failure -> r.message
                        is RedactedResult.Success -> r.responseString ?: r.root.toString()
                        else -> ""
                    }
                }
            }.start()
        }
    }
}

class RedactedTorrentEditActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = RedactedUiHelper.requireApi(this) ?: return
        val binding = ActivityRedactedTorrentEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        val tid = intent.getIntExtra(RedactedExtras.TORRENT_ID, 0)
        if (tid > 0) binding.editTorrentId.setText(tid.toString())

        binding.buttonSubmit.setOnClickListener {
            val id = binding.editTorrentId.text?.toString()?.toIntOrNull() ?: 0
            if (id <= 0) {
                Toast.makeText(this, R.string.redacted_invalid_id, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val fields = buildList {
                binding.editFormat.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { add("format" to it) }
                binding.editMedia.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { add("media" to it) }
                binding.editBitrate.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { add("bitrate" to it) }
                binding.editReleaseDesc.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { add("release_desc" to it) }
            }
            if (fields.isEmpty()) {
                Toast.makeText(this, R.string.redacted_one_field_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                val r = api.torrentEditPost(id, fields)
                runOnUiThread {
                    binding.textResult.text = when (r) {
                        is RedactedResult.Failure -> r.message
                        is RedactedResult.Success -> r.responseString ?: r.root.toString()
                        else -> ""
                    }
                }
            }.start()
        }
    }
}

class RedactedAddToCollageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = RedactedUiHelper.requireApi(this) ?: return
        val binding = ActivityRedactedAddCollageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        binding.buttonSubmit.setOnClickListener {
            val cid = binding.editCollageId.text?.toString()?.toIntOrNull() ?: 0
            val gids = binding.editGroupIds.text?.toString()?.trim().orEmpty()
            if (cid <= 0 || gids.isBlank()) {
                Toast.makeText(this, R.string.redacted_invalid_id, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                val r = api.addToCollage(cid, gids)
                runOnUiThread {
                    binding.textResult.text = when (r) {
                        is RedactedResult.Failure -> r.message
                        is RedactedResult.Success -> r.response?.toString(2) ?: ""
                        else -> ""
                    }
                }
            }.start()
        }
    }
}

class RedactedSendPmActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = RedactedUiHelper.requireApi(this) ?: return
        val binding = ActivityRedactedSendPmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)

        binding.buttonSend.setOnClickListener {
            val to = binding.editToId.text?.toString()?.toIntOrNull() ?: 0
            val subj = binding.editSubject.text?.toString()?.trim().orEmpty()
            val body = binding.editBody.text?.toString().orEmpty()
            if (to <= 0 || body.isBlank()) {
                Toast.makeText(this, R.string.redacted_pm_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                val r = api.sendPm(toUserId = to, subject = subj.takeIf { it.isNotBlank() }, body = body)
                runOnUiThread {
                    Toast.makeText(
                        this,
                        when (r) {
                            is RedactedResult.Failure -> r.message
                            is RedactedResult.Success -> r.responseString ?: getString(R.string.redacted_ok)
                            else -> ""
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }.start()
        }
    }
}

class RedactedUploadInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (RedactedUiHelper.requireApi(this) == null) return
        val binding = ActivityRedactedUploadInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
    }
}
