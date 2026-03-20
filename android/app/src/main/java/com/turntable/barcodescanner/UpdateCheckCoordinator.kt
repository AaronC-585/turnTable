package com.turntable.barcodescanner

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Background check (home screen, throttled) and manual check (About / Settings).
 */
object UpdateCheckCoordinator {

    private const val BACKGROUND_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    fun requestBackgroundCheckIfDue(activity: AppCompatActivity) {
        val apiUrl = GithubAppUpdateChecker.latestReleaseApiUrl(activity) ?: return
        val prefs = UpdatePrefs(activity)
        val now = System.currentTimeMillis()
        if (now - prefs.lastBackgroundCheckWallTimeMs < BACKGROUND_CHECK_INTERVAL_MS) return
        prefs.lastBackgroundCheckWallTimeMs = now

        Thread {
            val localVer = currentVersionName(activity) ?: ""
            val result = GithubAppUpdateChecker.fetchLatestRelease(apiUrl)
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                result.fold(
                    onSuccess = { info ->
                        if (!GithubAppUpdateChecker.isRemoteNewerThanLocal(info.tagNameRaw, localVer)) {
                            return@fold
                        }
                        val norm = GithubAppUpdateChecker.normalizedTag(info.tagNameRaw)
                        if (norm == prefs.skippedReleaseTag) return@fold
                        showUpdateDialog(activity, info, localVer) {
                            prefs.skippedReleaseTag = norm
                        }
                    },
                    onFailure = { /* silent for background */ },
                )
            }
        }.start()
    }

    fun checkManually(activity: AppCompatActivity) {
        val apiUrl = GithubAppUpdateChecker.latestReleaseApiUrl(activity)
        if (apiUrl == null) {
            Toast.makeText(
                activity,
                R.string.github_update_not_configured,
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        Toast.makeText(activity, R.string.github_update_checking, Toast.LENGTH_SHORT).show()
        Thread {
            val localVer = currentVersionName(activity) ?: "—"
            val result = GithubAppUpdateChecker.fetchLatestRelease(apiUrl)
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                result.fold(
                    onSuccess = { info ->
                        if (GithubAppUpdateChecker.isRemoteNewerThanLocal(info.tagNameRaw, localVer)) {
                            showUpdateDialog(activity, info, localVer) {
                                UpdatePrefs(activity).skippedReleaseTag =
                                    GithubAppUpdateChecker.normalizedTag(info.tagNameRaw)
                            }
                        } else {
                            Toast.makeText(
                                activity,
                                activity.getString(
                                    R.string.github_update_up_to_date_fmt,
                                    localVer,
                                ),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    onFailure = { e ->
                        Toast.makeText(
                            activity,
                            activity.getString(
                                R.string.github_update_failed_fmt,
                                e.message ?: e.javaClass.simpleName,
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }.start()
    }

    private fun showUpdateDialog(
        activity: AppCompatActivity,
        info: GithubAppUpdateChecker.ReleaseInfo,
        localVersion: String,
        onSkipThisRelease: () -> Unit,
    ) {
        val summary = activity.getString(
            R.string.github_update_dialog_summary_fmt,
            localVersion,
            info.tagNameRaw,
        )
        val bodyPreview = info.body?.let { b ->
            val max = 400
            if (b.length <= max) "\n\n$b" else "\n\n${b.take(max)}…"
        }.orEmpty()
        val message = summary + bodyPreview

        data class Row(val label: String, val go: () -> Unit)

        val rows = mutableListOf<Row>()
        info.apkBrowserDownloadUrl?.let { apkUrl ->
            rows.add(
                Row(activity.getString(R.string.github_update_download_apk)) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
                },
            )
        }
        rows.add(
            Row(activity.getString(R.string.github_update_open_release)) {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl)))
            },
        )
        rows.add(
            Row(activity.getString(R.string.github_update_skip_version)) {
                onSkipThisRelease()
            },
        )

        val labels = rows.map { it.label }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.github_update_dialog_title)
            .setMessage(message)
            .setItems(labels) { d, which ->
                rows[which].go()
                d.dismiss()
            }
            .setNegativeButton(R.string.github_update_later, null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun currentVersionName(activity: AppCompatActivity): String? = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
    } catch (_: Exception) {
        null
    }
}
