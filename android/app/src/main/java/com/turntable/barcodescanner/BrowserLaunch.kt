package com.turntable.barcodescanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.turntable.barcodescanner.debug.OutgoingUrlLog
import java.net.URLEncoder

/**
 * Opens http(s) links using the browser package from Settings ([SearchPrefs.secondaryBrowserPackage]).
 * Mirrors the previous SearchActivity behavior: pinned browser → Play Store for that browser if missing →
 * unpinned retry → error toast.
 */
object BrowserLaunch {

    /** Preferred browser package from settings, or null for system default. */
    fun preferredBrowserPackage(context: Context): String? =
        SearchPrefs(context).secondaryBrowserPackage?.takeIf { it.isNotBlank() }

    /**
     * [Intent.ACTION_VIEW] for [uri] with optional package from settings and [FLAG_ACTIVITY_NEW_TASK].
     */
    fun newViewIntent(context: Context, uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            preferredBrowserPackage(context)?.let { setPackage(it) }
        }
    }

    /**
     * Opens an http(s) URL in the configured browser.
     *
     * @param onDone same semantics as the old SearchActivity helper: `true` when the main [url] was opened;
     *   `false` when only Play Store / nothing worked (caller may still finish the activity).
     */
    fun openHttpUrl(context: Context, url: String, onDone: (success: Boolean) -> Unit = { _ -> }) {
        OutgoingUrlLog.log("VIEW", url)
        val uri = Uri.parse(url)
        val pkg = preferredBrowserPackage(context)
        val intent = newViewIntent(context, uri)
        try {
            context.startActivity(intent)
            onDone(true)
        } catch (_: Exception) {
            val playStoreUrl = KnownBrowsers.findByPackage(pkg)?.playStoreUrl
                ?: "https://play.google.com/store/apps/details?id=${
                    pkg?.let { URLEncoder.encode(it, Charsets.UTF_8.name()) } ?: ""
                }"
            val displayName = KnownBrowsers.findByPackage(pkg)?.name ?: pkg ?: ""
            if (!pkg.isNullOrBlank()) {
                try {
                    OutgoingUrlLog.log("VIEW", playStoreUrl)
                    context.startActivity(newViewIntent(context, Uri.parse(playStoreUrl)))
                    Toast.makeText(
                        context,
                        context.getString(R.string.browser_open_play_store, displayName.ifBlank { pkg }),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onDone(false)
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.could_not_open_link, Toast.LENGTH_SHORT).show()
                    onDone(false)
                }
            } else {
                try {
                    intent.setPackage(null)
                    context.startActivity(intent)
                    onDone(true)
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.could_not_open_link, Toast.LENGTH_SHORT).show()
                    onDone(false)
                }
            }
        }
    }
}
