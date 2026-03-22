package com.turntable.barcodescanner

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.os.SystemClock
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.turntable.barcodescanner.redacted.RedactedApiClient
import com.turntable.barcodescanner.redacted.RedactedIndexNotifications
import com.turntable.barcodescanner.redacted.RedactedResult
import com.turntable.barcodescanner.TrackerStatusClient.Service
import org.json.JSONObject

/**
 * Global bottom dock: tracker status strip + shortcut row. Attached to most activities via
 * [TurnTableApp] lifecycle callbacks.
 */
object AppBottomBars {

    private const val TRACKER_THROTTLE_MS = 45_000L
    private const val MAIL_BADGE_MIN_INTERVAL_MS = 25_000L

    @Volatile
    private var lastTrackerFetchElapsed: Long = 0L

    @Volatile
    private var lastMailBadgeFetchElapsed: Long = 0L

    private val excludedActivities: Set<Class<out Activity>> = setOf(
        SplashActivity::class.java,
        PermissionOnboardingActivity::class.java,
    )

    private class Callbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity !is AppCompatActivity) return
            if (excludedActivities.contains(activity.javaClass)) return
            activity.window.decorView.post {
                attachIfNeeded(activity)
            }
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity !is AppCompatActivity) return
            if (excludedActivities.contains(activity.javaClass)) return
            maybeRefreshTracker(activity, force = false)
            maybeRefreshMailUnreadBadge(activity)
        }

        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    fun registerIn(application: Application) {
        application.registerActivityLifecycleCallbacks(Callbacks())
    }

    fun attachIfNeeded(activity: AppCompatActivity) {
        if (excludedActivities.contains(activity.javaClass)) return
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        if (content.childCount != 1) return
        val child = content.getChildAt(0)
        if (child.getTag(R.id.tag_turn_table_bottom_wrapped) == true) return

        content.removeView(child)
        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        outer.addView(
            child,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        val dock = activity.layoutInflater.inflate(R.layout.include_app_bottom_dock, outer, false)
        outer.addView(dock)
        outer.setTag(R.id.tag_turn_table_bottom_wrapped, true)
        content.addView(outer)

        wireDock(activity, dock)
        dock.findViewById<View>(R.id.trackerStatusBar).alpha = 0.45f
        maybeRefreshTracker(activity, force = true)
        maybeRefreshMailUnreadBadge(activity, force = true)
    }

    private fun wireDock(activity: AppCompatActivity, dock: View) {
        val showLegend = View.OnClickListener {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.tracker_status_legend_title)
                .setMessage(R.string.tracker_status_legend_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        listOf(
            R.id.imageTrackerWebsite,
            R.id.imageTrackerHttp,
            R.id.imageTrackerHttps,
            R.id.imageTrackerIrc,
            R.id.imageTrackerAnnouncer,
            R.id.imageTrackerUserId,
        ).forEach { id ->
            dock.findViewById<ImageView>(id).setOnClickListener(showLegend)
        }

        dock.findViewById<View>(R.id.buttonHomeNews).setOnClickListener {
            if (SearchPrefs(activity).redactedApiKey.isNullOrBlank()) {
                Toast.makeText(activity, R.string.redacted_need_api_key, Toast.LENGTH_LONG).show()
            } else {
                activity.startActivity(Intent(activity, RedactedAnnouncementsActivity::class.java))
            }
        }
        dock.findViewById<View>(R.id.buttonHomeForums).setOnClickListener {
            if (SearchPrefs(activity).redactedApiKey.isNullOrBlank()) {
                Toast.makeText(activity, R.string.redacted_need_api_key, Toast.LENGTH_LONG).show()
            } else {
                activity.startActivity(Intent(activity, RedactedForumMainActivity::class.java))
            }
        }
        dock.findViewById<View>(R.id.buttonHomeMail).setOnClickListener {
            if (SearchPrefs(activity).redactedApiKey.isNullOrBlank()) {
                Toast.makeText(activity, R.string.redacted_need_api_key, Toast.LENGTH_LONG).show()
            } else {
                activity.startActivity(Intent(activity, RedactedInboxActivity::class.java))
            }
        }
        dock.findViewById<View>(R.id.buttonHomeFriends).setOnClickListener {
            if (SearchPrefs(activity).redactedApiKey.isNullOrBlank()) {
                Toast.makeText(activity, R.string.redacted_need_api_key, Toast.LENGTH_LONG).show()
            } else {
                activity.startActivity(Intent(activity, RedactedFriendsActivity::class.java))
            }
        }
        dock.findViewById<View>(R.id.buttonHomeHome).setOnClickListener {
            activity.navigateToHome()
        }
        dock.findViewById<View>(R.id.buttonHomeScan).setOnClickListener {
            activity.startActivity(Intent(activity, MainActivity::class.java))
        }
        dock.findViewById<View>(R.id.buttonHomeHistory).setOnClickListener {
            activity.startActivity(Intent(activity, SearchHistoryActivity::class.java))
        }
        dock.findViewById<View>(R.id.buttonHomeSettings).setOnClickListener {
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }
        dock.findViewById<View>(R.id.buttonHomeRedacted).setOnClickListener {
            if (SearchPrefs(activity).redactedApiKey.isNullOrBlank()) {
                Toast.makeText(activity, R.string.redacted_need_api_key, Toast.LENGTH_LONG).show()
            } else {
                activity.startActivity(Intent(activity, RedactedBrowseActivity::class.java))
            }
        }
    }

    /** Force refresh (e.g. pull-to-refresh on home). */
    fun refreshTrackerNow(activity: AppCompatActivity) {
        maybeRefreshTracker(activity, force = true)
    }

    /** Re-fetch unread PM count for the dock mail badge (e.g. after leaving inbox). */
    fun refreshMailUnreadBadgeNow(activity: AppCompatActivity) {
        maybeRefreshMailUnreadBadge(activity, force = true)
    }

    /**
     * Updates the mail shortcut badge from an [index] response already loaded (e.g. Home profile),
     * so the count stays in sync without a second API call.
     */
    fun applyMailUnreadCountFromIndex(activity: AppCompatActivity, indexResponse: JSONObject) {
        // Skip the next throttled [index] poll — we already have fresh counts from this response.
        lastMailBadgeFetchElapsed = SystemClock.elapsedRealtime()
        val notif = indexResponse.optJSONObject("notifications")
        val count = RedactedIndexNotifications.unreadPrivateMessageCount(notif)
        activity.window.decorView.post {
            if (activity.isFinishing) return@post
            setMailBadgeCount(activity, count)
        }
    }

    private fun maybeRefreshMailUnreadBadge(activity: AppCompatActivity, force: Boolean = false) {
        if (excludedActivities.contains(activity.javaClass)) return
        val key = SearchPrefs(activity).redactedApiKey?.trim().orEmpty()
        if (key.isEmpty()) {
            activity.window.decorView.post {
                if (!activity.isFinishing) setMailBadgeCount(activity, 0)
            }
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && lastMailBadgeFetchElapsed != 0L &&
            now - lastMailBadgeFetchElapsed < MAIL_BADGE_MIN_INTERVAL_MS
        ) {
            return
        }
        lastMailBadgeFetchElapsed = now

        Thread {
            val r = RedactedApiClient(key).index()
            val count = when (r) {
                is RedactedResult.Success -> {
                    val body = r.response ?: r.root
                    RedactedIndexNotifications.unreadPrivateMessageCount(
                        body.optJSONObject("notifications"),
                    )
                }
                else -> null
            }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                if (count != null) setMailBadgeCount(activity, count)
            }
        }.start()
    }

    private fun setMailBadgeCount(activity: AppCompatActivity, count: Int) {
        val dock = activity.findViewById<View>(R.id.appBottomDock) ?: return
        val badge = dock.findViewById<TextView>(R.id.badgeMailUnread) ?: return
        val mailColumn = dock.findViewById<LinearLayout>(R.id.homeActionMail) ?: return
        if (count <= 0) {
            badge.visibility = View.GONE
            mailColumn.contentDescription = activity.getString(R.string.home_content_cd_mail)
            return
        }
        badge.visibility = View.VISIBLE
        badge.text = if (count > 99) "99+" else count.toString()
        mailColumn.contentDescription = activity.getString(
            R.string.home_content_cd_mail_unread,
            count,
        )
    }

    private fun maybeRefreshTracker(activity: AppCompatActivity, force: Boolean) {
        if (excludedActivities.contains(activity.javaClass)) return
        val dock = activity.findViewById<View>(R.id.appBottomDock) ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastTrackerFetchElapsed < TRACKER_THROTTLE_MS) return
        lastTrackerFetchElapsed = now

        Thread {
            val result = TrackerStatusClient.fetch()
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                applyTrackerResult(activity, dock, result)
            }
        }.start()
    }

    private fun applyTrackerResult(
        activity: AppCompatActivity,
        dock: View,
        result: Result<List<TrackerStatusClient.Row>>,
    ) {
        dock.findViewById<View>(R.id.trackerStatusBar).alpha = 1f
        val views = listOf(
            dock.findViewById<ImageView>(R.id.imageTrackerWebsite),
            dock.findViewById<ImageView>(R.id.imageTrackerHttp),
            dock.findViewById<ImageView>(R.id.imageTrackerHttps),
            dock.findViewById<ImageView>(R.id.imageTrackerIrc),
            dock.findViewById<ImageView>(R.id.imageTrackerAnnouncer),
            dock.findViewById<ImageView>(R.id.imageTrackerUserId),
        )
        result.fold(
            onSuccess = { rows ->
                rows.zip(views).forEach { (row, iv) ->
                    iv.setImageResource(row.service.icon)
                    ImageViewCompat.setImageTintList(
                        iv,
                        ColorStateList.valueOf(
                            ContextCompat.getColor(
                                activity,
                                if (row.ok) R.color.tracker_status_up else R.color.tracker_status_down,
                            ),
                        ),
                    )
                    val state = activity.getString(
                        if (row.ok) R.string.tracker_status_up else R.string.tracker_status_down,
                    )
                    val title = activity.getString(trackerTitleRes(row.service))
                    val lat = activity.getString(R.string.tracker_status_latency, row.latency)
                    iv.contentDescription = activity.getString(
                        R.string.tracker_status_a11y,
                        title,
                        state,
                        lat,
                    )
                }
            },
            onFailure = {
                Service.entries.zip(views).forEach { (svc, iv) ->
                    iv.setImageResource(svc.icon)
                    ImageViewCompat.setImageTintList(
                        iv,
                        ColorStateList.valueOf(
                            ContextCompat.getColor(activity, R.color.tracker_status_down),
                        ),
                    )
                    val title = activity.getString(trackerTitleRes(svc))
                    iv.contentDescription = activity.getString(
                        R.string.tracker_status_a11y,
                        title,
                        activity.getString(R.string.tracker_status_down),
                        activity.getString(R.string.tracker_status_latency, "—"),
                    )
                }
            },
        )
    }

    private fun trackerTitleRes(s: Service): Int = when (s) {
        Service.WEBSITE -> R.string.tracker_status_service_website
        Service.TRACKER_HTTP -> R.string.tracker_status_service_http
        Service.TRACKER_HTTPS -> R.string.tracker_status_service_https
        Service.IRC -> R.string.tracker_status_service_irc
        Service.IRC_TORRENT_ANNOUNCER -> R.string.tracker_status_service_announcer
        Service.IRC_USER_IDENTIFIER -> R.string.tracker_status_service_user_id
    }
}
