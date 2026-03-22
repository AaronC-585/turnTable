package com.turntable.barcodescanner

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.turntable.barcodescanner.debug.OutgoingUrlLog
import okhttp3.Call
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Background check (home screen, throttled) and manual check (About / Settings).
 * Manual check: if a newer release has a direct APK URL, downloads and opens the installer automatically;
 * otherwise shows the release dialog (e.g. open in browser).
 */
object UpdateCheckCoordinator {

    private const val BACKGROUND_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    fun requestBackgroundCheckIfDue(activity: AppCompatActivity) {
        if (!GithubAppUpdateChecker.isGithubUpdateConfigured(activity)) return
        val prefs = UpdatePrefs(activity)
        val now = System.currentTimeMillis()
        if (now - prefs.lastBackgroundCheckWallTimeMs < BACKGROUND_CHECK_INTERVAL_MS) return
        prefs.lastBackgroundCheckWallTimeMs = now

        Thread {
            val localVer = currentVersionName(activity) ?: ""
            val result = GithubAppUpdateChecker.fetchLatestReleaseWithFallback(activity)
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
        if (!GithubAppUpdateChecker.isGithubUpdateConfigured(activity)) {
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
            val result = GithubAppUpdateChecker.fetchLatestReleaseWithFallback(activity)
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                result.fold(
                    onSuccess = { info ->
                        if (GithubAppUpdateChecker.isRemoteNewerThanLocal(info.tagNameRaw, localVer)) {
                            val apkUrl = info.apkBrowserDownloadUrl?.trim().orEmpty()
                            if (apkUrl.isNotEmpty()) {
                                val sp = SearchPrefs(activity)
                                if (sp.downloadOverWifiOnly &&
                                    !DownloadNetworkPolicy.allowsLargeDownload(activity, true)
                                ) {
                                    Toast.makeText(
                                        activity,
                                        R.string.github_update_wifi_only_blocked,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    showUpdateDialog(activity, info, localVer) {
                                        UpdatePrefs(activity).skippedReleaseTag =
                                            GithubAppUpdateChecker.normalizedTag(info.tagNameRaw)
                                    }
                                } else {
                                    Toast.makeText(
                                        activity,
                                        R.string.github_update_auto_downloading,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    startVisibleApkDownload(activity, apkUrl)
                                }
                            } else {
                                showUpdateDialog(activity, info, localVer) {
                                    UpdatePrefs(activity).skippedReleaseTag =
                                        GithubAppUpdateChecker.normalizedTag(info.tagNameRaw)
                                }
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
                    startVisibleApkDownload(activity, apkUrl)
                },
            )
        }
        rows.add(
            Row(activity.getString(R.string.github_update_open_release)) {
                BrowserLaunch.openHttpUrl(activity, info.htmlUrl)
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

    /** In-app download with progress, then system package installer (FileProvider). */
    private fun startVisibleApkDownload(activity: AppCompatActivity, apkUrl: String) {
        if (SearchPrefs(activity).downloadOverWifiOnly &&
            !DownloadNetworkPolicy.allowsLargeDownload(activity, true)
        ) {
            Toast.makeText(
                activity,
                R.string.github_update_wifi_only_blocked,
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        OutgoingUrlLog.log("GET", apkUrl)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_github_apk_download, null)
        val textStatus = view.findViewById<TextView>(R.id.textDownloadStatus)
        val progress = view.findViewById<ProgressBar>(R.id.progressDownload)

        val cancelled = AtomicBoolean(false)
        val callRef = AtomicReference<Call?>(null)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.github_update_download_apk)
            .setView(view)
            .setNegativeButton(R.string.github_update_cancel_download) { d, _ ->
                cancelled.set(true)
                callRef.get()?.cancel()
                d.dismiss()
            }
            .setCancelable(false)
            .create()

        dialog.show()
        textStatus.setText(R.string.github_update_download_starting)
        progress.progress = 0
        progress.isIndeterminate = true

        Thread {
            val result = AppUpdateApkDownloader.downloadApk(
                activity,
                apkUrl,
                callRef,
                cancelled,
            ) { pct, done, total ->
                activity.runOnUiThread {
                    if (activity.isFinishing) return@runOnUiThread
                    if (total > 0) {
                        progress.isIndeterminate = false
                        progress.progress = (pct ?: 0).coerceIn(0, 100)
                        textStatus.text = activity.getString(
                            R.string.github_update_download_progress_fmt,
                            pct ?: 0,
                            formatBytes(done),
                            formatBytes(total),
                        )
                    } else {
                        progress.isIndeterminate = true
                        textStatus.text = activity.getString(
                            R.string.github_update_download_progress_indeterminate_fmt,
                            formatBytes(done),
                        )
                    }
                }
            }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
                result.fold(
                    onSuccess = { file -> promptInstallDownloadedApk(activity, file) },
                    onFailure = { e ->
                        when (e) {
                            is AppUpdateDownloadCancelledException ->
                                Toast.makeText(
                                    activity,
                                    R.string.github_update_download_cancelled,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            else ->
                                Toast.makeText(
                                    activity,
                                    activity.getString(
                                        R.string.github_update_download_failed_fmt,
                                        e.message ?: e.javaClass.simpleName,
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    },
                )
            }
        }.start()
    }

    private fun promptInstallDownloadedApk(activity: AppCompatActivity, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(intent)
            Toast.makeText(
                activity,
                R.string.github_update_opening_installer,
                Toast.LENGTH_LONG,
            ).show()
        } catch (e: Exception) {
            var openedUnknownSources = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !activity.packageManager.canRequestPackageInstalls()
            ) {
                try {
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        },
                    )
                    openedUnknownSources = true
                } catch (_: Exception) {
                    // ignore
                }
            }
            if (openedUnknownSources) {
                Toast.makeText(
                    activity,
                    R.string.github_update_enable_unknown_sources_hint,
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                Toast.makeText(
                    activity,
                    activity.getString(
                        R.string.github_update_install_intent_failed_fmt,
                        e.message ?: e.javaClass.simpleName,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun formatBytes(n: Long): String {
        if (n < 0L) return "—"
        if (n < 1024L) return "$n B"
        val kb = n / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    private fun currentVersionName(activity: AppCompatActivity): String? = try {
        val pm = activity.packageManager
        val pkg = activity.packageName
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0).versionName
        }
    } catch (_: Exception) {
        null
    }
}
