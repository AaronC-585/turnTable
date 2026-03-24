package com.turntable.barcodescanner.redacted

import com.turntable.barcodescanner.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Builds structured profile sections for the Home screen from Redacted JSON API payloads
 * ([index], [user], [community_stats]) — aligned with Gazelle-style `user` responses.
 */
object RedactedProfileUiBuilder {

    data class ProfileRow(
        val label: String,
        val value: String,
        val valueColorRes: Int? = null,
        val labelColorRes: Int? = null,
        val valueBold: Boolean = false,
        /** Shown below the row (e.g. merit token expiry). */
        val footer: String? = null,
        val footerBold: Boolean = false,
    )

    data class ProfileUploadRow(
        val torrentId: Int,
        val title: String,
        val subtitle: String,
    )

    data class ProfileSection(
        val titleRes: Int,
        val rows: List<ProfileRow>,
        /** Optional format arg for [R.string.home_section_dynamic_title]. */
        val titleArg: String? = null,
        /** Collapsible list: double-tap opens [com.turntable.barcodescanner.RedactedTorrentDetailActivity]. */
        val uploadRows: List<ProfileUploadRow>? = null,
    )

    /**
     * Parses `user_torrents` JSON ([RedactedApiClient.userTorrents]) for `type=uploaded` — array key `uploaded`.
     */
    fun parseUploadedTorrents(response: JSONObject?): List<ProfileUploadRow> {
        if (response == null) return emptyList()
        val arr = response.optJSONArray("uploaded") ?: JSONArray()
        val out = mutableListOf<ProfileUploadRow>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val tid = o.optInt("id", o.optInt("torrentId", 0))
            if (tid <= 0) continue
            val name = o.optString("name").ifBlank { o.optString("groupName") }
            val artist = o.optString("artistName").ifBlank { o.optString("artist") }
            val fmt = o.optString("format").ifBlank { o.optString("media") }
            val subtitle = buildString {
                if (artist.isNotBlank()) append(artist)
                if (fmt.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(fmt)
                }
            }
            out.add(
                ProfileUploadRow(
                    torrentId = tid,
                    title = name.ifBlank { "Torrent $tid" },
                    subtitle = subtitle,
                ),
            )
        }
        return out
    }

    fun build(
        index: JSONObject?,
        user: JSONObject?,
        communityStats: JSONObject?,
    ): List<ProfileSection> {
        val out = mutableListOf<ProfileSection>()
        val idxStats = index?.optJSONObject("userstats")
        if (user == null) {
            return if (index != null) listOf(fallbackFromIndexOnly(index)) else emptyList()
        }
        val u = user

        val stats = u.optJSONObject("stats")
        buildStatisticsSection(stats, idxStats, u)?.let { out.add(it) }

        u.optJSONObject("personal")?.let { p ->
            buildPersonalSection(p, u)?.let { out.add(it) }
        }

        val comm = u.optJSONObject("community")
        val commExtra = communityStats?.let { extractCommunityPayload(it) }
        buildCommunitySection(comm, commExtra)?.let { out.add(it) }

        u.optJSONObject("nextUserclass")
            ?: u.optJSONObject("nextClass")
            ?: u.optJSONObject("next_userclass")
            ?.let { buildGenericSection(R.string.home_section_next_class, it) }
            ?.let { out.add(it) }

        u.optJSONObject("ranks")?.let { r ->
            buildRanksSection(r)?.let { out.add(it) }
        }

        buildDonorSection(u)?.let { out.add(it) }

        // Index `notifications` is not shown on Home (OS alerts handled in HomeActivity).

        // Remaining top-level objects on user (site-specific extensions)
        val consumed = setOf(
            "stats", "personal", "community", "ranks",
            "nextUserclass", "nextClass", "next_userclass",
        )
        val extraKeys = user.keys().asSequence()
            .filter { it !in consumed }
            .filter { user.get(it) is JSONObject }
            .sorted()
            .toList()
        for (key in extraKeys) {
            val o = user.optJSONObject(key) ?: continue
            if (o.length() == 0) continue
            val title = humanizeKey(key)
            val rows = flattenObject(o, maxDepth = 3)
            if (rows.isNotEmpty()) {
                out.add(ProfileSection(R.string.home_section_dynamic_title, rows, title))
            }
        }

        return out.filter { it.rows.isNotEmpty() }
    }

    private fun fallbackFromIndexOnly(index: JSONObject?): ProfileSection {
        val rows = mutableListOf<ProfileRow>()
        index?.optJSONObject("userstats")?.let { flattenObject(it, 1) }?.let { rows.addAll(it) }
        if (rows.isEmpty()) {
            rows.add(ProfileRow("", "—"))
        }
        return ProfileSection(R.string.home_section_statistics, rows)
    }

    private fun extractCommunityPayload(root: JSONObject): JSONObject? {
        val r = root.optJSONObject("response") ?: root
        return if (r.length() > 0) r else null
    }

    private fun buildStatisticsSection(
        stats: JSONObject?,
        idxStats: JSONObject?,
        userTop: JSONObject,
    ): ProfileSection? {
        val rows = mutableListOf<ProfileRow>()

        stats?.optString("joinedDate")?.takeIf { it.isNotBlank() }?.let {
            rows.add(ProfileRow("Joined", formatJoinedDate(it)))
        }
        stats?.optString("lastAccess")?.takeIf { it.isNotBlank() }?.let {
            rows.add(ProfileRow("Last seen", formatLastSeen(it)))
        }

        val up = firstLong(stats, idxStats, "uploaded")
        val down = firstLong(stats, idxStats, "downloaded")
        if (up != null) rows.add(ProfileRow("Uploaded", RedactedFormat.formatBytes(up)))
        if (down != null) rows.add(ProfileRow("Downloaded", RedactedFormat.formatBytes(down)))

        val buffer = firstLong(stats, idxStats, "buffer")
        if (buffer != null) rows.add(ProfileRow("Buffer", RedactedFormat.formatBytes(buffer)))

        val ratio = firstDouble(stats, idxStats, "ratio")
        val req = firstDouble(stats, idxStats, "requiredRatio", "requiredratio")
        val ratioColor = when {
            ratio == null -> null
            req == null -> R.color.home_ratio_warn
            ratio >= req -> R.color.home_ratio_ok
            else -> R.color.home_ratio_warn
        }
        if (ratio != null) {
            rows.add(
                ProfileRow(
                    "Ratio",
                    RedactedFormat.formatRatio(ratio),
                    valueColorRes = ratioColor,
                    valueBold = true,
                ),
            )
        }
        if (req != null) {
            rows.add(ProfileRow("Required ratio", RedactedFormat.formatRatio(req)))
        }

        if (idxStats != null && idxStats.has("bonusPoints")) {
            rows.add(
                ProfileRow(
                    "Bonus points",
                    idxStats.optLong("bonusPoints").toString(),
                    labelColorRes = R.color.home_token_label,
                ),
            )
        }
        idxStats?.optDouble("bonusPointsPerHour", Double.NaN)?.takeIf { !it.isNaN() }?.let {
            rows.add(ProfileRow("Bonus points / hour", RedactedFormat.formatRatio(it)))
        }

        // Merit / tokens (site-specific keys)
        listOf(
            "meritPoints" to "Merit points",
            "merit" to "Merit",
            "tokens" to "Tokens",
            "tokenCount" to "Tokens",
        ).forEach { (k, label) ->
            val v = stats?.opt(k) ?: userTop.opt(k) ?: idxStats?.opt(k)
            when (v) {
                is Number -> if (v.toDouble() != 0.0 || label == "Tokens") {
                    rows.add(ProfileRow(label, v.toString(), labelColorRes = R.color.home_token_label))
                }
                is String -> if (v.isNotBlank()) {
                    rows.add(ProfileRow(label, v, labelColorRes = R.color.home_token_label))
                }
            }
        }

        stats?.optString("meritExpiry")?.takeIf { it.isNotBlank() }
            ?: userTop.optString("meritExpiry").takeIf { it.isNotBlank() }
            ?: stats?.optString("tokenExpiry")?.takeIf { it.isNotBlank() }
            ?.let { exp ->
                rows.add(
                    ProfileRow(
                        "",
                        "",
                        footer = "Merit Tokens expire in: $exp",
                        footerBold = true,
                    ),
                )
            }

        idxStats?.optString("class")?.takeIf { it.isNotBlank() }?.let {
            rows.add(ProfileRow("Class", it))
        }

        if (rows.isEmpty() && idxStats != null) {
            rows.addAll(flattenObject(idxStats, 1))
        }

        if (rows.isEmpty()) return null
        return ProfileSection(R.string.home_section_statistics, rows)
    }

    private fun formatLastSeen(raw: String): String {
        val lower = raw.lowercase(Locale.US)
        return when {
            "just now" in lower || raw.isBlank() -> "Just now"
            else -> raw
        }
    }

    private fun buildPersonalSection(p: JSONObject, userTop: JSONObject): ProfileSection? {
        val rows = mutableListOf<ProfileRow>()
        p.optString("class").takeIf { it.isNotBlank() }?.let {
            rows.add(ProfileRow("Class", it))
        }
        val paranoiaText = p.optString("paranoiaText").takeIf { it.isNotBlank() }
        if (paranoiaText != null) {
            rows.add(ProfileRow("Paranoia level", paranoiaText))
        } else if (p.has("paranoia")) {
            rows.add(ProfileRow("Paranoia level", p.opt("paranoia")?.toString() ?: "—"))
        }
        userTop.optString("email").takeIf { it.isNotBlank() }?.let {
            rows.add(ProfileRow("Email", it))
        }
        if (p.has("passkey") || userTop.has("passkey")) {
            val pk = p.optString("passkey").ifBlank { userTop.optString("passkey") }
            rows.add(ProfileRow("Passkey", if (pk.isNotBlank()) "[configured]" else "—"))
        }
        userTop.optString("clients").takeIf { it.isNotBlank() }
            ?: p.optString("clients").takeIf { it.isNotBlank() }
            ?.let { rows.add(ProfileRow("Clients", it)) }

        val pwdSec = p.optLong("passwordAgeSeconds", -1L).takeIf { it >= 0 }
            ?: p.optLong("password_age_seconds", -1L).takeIf { it >= 0 }
        pwdSec?.let {
            rows.add(ProfileRow("Password age", RedactedFormat.formatDurationSeconds(it)))
        }

        p.keys().forEach { k ->
            if (k in setOf("class", "paranoia", "paranoiaText", "donor", "warned", "enabled", "passkey")) return@forEach
            val v = p.opt(k) ?: return@forEach
            if (v is JSONObject || v is JSONArray) return@forEach
            val label = humanizeKey(k)
            if (rows.none { it.label == label }) {
                rows.add(ProfileRow(label, formatLeafForJsonKey(k, v)))
            }
        }

        if (rows.isEmpty()) return null
        return ProfileSection(R.string.home_section_personal, rows)
    }

    private fun buildCommunitySection(
        comm: JSONObject?,
        commExtra: JSONObject?,
    ): ProfileSection? {
        val merged = JSONObject()
        comm?.let { mergeJson(merged, it) }
        commExtra?.let { mergeJson(merged, it) }
        if (merged.length() == 0) return null
        val rows = flattenObject(merged, maxDepth = 2)
        if (rows.isEmpty()) return null
        return ProfileSection(R.string.home_section_community, rows)
    }

    private fun mergeJson(target: JSONObject, source: JSONObject) {
        val keys = source.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            target.put(k, source.get(k))
        }
    }

    private fun buildRanksSection(r: JSONObject): ProfileSection? {
        val rows = mutableListOf<ProfileRow>()
        val order = listOf(
            "uploaded" to "Data uploaded",
            "downloaded" to "Data downloaded",
            "uploads" to "Torrents uploaded",
            "requests" to "Requests filled",
            // Gazelle: percentile rank (0–100). Community stats use separate keys for byte amounts.
            "bounty" to "Bounty rank",
            "bountyEarned" to "Bounty earned",
            "bountySpent" to "Bounty spent",
            "bounty_earned" to "Bounty earned",
            "bounty_spent" to "Bounty spent",
            "posts" to "Posts made",
            "artists" to "Artists added",
            "overall" to "Overall rank",
        )
        for ((key, label) in order) {
            if (!r.has(key)) continue
            val v = r.opt(key)
            val isOverall = key == "overall"
            rows.add(
                ProfileRow(
                    label,
                    formatLeafForJsonKey(key, v),
                    valueBold = isOverall,
                    valueColorRes = if (isOverall) R.color.home_rank_highlight else null,
                ),
            )
        }
        r.keys().asSequence().sorted().forEach { k ->
            if (order.any { it.first == k }) return@forEach
            rows.add(ProfileRow(humanizeKey(k), formatLeafForJsonKey(k, r.opt(k))))
        }
        if (rows.isEmpty()) return null
        return ProfileSection(R.string.home_section_percentile_rankings, rows)
    }

    private fun buildDonorSection(user: JSONObject): ProfileSection? {
        val personal = user.optJSONObject("personal") ?: return null
        val donor = personal.opt("donor") ?: user.opt("donor")
        val rows = mutableListOf<ProfileRow>()
        when (donor) {
            is Boolean -> rows.add(
                ProfileRow(
                    "Donor",
                    if (donor) "Yes" else "No (not donated)",
                ),
            )
            is String -> if (donor.isNotBlank()) rows.add(ProfileRow("Donor", donor))
        }
        user.optJSONObject("donorStats")?.let { d ->
            rows.addAll(flattenObject(d, 2))
        }
        if (rows.isEmpty()) return null
        return ProfileSection(R.string.home_section_donor, rows)
    }

    private fun buildGenericSection(titleRes: Int, o: JSONObject): ProfileSection {
        return ProfileSection(titleRes, flattenObject(o, maxDepth = 4))
    }

    private fun flattenObject(o: JSONObject, maxDepth: Int): List<ProfileRow> {
        val rows = mutableListOf<ProfileRow>()
        val keys = o.keys().asSequence().sorted().toList()
        for (k in keys) {
            val v = o.opt(k) ?: continue
            when {
                maxDepth > 0 && v is JSONObject -> {
                    val inner = flattenObject(v, maxDepth - 1)
                    inner.forEach { innerRow ->
                        rows.add(
                            ProfileRow(
                                "${humanizeKey(k)} › ${innerRow.label}",
                                innerRow.value,
                                valueColorRes = innerRow.valueColorRes,
                            ),
                        )
                    }
                }
                v is JSONArray -> rows.add(ProfileRow(humanizeKey(k), v.toString()))
                else -> rows.add(ProfileRow(humanizeKey(k), formatLeafForJsonKey(k, v)))
            }
        }
        return rows
    }

    private fun humanizeKey(key: String): String {
        return key
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .split(' ')
            .joinToString(" ") { w ->
                w.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString() }
            }
    }

    private fun formatLeaf(v: Any?): String = when (v) {
        null -> "—"
        is Number -> {
            val d = v.toDouble()
            if (d == d.roundToInt().toDouble()) v.toLong().toString() else v.toString()
        }
        is Boolean -> if (v) "Yes" else "No"
        else -> v.toString()
    }

    /**
     * Request bounty totals from the API are byte amounts; [ranks] `bounty` is usually a small percentile (0–100).
     * Format large bounty-related integers as binary sizes; leave small values as plain numbers.
     */
    private fun formatLeafForJsonKey(jsonKey: String, v: Any?): String {
        if (!jsonKey.contains("bounty", ignoreCase = true)) return formatLeaf(v)
        val asLong = when (v) {
            is Long -> v
            is Int -> v.toLong()
            is Double ->
                if (v.isNaN() || v.isInfinite()) return formatLeaf(v)
                else {
                    val l = v.toLong()
                    if (v != l.toDouble()) return formatLeaf(v)
                    l
                }
            is Number -> {
                val d = v.toDouble()
                if (d.isNaN() || d.isInfinite()) return formatLeaf(v)
                val l = d.toLong()
                if (d != l.toDouble()) return formatLeaf(v)
                l
            }
            else -> return formatLeaf(v)
        }
        return if (kotlin.math.abs(asLong) >= 1024) {
            RedactedFormat.formatBytes(asLong)
        } else {
            formatLeaf(v)
        }
    }

    private fun firstLong(
        stats: JSONObject?,
        idx: JSONObject?,
        vararg keys: String,
    ): Long? {
        for (k in keys) {
            if (stats != null && stats.has(k)) return stats.optLong(k)
            if (idx != null && idx.has(k)) return idx.optLong(k)
        }
        return null
    }

    private fun firstDouble(
        stats: JSONObject?,
        idx: JSONObject?,
        vararg keys: String,
    ): Double? {
        for (k in keys) {
            if (stats != null && stats.has(k)) {
                val d = stats.optDouble(k, Double.NaN)
                if (!d.isNaN()) return d
            }
            if (idx != null && idx.has(k)) {
                val d = idx.optDouble(k, Double.NaN)
                if (!d.isNaN()) return d
            }
        }
        return null
    }

    private fun formatJoinedDate(raw: String): String = raw.trim()
}
