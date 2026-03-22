package com.turntable.barcodescanner

import android.content.Context
import com.turntable.barcodescanner.debug.OutgoingUrlInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Update check: fetches **`CurrentVersion.json`** from **`github_update_current_version_json_url`** (full raw URL),
 * or if that is empty, from **`raw.githubusercontent.com/{owner}/{repo}/{ref}/CurrentVersion.json`**. Decodes
 * **`version`**, **`releasePageUrl`**, and APK URL from **`assets`** / **`apkUrl`**. No GitHub API for discovery.
 */
object GithubAppUpdateChecker {

    private const val CURRENT_MANIFEST_REPO_PATH = "CurrentVersion.json"

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
        val apkBrowserDownloadUrl: String?,
    )

    fun normalizedTag(tagName: String): String = DottedVersionCompare.normalizedForCompare(tagName)

    fun isGithubUpdateConfigured(context: Context): Boolean {
        if (context.getString(R.string.github_update_current_version_json_url).trim().isNotEmpty()) {
            return true
        }
        val owner = context.getString(R.string.github_update_owner).trim()
        val repo = context.getString(R.string.github_update_repo).trim()
        return owner.isNotEmpty() && repo.isNotEmpty()
    }

    /** Optional: GitHub releases/latest JSON URL (not used for version discovery). */
    fun latestReleaseApiUrl(context: Context): String? {
        if (!isGithubUpdateConfigured(context)) return null
        val owner = context.getString(R.string.github_update_owner).trim()
        val repo = context.getString(R.string.github_update_repo).trim()
        return "https://api.github.com/repos/$owner/$repo/releases/latest"
    }

    /** Resolved URL used to fetch CurrentVersion.json (explicit string or owner/repo/ref). */
    fun rawCurrentVersionJsonUrl(context: Context): String? = manifestJsonUrl(context)

    /** Full raw URL if set in resources, else `raw.githubusercontent.com/{owner}/{repo}/{ref}/CurrentVersion.json`. */
    private fun manifestJsonUrl(context: Context): String? {
        val direct = context.getString(R.string.github_update_current_version_json_url).trim()
        if (direct.isNotEmpty()) return direct
        return rawRepoFileUrl(context, CURRENT_MANIFEST_REPO_PATH)
    }

    private fun rawRepoFileUrl(context: Context, repoRelativePath: String): String? {
        val owner = context.getString(R.string.github_update_owner).trim()
        val repo = context.getString(R.string.github_update_repo).trim()
        val ref = context.getString(R.string.github_update_version_ref).trim().ifEmpty { "main" }
        if (owner.isEmpty() || repo.isEmpty()) return null
        return "https://raw.githubusercontent.com/$owner/$repo/$ref/$repoRelativePath"
    }

    /**
     * Fetches and decodes **only** [CURRENT_MANIFEST_REPO_PATH].
     */
    fun fetchLatestReleaseWithFallback(context: Context): Result<ReleaseInfo> =
        fetchCurrentVersionJson(context)

    private fun fetchCurrentVersionJson(context: Context): Result<ReleaseInfo> {
        val url = manifestJsonUrl(context)
            ?: return Result.failure(
                IllegalStateException("Set github_update_current_version_json_url or github_update_owner/repo"),
            )
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "turnTable/1.0 (Android)")
            .build()
        return try {
            http.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException(
                            "$CURRENT_MANIFEST_REPO_PATH HTTP ${response.code}: ${body.take(120)}",
                        ),
                    )
                }
                parseReleaseManifestJson(body, context, CURRENT_MANIFEST_REPO_PATH)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses **releases/latest** JSON (unused for normal update checks; kept for optional tooling).
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

    private fun parseReleaseManifestJson(
        body: String,
        context: Context,
        sourceLabel: String,
    ): Result<ReleaseInfo> {
        val owner = context.getString(R.string.github_update_owner).trim()
        val repo = context.getString(R.string.github_update_repo).trim()
        return try {
            val root = JSONObject(body.trim())
            val versionPlain = root.optString("version").trim().ifEmpty {
                root.optString("versionName").trim()
            }
            if (versionPlain.isEmpty()) {
                return Result.failure(IllegalStateException("Missing version in $sourceLabel"))
            }
            val norm = DottedVersionCompare.normalizedForCompare(versionPlain)
            if (norm.isEmpty() || !norm.any { it.isDigit() }) {
                return Result.failure(
                    IllegalStateException("Invalid version in $sourceLabel: ${versionPlain.take(40)}"),
                )
            }
            val tagRaw = versionPlain.removePrefix("V").let { t ->
                if (t.startsWith("v")) t else "v$t"
            }
            var htmlUrl = root.optString("releasePageUrl").trim()
            if (htmlUrl.isEmpty()) {
                htmlUrl = if (owner.isNotEmpty() && repo.isNotEmpty()) {
                    "https://github.com/$owner/$repo/releases/latest"
                } else {
                    ""
                }
            }
            if (htmlUrl.isEmpty()) {
                return Result.failure(
                    IllegalStateException("Missing releasePageUrl in $sourceLabel (and no owner/repo fallback)"),
                )
            }
            var apkUrl = extractApkUrlFromManifest(root)?.trim()?.takeIf { it.isNotEmpty() }
            if (apkUrl.isNullOrEmpty() && owner.isNotEmpty() && repo.isNotEmpty()) {
                apkUrl = syntheticReleaseApkUrl(owner, repo, norm)
            }
            Result.success(
                ReleaseInfo(
                    tagNameRaw = tagRaw,
                    title = root.optString("title").trim().takeIf { it.isNotEmpty() },
                    body = root.optString("body").trim().takeIf { it.isNotEmpty() },
                    htmlUrl = htmlUrl,
                    apkBrowserDownloadUrl = apkUrl,
                ),
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException("$sourceLabel: ${e.message ?: e.javaClass.simpleName}", e))
        }
    }

    private fun extractApkUrlFromManifest(root: JSONObject): String? {
        root.optString("apkUrl").trim().takeIf { it.isNotEmpty() }?.let { return it }
        val obj = root.optJSONObject("assets")
        if (obj != null) {
            obj.optString("apk").trim().takeIf { it.isNotEmpty() }?.let { return it }
            obj.optString("android").trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        val arr = root.optJSONArray("assets")
        if (arr != null) {
            val fromArray = pickApkUrlFromManifestAssetsArray(arr)
            if (fromArray != null) return fromArray
        }
        return null
    }

    private fun pickApkUrlFromManifestAssetsArray(arr: JSONArray): String? {
        val candidates = mutableListOf<Pair<Int, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = o.optString("url").trim().ifEmpty { o.optString("browser_download_url").trim() }
            if (url.isEmpty()) continue
            val score = when {
                name.contains("release", ignoreCase = true) -> 2
                else -> 1
            }
            candidates.add(score to url)
        }
        return candidates.maxByOrNull { it.first }?.second
    }

    private fun syntheticReleaseApkUrl(owner: String, repo: String, versionPlain: String): String =
        "https://github.com/$owner/$repo/releases/download/v$versionPlain/turnTable.release-$versionPlain.apk"

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
