package com.turntable.barcodescanner

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches the latest **public** GitHub release and compares [tag_name] to the app [versionName].
 * @see <a href="https://docs.github.com/en/rest/releases/releases#get-the-latest-release">GitHub API</a>
 */
object GithubAppUpdateChecker {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class ReleaseInfo(
        val tagNameRaw: String,
        val title: String?,
        val body: String?,
        val htmlUrl: String,
        /** Direct .apk asset URL when the release includes one; else null (use [htmlUrl]). */
        val apkBrowserDownloadUrl: String?,
    )

    fun normalizedTag(tagName: String): String = DottedVersionCompare.normalizedForCompare(tagName)

    fun latestReleaseApiUrl(context: Context): String? {
        val owner = context.getString(R.string.github_update_owner).trim()
        val repo = context.getString(R.string.github_update_repo).trim()
        if (owner.isEmpty() || repo.isEmpty()) return null
        return "https://api.github.com/repos/$owner/$repo/releases/latest"
    }

    /**
     * @return parsed release or failure (network / HTTP / JSON).
     */
    fun fetchLatestRelease(apiUrl: String): Result<ReleaseInfo> {
        val req = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "turnTable/1.0 (Android)")
            .build()
        return try {
            http.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("HTTP ${response.code}: ${body.take(200)}"),
                    )
                }
                val json = JSONObject(body)
                val tag = json.optString("tag_name").trim()
                if (tag.isEmpty()) {
                    return Result.failure(IllegalStateException("Missing tag_name"))
                }
                val name = json.optString("name").trim().takeIf { it.isNotEmpty() }
                val bodyText = json.optString("body").trim().takeIf { it.isNotEmpty() }
                val htmlUrl = json.optString("html_url").trim()
                if (htmlUrl.isEmpty()) {
                    return Result.failure(IllegalStateException("Missing html_url"))
                }
                val apkUrl = pickApkDownloadUrl(json.optJSONArray("assets"))
                Result.success(
                    ReleaseInfo(
                        tagNameRaw = tag,
                        title = name,
                        body = bodyText,
                        htmlUrl = htmlUrl,
                        apkBrowserDownloadUrl = apkUrl,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * True if the release tag is **newer** than [localVersionName] (from [PackageManager]).
     */
    fun isRemoteNewerThanLocal(remoteTag: String, localVersionName: String): Boolean {
        val remoteNorm = normalizedTag(remoteTag)
        val localNorm = normalizedTag(localVersionName)
        if (remoteNorm.isEmpty() || localNorm.isEmpty()) return false
        return DottedVersionCompare.compare(remoteNorm, localNorm) > 0
    }

    private fun pickApkDownloadUrl(assets: JSONArray?): String? {
        if (assets == null || assets.length() == 0) return null
        val candidates = mutableListOf<Pair<Int, String>>()
        for (i in 0 until assets.length()) {
            val o = assets.optJSONObject(i) ?: continue
            val name = o.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = o.optString("browser_download_url").trim()
            if (url.isEmpty()) continue
            val score = when {
                name.contains("release", ignoreCase = true) -> 2
                else -> 1
            }
            candidates.add(score to url)
        }
        return candidates.maxByOrNull { it.first }?.second
    }
}
