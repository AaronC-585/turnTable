package com.turntable.barcodescanner.redacted

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.turntable.barcodescanner.BrowserLaunch
import com.turntable.barcodescanner.SearchPrefs
import java.io.File

object RedactedUiHelper {

    enum class TorrentDownloadOutcome {
        /** Saved under the user-chosen folder (Settings). */
        SavedToPreferredFolder,

        /** Wrote to app cache and opened the share chooser. */
        Shared,

        /** Could not write or open share. */
        Failed,
    }

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

    /**
     * If [SearchPrefs.redactedTorrentDownloadTreeUri] is set, writes the `.torrent` there.
     * Otherwise copies to cache and opens the share sheet. If the folder write fails, falls back to share.
     */
    fun deliverDownloadedTorrent(context: Context, torrentId: Int, bytes: ByteArray): TorrentDownloadOutcome {
        val treeStr = SearchPrefs(context).redactedTorrentDownloadTreeUri?.trim().orEmpty()
        if (treeStr.isNotEmpty()) {
            val treeUri = Uri.parse(treeStr)
            if (saveTorrentToTree(context, treeUri, torrentId, bytes)) {
                return TorrentDownloadOutcome.SavedToPreferredFolder
            }
        }
        return if (shareTorrentViaChooser(context, torrentId, bytes)) {
            TorrentDownloadOutcome.Shared
        } else {
            TorrentDownloadOutcome.Failed
        }
    }

    private fun saveTorrentToTree(context: Context, treeUri: Uri, torrentId: Int, bytes: ByteArray): Boolean {
        return try {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            if (!tree.canWrite()) return false
            val baseName = "redacted_$torrentId"
            val withExt = "$baseName.torrent"
            tree.findFile(withExt)?.delete()
            tree.findFile(baseName)?.delete()
            val doc = tree.createFile("application/x-bittorrent", baseName)
                ?: tree.createFile("application/octet-stream", withExt)
                ?: return false
            context.contentResolver.openOutputStream(doc.uri)?.use { os ->
                os.write(bytes)
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun shareTorrentViaChooser(context: Context, torrentId: Int, bytes: ByteArray): Boolean {
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
        BrowserLaunch.openHttpUrl(context, url)
    }

}
