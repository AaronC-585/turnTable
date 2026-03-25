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
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Other users' profiles use the same layout and section styling as [HomeActivity], built from
 * `user` + `community_stats` (no `index` — viewer-specific stats like bonus points from index
 * are omitted for the viewed user).
 */
class RedactedUserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var profileUi: HomeProfileLayoutController
    private lateinit var api: RedactedApiClient

    /** Set after intent or username resolution; used by [loadUser]. */
    private var activeUserId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RedactedUiHelper.requireApi(this) ?: return
        api = c

        val userId = intent.getIntExtra(RedactedExtras.USER_ID, 0)
        val username = intent.getStringExtra(RedactedExtras.USERNAME)?.trim().orEmpty()
        if (userId <= 0 && username.isEmpty()) {
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
        supportActionBar?.title = getString(R.string.redacted_user_profile)

        binding.rowProfileHomeAction.visibility = View.VISIBLE
        binding.buttonProfileNavigateHome.setOnClickListener { navigateToHome() }

        binding.layoutNoKey.visibility = View.GONE
        binding.buttonAddApiKey.visibility = View.GONE

        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.home_token_label),
            ContextCompat.getColor(this, R.color.home_ratio_ok),
        )
        binding.swipeRefresh.setOnRefreshListener {
            AppBottomBars.refreshTrackerNow(this)
            loadUser(isPullRefresh = true)
        }

        binding.textError.visibility = View.GONE
        binding.swipeRefresh.visibility = View.GONE

        when {
            userId > 0 -> {
                activeUserId = userId
                loadUser(isPullRefresh = false)
            }
            else -> resolveUsernameThenLoad(username)
        }
    }

    private fun resolveUserIdFromSearchResponse(resp: JSONObject, queryUsername: String): Int {
        val arr: JSONArray = resp.optJSONArray("results")
            ?: resp.optJSONArray("users")
            ?: JSONArray()
        val q = queryUsername.trim().lowercase(Locale.ROOT)
        var fallback = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val uid = o.optInt("userId", o.optInt("id", 0))
            if (uid <= 0) continue
            val un = o.optString("username").trim().lowercase(Locale.ROOT)
            if (un == q) return uid
            if (fallback == 0) fallback = uid
        }
        return fallback
    }

    private fun resolveUsernameThenLoad(username: String) {
        binding.loadingTopBar.visibility = View.VISIBLE
        binding.swipeRefresh.visibility = View.GONE
        Thread {
            val r = api.userSearch(username)
            when (r) {
                is RedactedResult.Failure -> runOnUiThread {
                    binding.loadingTopBar.visibility = View.GONE
                    Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                    finish()
                }
                is RedactedResult.Success -> {
                    val resp = r.response ?: JSONObject()
                    val uid = resolveUserIdFromSearchResponse(resp, username)
                    runOnUiThread {
                        if (uid <= 0) {
                            binding.loadingTopBar.visibility = View.GONE
                            Toast.makeText(this, R.string.redacted_no_results, Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            activeUserId = uid
                            loadUser(isPullRefresh = false)
                        }
                    }
                }
                else -> runOnUiThread {
                    binding.loadingTopBar.visibility = View.GONE
                    Toast.makeText(this, R.string.redacted_unexpected, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.start()
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

    private fun loadUser(isPullRefresh: Boolean) {
        val userId = activeUserId
        if (userId <= 0) {
            finish()
            return
        }
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
            val uploadsRes = api.userTorrents(userId, "uploaded", limit = 100)
            val seedingRes = api.userTorrents(userId, "seeding", limit = 100)

            val userObj = when (userRes) {
                is RedactedResult.Success -> userRes.responseOrNull()
                else -> null
            }
            val commObj = when (commRes) {
                is RedactedResult.Success -> commRes.responseOrNull()
                else -> null
            }
            val uploadRows = when (uploadsRes) {
                is RedactedResult.Success ->
                    RedactedProfileUiBuilder.parseUploadedTorrents(uploadsRes.response, userId)
                else -> emptyList()
            }
            val seedingRows = when (seedingRes) {
                is RedactedResult.Success ->
                    RedactedProfileUiBuilder.parseSeedingTorrents(seedingRes.response)
                else -> emptyList()
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
                    ).toMutableList()
                    if (uploadRows.isNotEmpty()) {
                        sections.add(
                            RedactedProfileUiBuilder.ProfileSection(
                                titleRes = R.string.home_section_uploads,
                                rows = emptyList(),
                                uploadRows = uploadRows,
                            ),
                        )
                    }
                    if (seedingRows.isNotEmpty()) {
                        sections.add(
                            RedactedProfileUiBuilder.ProfileSection(
                                titleRes = R.string.home_section_seeding,
                                rows = emptyList(),
                                uploadRows = seedingRows,
                            ),
                        )
                    }
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
