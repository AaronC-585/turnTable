package com.turntable.barcodescanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.turntable.barcodescanner.databinding.ActivityHomeBinding
import com.turntable.barcodescanner.redacted.RedactedApiClient
import com.turntable.barcodescanner.redacted.RedactedAvatarLoader
import com.turntable.barcodescanner.redacted.RedactedHomeStatsFormatter
import com.turntable.barcodescanner.redacted.RedactedIndexNotifications
import com.turntable.barcodescanner.redacted.RedactedProfileUiBuilder
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.redacted.RedactedSiteNotificationHelper
import com.turntable.barcodescanner.debug.AppEventLog
import com.turntable.barcodescanner.redacted.responseOrNull
import org.json.JSONObject

/**
 * App **home page** after the splash screen: Redacted profile summary when an API key is set,
 * or prompt to enter one. First two profile sections sit beside the avatar; the rest are
 * collapsible. Pull-to-refresh reloads profile data.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var profileUi: HomeProfileLayoutController

    private val autoRefreshHandler = Handler(Looper.getMainLooper())
    private val autoRefreshRunnable = Runnable {
        if (!SearchPrefs(this).redactedApiKey.isNullOrBlank() &&
            binding.swipeRefresh.visibility == View.VISIBLE
        ) {
            loadProfile(isPullRefresh = true)
        }
        scheduleAutoRefresh()
    }

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional: could toast if denied */ }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra(EXTRA_POST_SCAN_BARCODE)) {
            dispatchPendingSearchFromScanner(intent)
        } else if (!SearchPrefs(this).redactedApiKey.isNullOrBlank()) {
            loadProfile(isPullRefresh = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        profileUi = HomeProfileLayoutController(this, binding)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.home_title)

        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.home_token_label),
            ContextCompat.getColor(this, R.color.home_ratio_ok),
        )
        binding.swipeRefresh.setOnRefreshListener {
            AppBottomBars.refreshTrackerNow(this)
            loadProfile(isPullRefresh = true)
        }

        binding.buttonAddApiKey.setOnClickListener { showApiKeyDialog(allowDismissToNoKey = true) }

        binding.textNoApiKeyHint.setRichHelp(R.string.home_no_api_key_hint)

        if (SearchPrefs(this).redactedApiKey.isNullOrBlank()) {
            showApiKeyDialog(allowDismissToNoKey = true)
        } else {
            loadProfile(isPullRefresh = false)
        }

        binding.root.post { dispatchPendingSearchFromScanner(intent) }
    }

    /**
     * After a scan, [MainActivity] brings Home to the front with this extra; we open [SearchActivity]
     * with the barcode so the user leaves the camera and lands on home with search on top.
     */
    private fun dispatchPendingSearchFromScanner(intent: Intent?) {
        val barcode = intent?.getStringExtra(EXTRA_POST_SCAN_BARCODE)?.trim().orEmpty()
        if (barcode.isEmpty()) return
        AppEventLog.log(AppEventLog.Category.SCAN, "home opened search from scan barcode=$barcode")
        intent?.removeExtra(EXTRA_POST_SCAN_BARCODE)
        startActivity(
            Intent(this, SearchActivity::class.java)
                .putExtra(SearchActivity.EXTRA_BARCODE, barcode),
        )
    }

    override fun onResume() {
        super.onResume()
        maybeRequestNotificationPermissionOnce()
        UpdateCheckCoordinator.consumePendingSplashUpdateIfAny(this)
        UpdateCheckCoordinator.requestBackgroundCheckIfDue(this)
        if (!SearchPrefs(this).redactedApiKey.isNullOrBlank()) {
            scheduleAutoRefresh()
        }
    }

    /** Ask for POST_NOTIFICATIONS at most once (avoid prompting every resume). */
    private fun maybeRequestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val sp = getPreferences(MODE_PRIVATE)
        if (sp.getBoolean(PREF_ASKED_NOTIF_PERMISSION, false)) return
        sp.edit().putBoolean(PREF_ASKED_NOTIF_PERMISSION, true).apply()
        requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onPause() {
        cancelAutoRefresh()
        super.onPause()
    }

    override fun onRestart() {
        super.onRestart()
        if (!SearchPrefs(this).redactedApiKey.isNullOrBlank()) {
            loadProfile(isPullRefresh = false)
        }
    }

    private fun scheduleAutoRefresh() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL_MS)
    }

    private fun cancelAutoRefresh() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
    }

    private fun handleNotificationsFromIndex(indexResp: JSONObject) {
        AppBottomBars.applyMailUnreadCountFromIndex(this, indexResp)
        val notifJson = indexResp.optJSONObject("notifications") ?: run {
            SearchPrefs(this).lastRedactedNotificationsSnapshot = ""
            return
        }
        val eval = RedactedIndexNotifications.evaluate(notifJson)
        val prefs = SearchPrefs(this)
        if (eval.shouldNotify && eval.snapshot != prefs.lastRedactedNotificationsSnapshot) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                RedactedSiteNotificationHelper.showSiteAlerts(this, eval.reasons)
            }
        }
        prefs.lastRedactedNotificationsSnapshot = eval.snapshot
    }

    private fun showNoKeyState() {
        binding.loadingTopBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        binding.swipeRefresh.visibility = View.GONE
        binding.textError.visibility = View.GONE
        binding.layoutNoKey.visibility = View.VISIBLE
        profileUi.clearProfileContainers()
        binding.imageProfile.setImageResource(android.R.drawable.ic_menu_myplaces)
        applyProfilePlaceholderIconTint()
        supportActionBar?.title = getString(R.string.home_title)
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

    private fun showApiKeyDialog(allowDismissToNoKey: Boolean) {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            setHint(R.string.redacted_api_key_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setText(SearchPrefs(this@HomeActivity).redactedApiKey.orEmpty())
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val container = FrameLayout(this).apply {
            setPadding(pad, 0, pad, 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.redacted_api_key_dialog_title)
            .setMessage(R.string.redacted_api_key_permissions_message)
            .setView(container)
            .setPositiveButton(R.string.save) { d, _ ->
                val v = input.text?.toString()?.trim().orEmpty()
                if (v.isEmpty()) {
                    Toast.makeText(this, R.string.redacted_need_api_key, Toast.LENGTH_SHORT).show()
                } else {
                    SearchPrefs(this).redactedApiKey = v
                    d.dismiss()
                    binding.layoutNoKey.visibility = View.GONE
                    loadProfile(isPullRefresh = false)
                }
            }
        if (allowDismissToNoKey) {
            builder.setNegativeButton(android.R.string.cancel) { _, _ ->
                showNoKeyState()
            }
        }
        builder.setCancelable(allowDismissToNoKey)
        val dialog = builder.create()
        dialog.show()
        AppRichText.enableLinksInMessage(dialog)
    }

    private fun loadProfile(isPullRefresh: Boolean) {
        val key = SearchPrefs(this).redactedApiKey?.trim().orEmpty()
        if (key.isEmpty()) {
            showNoKeyState()
            return
        }

        if (!isPullRefresh) {
            binding.loadingTopBar.visibility = View.VISIBLE
            binding.swipeRefresh.visibility = View.GONE
        } else {
            binding.swipeRefresh.isRefreshing = true
        }
        binding.textError.visibility = View.GONE
        binding.layoutNoKey.visibility = View.GONE

        val iconPx = (resources.getDimensionPixelSize(R.dimen.home_profile_icon_size) * 2).coerceIn(256, 1024)

        Thread {
            val api = RedactedApiClient(key)
            val idx = api.index()
            when (idx) {
                is RedactedResult.Failure -> runOnUiThread {
                    binding.loadingTopBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.swipeRefresh.visibility = View.GONE
                    binding.textError.visibility = View.VISIBLE
                    binding.textError.text = idx.message
                    binding.layoutNoKey.visibility = View.VISIBLE
                    supportActionBar?.title = getString(R.string.home_title)
                }
                is RedactedResult.Success -> {
                    val indexResp = idx.response ?: idx.root
                    val username = indexResp.optString("username")
                    val userId = indexResp.optInt("id", 0)
                    val userRes = if (userId > 0) api.user(userId) else null
                    val commRes = if (userId > 0) api.communityStats(userId) else null

                    val userObj = when (userRes) {
                        is RedactedResult.Success -> userRes.responseOrNull()
                        else -> null
                    }
                    val commObj = when (commRes) {
                        is RedactedResult.Success -> commRes.responseOrNull()
                        else -> null
                    }

                    val avatarUrl = RedactedHomeStatsFormatter.avatarUrlFromUserResponse(userObj)
                    val bmp: Bitmap? = RedactedAvatarLoader.loadBitmap(avatarUrl, key, maxSidePx = iconPx)
                    val sections = RedactedProfileUiBuilder.build(indexResp, userObj, commObj)

                    runOnUiThread {
                        binding.loadingTopBar.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        binding.textError.visibility = View.GONE
                        binding.layoutNoKey.visibility = View.GONE
                        binding.swipeRefresh.visibility = View.VISIBLE
                        supportActionBar?.title =
                            username.ifBlank { getString(R.string.home_user_fallback) }
                        profileUi.inflateProfileSections(sections)
                        handleNotificationsFromIndex(indexResp)
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
                    binding.layoutNoKey.visibility = View.VISIBLE
                    supportActionBar?.title = getString(R.string.home_title)
                }
            }
        }.start()
    }

    companion object {
        /** Set by [MainActivity] after a scan; [HomeActivity] opens [SearchActivity] with this barcode. */
        const val EXTRA_POST_SCAN_BARCODE = "com.turntable.barcodescanner.EXTRA_POST_SCAN_BARCODE"

        private const val AUTO_REFRESH_INTERVAL_MS = 60_000L
        private const val PREF_ASKED_NOTIF_PERMISSION = "asked_post_notifications_permission"
    }
}
