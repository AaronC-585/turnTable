package com.turntable.barcodescanner.redacted

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import com.turntable.barcodescanner.debug.OutgoingUrlInterceptor
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Redacted JSON API client per site wiki documentation.
 * Auth: [Authorization] header with API key value (no "Bearer" prefix per docs).
 * Rate limit (API key): 10 requests / 10 seconds per user; HTTP 429 + Retry-After.
 */
class RedactedApiClient(private val apiKey: String) {

    /** Same value as the JSON API [Authorization] header — needed for gated cover URLs on redacted.sh. */
    fun redactedAuthorizationValue(): String = apiKey.trim()

    private val http = OkHttpClient.Builder()
        .addInterceptor(OutgoingUrlInterceptor)
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    companion object {
        const val BASE_URL = "https://redacted.sh/ajax.php"
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }

    private fun authorized(url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", apiKey.trim())
            .header("User-Agent", "turnTable/1.0 (Android)")

    private fun buildUrl(
        action: String,
        params: List<Pair<String, String?>> = emptyList(),
    ): HttpUrl {
        val b = BASE_URL.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("action", action)
        for ((k, v) in params) {
            if (v.isNullOrEmpty()) continue
            b.addEncodedQueryParameter(k, v)
        }
        return b.build()
    }

    private fun executeJson(request: Request): RedactedResult {
        return try {
            http.newCall(request).execute().use { resp -> handleResponseJson(resp) }
        } catch (e: Exception) {
            RedactedResult.Failure(e.message ?: "Network error", 0, null)
        }
    }

    private fun handleResponseJson(response: Response): RedactedResult {
        val code = response.code
        val retryAfter = response.header("Retry-After")?.toIntOrNull()
        if (code == 429) {
            return RedactedResult.Failure("Rate limited (429). Wait before retrying.", code, retryAfter)
        }
        val bodyStr = response.body?.string().orEmpty()
        if (bodyStr.isBlank()) {
            return RedactedResult.Failure("Empty response (HTTP $code)", code, retryAfter)
        }
        if (!response.isSuccessful) {
            return RedactedResult.Failure("HTTP $code: ${bodyStr.take(500)}", code, retryAfter)
        }
        return try {
            val root = JSONObject(bodyStr)
            when (root.optString("status", "")) {
                "success" -> RedactedResult.Success(root)
                "failure" -> {
                    val err = root.optString("error")
                        .ifBlank { root.optString("message") }
                        .ifBlank { "Request failed" }
                    RedactedResult.Failure(err, code, retryAfter)
                }
                else -> {
                    // Download endpoint returns binary; if we got JSON with unknown shape:
                    if (bodyStr.trimStart().startsWith("{")) {
                        RedactedResult.Success(root)
                    } else {
                        RedactedResult.Failure("Unexpected response", code, retryAfter)
                    }
                }
            }
        } catch (e: Exception) {
            RedactedResult.Failure("Invalid JSON: ${e.message}", code, retryAfter)
        }
    }

    private fun executeBinary(request: Request): RedactedResult {
        return try {
            http.newCall(request).execute().use { resp ->
                val code = resp.code
                val retryAfter = resp.header("Retry-After")?.toIntOrNull()
                if (code == 429) {
                    return RedactedResult.Failure("Rate limited (429)", code, retryAfter)
                }
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                val ct = resp.header("Content-Type").orEmpty()
                if (ct.contains("json", ignoreCase = true) ||
                    (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte())
                ) {
                    val s = bytes.toString(Charsets.UTF_8)
                    return try {
                        val root = JSONObject(s)
                        when (root.optString("status", "")) {
                            "failure" -> {
                                val err = root.optString("error")
                                    .ifBlank { root.optString("message") }
                                    .ifBlank { "Request failed" }
                                RedactedResult.Failure(err, code, retryAfter)
                            }
                            "success" -> RedactedResult.Success(root)
                            else -> RedactedResult.Failure(s.take(300), code, retryAfter)
                        }
                    } catch (e: Exception) {
                        RedactedResult.Failure("Invalid JSON: ${e.message}", code, retryAfter)
                    }
                }
                if (!resp.isSuccessful) {
                    return RedactedResult.Failure("HTTP $code", code, retryAfter)
                }
                RedactedResult.Binary(bytes, ct)
            }
        } catch (e: Exception) {
            RedactedResult.Failure(e.message ?: "Network error", 0, null)
        }
    }

    // --- No scope / mixed ---

    fun index(): RedactedResult = executeJson(authorized(buildUrl("index")).get().build())

    fun communityStats(userId: Int): RedactedResult =
        executeJson(authorized(buildUrl("community_stats", listOf("userid" to userId.toString()))).get().build())

    fun browse(params: List<Pair<String, String?>>): RedactedResult =
        executeJson(authorized(buildUrl("browse", params)).get().build())

    fun logcheckerPaste(pasteLog: String): RedactedResult {
        val url = buildUrl("logchecker")
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("pastelog", pasteLog)
            .build()
        val req = authorized(url).post(body).build()
        return executeJson(req)
    }

    fun logcheckerFile(logFile: File): RedactedResult {
        val url = buildUrl("logchecker")
        val part = logFile.asRequestBody(OCTET_STREAM)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("log", logFile.name, part)
            .build()
        val req = authorized(url).post(body).build()
        return executeJson(req)
    }

    fun similarArtists(artistId: Int, limit: Int? = null): RedactedResult {
        val p = buildList {
            add("id" to artistId.toString())
            if (limit != null) add("limit" to limit.toString())
        }
        return executeJson(authorized(buildUrl("similar_artists", p)).get().build())
    }

    fun announcements(
        page: Int? = null,
        perPage: Int? = null,
        orderWay: String? = null,
        orderBy: String? = null,
    ): RedactedResult {
        val p = buildList {
            if (page != null) add("page" to page.toString())
            if (perPage != null) add("perpage" to perPage.toString())
            if (!orderWay.isNullOrBlank()) add("order_way" to orderWay)
            if (!orderBy.isNullOrBlank()) add("order_by" to orderBy)
        }
        return executeJson(authorized(buildUrl("announcements", p)).get().build())
    }

    // --- User scope ---

    fun user(userId: Int): RedactedResult =
        executeJson(authorized(buildUrl("user", listOf("id" to userId.toString()))).get().build())

    fun inbox(
        page: Int? = null,
        type: String? = null,
        sort: String? = null,
        search: String? = null,
        searchType: String? = null,
    ): RedactedResult {
        val p = buildList {
            if (page != null) add("page" to page.toString())
            if (!type.isNullOrBlank()) add("type" to type)
            if (!sort.isNullOrBlank()) add("sort" to sort)
            if (!search.isNullOrBlank()) add("search" to search)
            if (!searchType.isNullOrBlank()) add("searchtype" to searchType)
        }
        return executeJson(authorized(buildUrl("inbox", p)).get().build())
    }

    fun inboxConversation(convId: Int): RedactedResult =
        executeJson(
            authorized(
                buildUrl(
                    "inbox",
                    listOf("type" to "viewconv", "id" to convId.toString())
                )
            ).get().build()
        )

    fun sendPm(toUserId: Int, subject: String?, body: String, convId: Int? = null): RedactedResult {
        val url = buildUrl("send_pm")
        val form = FormBody.Builder().apply {
            add("toid", toUserId.toString())
            add("body", body)
            convId?.let { add("convid", it.toString()) }
            if (!subject.isNullOrBlank()) add("subject", subject)
        }.build()
        val req = authorized(url).post(form).build()
        return executeJson(req)
    }

    fun userSearch(search: String, page: Int? = null): RedactedResult {
        val p = buildList {
            add("search" to search)
            if (page != null) add("page" to page.toString())
        }
        return executeJson(authorized(buildUrl("usersearch", p)).get().build())
    }

    fun bookmarks(type: String): RedactedResult =
        executeJson(authorized(buildUrl("bookmarks", listOf("type" to type))).get().build())

    fun subscriptions(showUnreadOnly: Boolean = true): RedactedResult {
        val p = listOf("showunread" to if (showUnreadOnly) "1" else "0")
        return executeJson(authorized(buildUrl("subscriptions", p)).get().build())
    }

    fun userTorrents(
        userId: Int,
        type: String,
        limit: Int? = null,
        offset: Int? = null,
    ): RedactedResult {
        val p = buildList {
            add("id" to userId.toString())
            add("type" to type)
            if (limit != null) add("limit" to limit.toString())
            if (offset != null) add("offset" to offset.toString())
        }
        return executeJson(authorized(buildUrl("user_torrents", p)).get().build())
    }

    fun notifications(page: Int? = null): RedactedResult {
        val p = if (page != null) listOf("page" to page.toString()) else emptyList()
        return executeJson(authorized(buildUrl("notifications", p)).get().build())
    }

    // --- Torrent scope ---

    fun top10(type: String? = null, limit: Int? = null): RedactedResult {
        val p = buildList {
            if (!type.isNullOrBlank()) add("type" to type)
            if (limit != null) add("limit" to limit.toString())
        }
        return executeJson(authorized(buildUrl("top10", p)).get().build())
    }

    fun artist(artistId: Int): RedactedResult =
        executeJson(authorized(buildUrl("artist", listOf("id" to artistId.toString()))).get().build())

    fun torrentGroup(groupId: Int, hash: String? = null): RedactedResult {
        val p = buildList {
            add("id" to groupId.toString())
            if (!hash.isNullOrBlank()) add("hash" to hash)
        }
        return executeJson(authorized(buildUrl("torrentgroup", p)).get().build())
    }

    fun torrent(torrentId: Int, hash: String? = null): RedactedResult {
        val p = buildList {
            add("id" to torrentId.toString())
            if (!hash.isNullOrBlank()) add("hash" to hash)
        }
        return executeJson(authorized(buildUrl("torrent", p)).get().build())
    }

    fun ripLog(torrentId: Int, logId: Int): RedactedResult =
        executeJson(
            authorized(
                buildUrl(
                    "riplog",
                    listOf("id" to torrentId.toString(), "logid" to logId.toString())
                )
            ).get().build()
        )

    fun downloadTorrent(torrentId: Int, useToken: Boolean = false): RedactedResult {
        val url = buildUrl(
            "download",
            listOf(
                "id" to torrentId.toString(),
                "usetoken" to if (useToken) "1" else "0",
            )
        )
        val req = authorized(url).get().build()
        return executeBinary(req)
    }

    /** API key only. Multipart body must include action-specific parts per wiki. */
    fun postUpload(multipart: MultipartBody): RedactedResult {
        val url = buildUrl("upload")
        val req = authorized(url).post(multipart).build()
        return executeJson(req)
    }

    fun groupEditGet(groupId: Int): RedactedResult =
        executeJson(authorized(buildUrl("groupedit", listOf("id" to groupId.toString()))).get().build())

    fun groupEditPost(
        groupId: Int,
        summary: String,
        body: String? = null,
        image: String? = null,
        releaseType: Int? = null,
        groupEditNotes: String? = null,
    ): RedactedResult {
        val url = buildUrl("groupedit", listOf("id" to groupId.toString()))
        val form = FormBody.Builder().apply {
            add("summary", summary)
            body?.let { add("body", it) }
            image?.let { add("image", it) }
            releaseType?.let { add("releasetype", it.toString()) }
            groupEditNotes?.let { add("groupeditnotes", it) }
        }.build()
        return executeJson(authorized(url).post(form).build())
    }

    fun addTag(groupId: Int, tagNamesCsv: String): RedactedResult {
        val url = buildUrl("addtag")
        val form = FormBody.Builder()
            .add("groupid", groupId.toString())
            .add("tagname", tagNamesCsv)
            .build()
        return executeJson(authorized(url).post(form).build())
    }

    fun torrentEditGet(torrentId: Int): RedactedResult =
        executeJson(authorized(buildUrl("torrentedit", listOf("id" to torrentId.toString()))).get().build())

    fun torrentEditPost(torrentId: Int, fields: List<Pair<String, String>>): RedactedResult {
        val url = buildUrl("torrentedit", listOf("id" to torrentId.toString()))
        val form = FormBody.Builder().apply {
            for ((k, v) in fields) add(k, v)
        }.build()
        return executeJson(authorized(url).post(form).build())
    }

    fun collage(collageId: Int, showOnlyGroups: Boolean = false): RedactedResult {
        val p = buildList {
            add("id" to collageId.toString())
            if (showOnlyGroups) add("showonlygroups" to "1")
        }
        return executeJson(authorized(buildUrl("collage", p)).get().build())
    }

    fun addToCollage(collageId: Int, groupIdsCsv: String): RedactedResult {
        val url = buildUrl("addtocollage", listOf("collageid" to collageId.toString()))
        val form = FormBody.Builder()
            .add("groupids", groupIdsCsv)
            .build()
        return executeJson(authorized(url).post(form).build())
    }

    // --- Request scope ---

    fun requests(
        search: String? = null,
        page: Int? = null,
        tags: String? = null,
        tagsType: Int? = null,
        showFilled: Boolean? = null,
        extra: List<Pair<String, String?>> = emptyList(),
    ): RedactedResult {
        val p = buildList {
            if (!search.isNullOrBlank()) add("search" to search)
            if (page != null) add("page" to page.toString())
            if (!tags.isNullOrBlank()) add("tags" to tags)
            if (tagsType != null) add("tags_type" to tagsType.toString())
            if (showFilled != null) add("show_filled" to if (showFilled) "true" else "false")
            addAll(extra)
        }
        return executeJson(authorized(buildUrl("requests", p)).get().build())
    }

    fun request(requestId: Int, page: Int? = null): RedactedResult {
        val p = buildList {
            add("id" to requestId.toString())
            if (page != null) add("page" to page.toString())
        }
        return executeJson(authorized(buildUrl("request", p)).get().build())
    }

    fun requestFill(requestId: Int, torrentId: Int? = null, link: String? = null): RedactedResult {
        val url = buildUrl("requestfill")
        val form = FormBody.Builder().apply {
            add("requestid", requestId.toString())
            torrentId?.let { add("torrentid", it.toString()) }
            link?.let { add("link", it) }
        }.build()
        return executeJson(authorized(url).post(form).build())
    }

    // --- Forum scope ---

    fun forumMain(): RedactedResult =
        executeJson(authorized(buildUrl("forum", listOf("type" to "main"))).get().build())

    fun forumViewForum(forumId: Int, page: Int? = null): RedactedResult {
        val p = buildList {
            add("type" to "viewforum")
            add("forumid" to forumId.toString())
            if (page != null) add("page" to page.toString())
        }
        return executeJson(authorized(buildUrl("forum", p)).get().build())
    }

    /**
     * @param skipUpdateLastRead when true, sends updatelastread=1 (do not update last read).
     */
    fun forumThread(
        threadId: Int,
        postId: Int? = null,
        page: Int? = null,
        skipUpdateLastRead: Boolean = false,
    ): RedactedResult {
        val p = buildList {
            add("type" to "viewthread")
            add("threadid" to threadId.toString())
            if (postId != null) add("postid" to postId.toString())
            if (page != null) add("page" to page.toString())
            if (skipUpdateLastRead) add("updatelastread" to "1")
        }
        return executeJson(authorized(buildUrl("forum", p)).get().build())
    }

    // --- Wiki scope ---

    fun wiki(id: Int? = null, name: String? = null): RedactedResult {
        val p = buildList {
            if (id != null) add("id" to id.toString())
            if (!name.isNullOrBlank()) add("name" to name)
        }
        return executeJson(authorized(buildUrl("wiki", p)).get().build())
    }
}

sealed class RedactedResult {
    data class Success(val root: JSONObject) : RedactedResult() {
        val response: JSONObject?
            get() = root.optJSONObject("response")
        val responseArray
            get() = root.optJSONArray("response")
        val responseString: String?
            get() = when {
                root.has("response") && root.get("response") is String ->
                    root.getString("response")
                else -> null
            }
    }

    data class Binary(val bytes: ByteArray, val contentType: String?) : RedactedResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Binary
            if (!bytes.contentEquals(other.bytes)) return false
            if (contentType != other.contentType) return false
            return true
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + (contentType?.hashCode() ?: 0)
            return result
        }
    }

    data class Failure(val message: String, val httpCode: Int, val retryAfterSeconds: Int?) : RedactedResult()
}

fun RedactedResult.responseOrNull(): JSONObject? = (this as? RedactedResult.Success)?.response
