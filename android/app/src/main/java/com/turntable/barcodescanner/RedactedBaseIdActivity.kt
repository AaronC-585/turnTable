package com.turntable.barcodescanner

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.turntable.barcodescanner.databinding.ActivityRedactedIdDetailBinding
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper

/**
 * Shared pattern: numeric ID field + load → JSON body.
 */
abstract class RedactedBaseIdActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedactedIdDetailBinding
    protected lateinit var api: com.turntable.barcodescanner.redacted.RedactedApiClient

    protected abstract fun screenTitle(): String
    protected abstract fun idExtraKey(): String
    protected abstract fun loadForId(id: Int): RedactedResult

    protected open fun formatBody(success: RedactedResult.Success): String {
        val r = success.response
        return r?.toString(2) ?: success.root.toString(2)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c
        binding = ActivityRedactedIdDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = screenTitle()

        val preset = intent.getIntExtra(idExtraKey(), 0)
        if (preset > 0) binding.editId.setText(preset.toString())

        binding.buttonLoad.setOnClickListener { doLoad() }
        if (preset > 0) {
            binding.root.post { doLoad() }
        }
    }

    private fun doLoad() {
        val id = binding.editId.text?.toString()?.toIntOrNull() ?: 0
        if (id <= 0) {
            Toast.makeText(this, R.string.redacted_invalid_id, Toast.LENGTH_SHORT).show()
            return
        }
        binding.progress.visibility = View.VISIBLE
        Thread {
            val result = loadForId(id)
            runOnUiThread {
                binding.progress.visibility = View.GONE
                when (result) {
                    is RedactedResult.Failure -> binding.textBody.text = result.message
                    is RedactedResult.Success -> binding.textBody.text = formatBody(result)
                    else -> binding.textBody.text = getString(R.string.redacted_unexpected)
                }
            }
        }.start()
    }
}

class RedactedUserProfileActivity : RedactedBaseIdActivity() {
    override fun screenTitle() = getString(R.string.redacted_user_profile)
    override fun idExtraKey() = RedactedExtras.USER_ID
    override fun loadForId(id: Int) = api.user(id)
}

class RedactedCommunityStatsActivity : RedactedBaseIdActivity() {
    override fun screenTitle() = getString(R.string.redacted_community_stats)
    override fun idExtraKey() = RedactedExtras.USER_ID
    override fun loadForId(id: Int) = api.communityStats(id)
}

class RedactedCollageActivity : RedactedBaseIdActivity() {
    override fun screenTitle() = getString(R.string.redacted_collage)
    override fun idExtraKey() = RedactedExtras.COLLAGE_ID
    override fun loadForId(id: Int) = api.collage(id)
}

class RedactedTorrentDetailActivity : RedactedBaseIdActivity() {
    override fun screenTitle() = getString(R.string.redacted_torrent_detail)
    override fun idExtraKey() = RedactedExtras.TORRENT_ID
    override fun loadForId(id: Int) = api.torrent(id)
}

class RedactedSimilarArtistsActivity : RedactedBaseIdActivity() {
    override fun screenTitle() = getString(R.string.redacted_similar_artists)
    override fun idExtraKey() = RedactedExtras.ARTIST_ID
    override fun loadForId(id: Int) = api.similarArtists(id, 50)
}

class RedactedRequestDetailActivity : RedactedBaseIdActivity() {
    override fun screenTitle() = getString(R.string.redacted_request_detail)
    override fun idExtraKey() = RedactedExtras.REQUEST_ID
    override fun loadForId(id: Int) = api.request(id)
}
