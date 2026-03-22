package com.turntable.barcodescanner

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.turntable.barcodescanner.databinding.ActivityHomeBinding
import com.turntable.barcodescanner.redacted.RedactedApiClient
import com.turntable.barcodescanner.redacted.RedactedAvatarLoader
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedHomeStatsFormatter
import com.turntable.barcodescanner.redacted.RedactedProfileUiBuilder
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedUiHelper
import com.turntable.barcodescanner.redacted.responseOrNull

/**
 * Other users' profiles use the same layout and section styling as [HomeActivity], built from
 * `user` + `community_stats` (no `index` — viewer-specific stats like bonus points from index
 * are omitted for the viewed user).
 */
class RedactedUserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var profileUi: HomeProfileLayoutController
    private lateinit var api: RedactedApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c

        val userId = intent.getIntExtra(RedactedExtras.USER_ID, 0)
        if (userId <= 0) {
            Toast.makeText(this, R.string.redacted_invalid_id, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        profileUi = HomeProfileLayoutController(this, binding)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupToolbarHome(binding.toolbar)
        supportActionBar?.title = getString(R.string.redacted_user_profile)

        binding.layoutNoKey.visibility = View.GONE
        binding.buttonAddApiKey.visibility = View.GONE

        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.home_token_label),
            ContextCompat.getColor(this, R.color.home_ratio_ok),
        )
        binding.swipeRefresh.setOnRefreshListener {
            AppBottomBars.refreshTrackerNow(this)
            loadUser(userId, isPullRefresh = true)
        }

        binding.textError.visibility = View.GONE
        binding.swipeRefresh.visibility = View.GONE

        loadUser(userId, isPullRefresh = false)
    }

    private fun applyProfilePlaceholderIconTint() {
        ImageViewCompat.setImageTintList(
            binding.imageProfile,
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.app_icon_emphasis),
            ),
        )
    }

    private fun clearProfileImageTint() {
        ImageViewCompat.setImageTintList(binding.imageProfile, null)
    }

    private fun loadUser(userId: Int, isPullRefresh: Boolean) {
        val key = SearchPrefs(this).redactedApiKey?.trim().orEmpty()
        if (key.isEmpty()) {
            finish()
            return
        }

        if (!isPullRefresh) {
            binding.loadingTopBar.visibility = View.VISIBLE
            binding.swipeRefresh.visibility = View.GONE
        } else {
            binding.swipeRefresh.isRefreshing = true
        }
        binding.textError.visibility = View.GONE

        val iconPx = (resources.getDimensionPixelSize(R.dimen.home_profile_icon_size) * 2).coerceIn(256, 1024)

        Thread {
            val userRes = api.user(userId)
            val commRes = api.communityStats(userId)

            val userObj = when (userRes) {
                is RedactedResult.Success -> userRes.responseOrNull()
                else -> null
            }
            val commObj = when (commRes) {
                is RedactedResult.Success -> commRes.responseOrNull()
                else -> null
            }

            when (userRes) {
                is RedactedResult.Failure -> runOnUiThread {
                    binding.loadingTopBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.swipeRefresh.visibility = View.GONE
                    binding.textError.visibility = View.VISIBLE
                    binding.textError.text = userRes.message
                    profileUi.clearProfileContainers()
                    supportActionBar?.title = getString(R.string.redacted_user_profile)
                }
                is RedactedResult.Success -> {
                    val avatarUrl = RedactedHomeStatsFormatter.avatarUrlFromUserResponse(userObj)
                    val bmp: Bitmap? = RedactedAvatarLoader.loadBitmap(avatarUrl, key, maxSidePx = iconPx)
                    val sections = RedactedProfileUiBuilder.build(
                        index = null,
                        user = userObj,
                        communityStats = commObj,
                    )
                    val username = userObj?.optString("username").orEmpty()

                    runOnUiThread {
                        binding.loadingTopBar.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        binding.textError.visibility = View.GONE
                        binding.swipeRefresh.visibility = View.VISIBLE
                        supportActionBar?.title =
                            username.ifBlank { getString(R.string.redacted_user_profile) }
                        profileUi.inflateProfileSections(sections)
                        if (bmp != null) {
                            clearProfileImageTint()
                            binding.imageProfile.setImageBitmap(bmp)
                        } else {
                            binding.imageProfile.setImageResource(android.R.drawable.ic_menu_myplaces)
                            applyProfilePlaceholderIconTint()
                        }
                    }
                }
                else -> runOnUiThread {
                    binding.loadingTopBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.swipeRefresh.visibility = View.GONE
                    binding.textError.visibility = View.VISIBLE
                    binding.textError.text = getString(R.string.redacted_unexpected)
                }
            }
        }.start()
    }
}
