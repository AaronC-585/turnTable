package com.turntable.barcodescanner.redacted

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.turntable.barcodescanner.SearchPrefs
import java.io.File

object RedactedUiHelper {

    fun apiClientOrNull(context: Context): RedactedApiClient? {
        val key = SearchPrefs(context).redactedApiKey?.trim().orEmpty()
        if (key.isEmpty()) return null
        return RedactedApiClient(key)
    }

    fun requireApi(activity: AppCompatActivity): RedactedApiClient? {
        val c = apiClientOrNull(activity)
        if (c == null) {
            Toast.makeText(activity, com.turntable.barcodescanner.R.string.redacted_need_api_key, Toast.LENGTH_LONG).show()
            activity.finish()
        }
        return c
    }

    fun shareTorrentFile(context: Context, torrentId: Int, bytes: ByteArray): Boolean {
        return try {
            val dir = File(context.cacheDir, "redacted").apply { mkdirs() }
            val f = File(dir, "redacted_$torrentId.torrent")
            f.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                f,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/x-bittorrent"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, context.getString(com.turntable.barcodescanner.R.string.redacted_share_torrent)))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openSite(context: Context, path: String) {
        val url = if (path.startsWith("http")) path else "https://redacted.sh/$path"
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
}
