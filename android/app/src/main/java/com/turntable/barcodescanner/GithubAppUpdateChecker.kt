package com.turntable.barcodescanner

import android.content.Context
import com.turntable.barcodescanner.debug.OutgoingUrlInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches the latest **public** GitHub release (REST API) and/or reads
 * `android/app/update-check-latest-version.txt` on `raw.githubusercontent.com` (see `rawLatestVersionFileUrl`).
 * when the API fails (rate limit, network). Compares the remote version to the app [versionName].
 * @see <a href="https://docs.github.com/en/rest/releases/releases#get-the-latest-release">GitHub API</a>
 */
object GithubAppUpdateChecker {

    private const val VERSION_FILE_REPO_PATH = "android/app/update-check-latest-version.txt"

    private val http = OkHttpClient.Builder()
        .addInterceptor(OutgoingUrlInterceptor)
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

    /** Raw URL to [VERSION_FILE_REPO_PATH] on GitHub (CDN); null if owner/repo not set. */
    fun rawLatestVersionFileUrl(context: Context): String? {
        val owner = context.getString(R.string.github_update_owner).trim()
        val repo = context.getString(R.string.github_update_repo).trim()
        val ref = context.getString(R.string.github_update_version_ref).trim().ifEmpty { "main" }
        if (owner.isEmpty() || repo.isEmpty()) return null
        return "https://raw.githubusercontent.com/$owner/$repo/$ref/$VERSION_FILE_REPO_PATH"
    }

    /**
     * Prefer GitHub **releases/latest** JSON; on failure use the repo **version file** (see [rawLatestVersionFileUrl]).
     */
    fun fetchLatestReleaseWithFallback(context: Context): Result<ReleaseInfo> {
        val apiUrl = latestReleaseApiUrl(context)
        val apiResult = apiUrl?.let { fetchLatestRelease(it) }
        if (apiResult?.isSuccess == true) return apiResult

        val fileResult = fetchLatestReleaseFromVersionFile(context)
        if (fileResult.isSuccess) return fileResult

        return apiResult ?: fileResult
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
     * Reads the first meaningful line from the raw version file; builds [ReleaseInfo] with
     * synthetic release and APK URLs (same naming as `scripts/release-github.sh`).
     */
    fun fetchLatestReleaseFromVersionFile(context: Context): Result<ReleaseInfo> {
        val owner = context.getString(R.string.github_update_owner).trim()
        val repo = context.getString(R.string.github_update_repo).trim()
        val url = rawLatestVersionFileUrl(context)
            ?: return Result.failure(IllegalStateException("Missing GitHub owner/repo"))
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "turnTable/1.0 (Android)")
            .build()
        return try {
            http.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("Version file HTTP ${response.code}: ${body.take(120)}"),
                    )
                }
                val line = firstVersionLineFromFile(body)
                    ?: return Result.failure(IllegalStateException("Empty version file"))
                val norm = DottedVersionCompare.normalizedForCompare(line)
                if (norm.isEmpty() || !norm.any { it.isDigit() }) {
                    return Result.failure(IllegalStateException("Invalid version line: ${line.take(40)}"))
                }
                val tagRaw = line.trim().removePrefix("V").let { t ->
                    if (t.startsWith("v")) t else "v$t"
                }
                val htmlUrl = "https://github.com/$owner/$repo/releases/latest"
                val apkUrl = syntheticReleaseApkUrl(owner, repo, norm)
                Result.success(
                    ReleaseInfo(
                        tagNameRaw = tagRaw,
                        title = null,
                        body = null,
                        htmlUrl = htmlUrl,
                        apkBrowserDownloadUrl = apkUrl,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun firstVersionLineFromFile(body: String): String? =
        body.lineSequence()
            .map { it.substringBefore("#").trim() }
            .firstOrNull { it.isNotEmpty() }

    private fun syntheticReleaseApkUrl(owner: String, repo: String, versionPlain: String): String =
        "https://github.com/$owner/$repo/releases/download/v$versionPlain/turnTable.release-$versionPlain.apk"

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
