package com.turntable.barcodescanner.redacted

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.AppRichText
import com.turntable.barcodescanner.R
import com.turntable.barcodescanner.SearchPrefs
import org.json.JSONObject

data class ConversationMessageRow(
    val senderName: String,
    val sentDate: String,
    val body: String,
    val imageUrls: List<String> = emptyList(),
    /** Alternates when [senderKey] changes from the previous message. */
    val useAltStripe: Boolean,
)

class ConversationMessagesAdapter : RecyclerView.Adapter<ConversationMessagesAdapter.VH>() {

    var rows: List<ConversationMessageRow> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation_message, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(rows[position])
    }

    override fun getItemCount(): Int = rows.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val meta: TextView = itemView.findViewById(R.id.textMeta)
        private val body: TextView = itemView.findViewById(R.id.textBody)
        private val images: LinearLayout = itemView.findViewById(R.id.layoutImages)

        fun bind(row: ConversationMessageRow) {
            meta.text = itemView.context.getString(
                R.string.redacted_conversation_meta_fmt,
                row.senderName,
                row.sentDate,
            )
            AppRichText.applyTo(body, row.body)
            bindImages(row.imageUrls)
            val bg = if (row.useAltStripe) {
                R.color.conv_message_stripe_b
            } else {
                R.color.conv_message_stripe_a
            }
            itemView.setBackgroundColor(ContextCompat.getColor(itemView.context, bg))
        }

        private fun bindImages(urls: List<String>) {
            images.removeAllViews()
            if (urls.isEmpty()) {
                images.visibility = View.GONE
                return
            }
            images.visibility = View.VISIBLE
            val context = itemView.context
            val apiKey = SearchPrefs(context).redactedApiKey?.trim().orEmpty()
            urls.take(4).forEach { url ->
                val iv = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).also { lp -> lp.topMargin = 6 }
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "Forum image"
                    tag = url
                }
                images.addView(iv)
                Thread {
                    val bmp = RedactedAvatarLoader.loadBitmapCached(
                        context = context,
                        rawUrl = url,
                        apiKey = apiKey,
                        maxSidePx = 1400,
                    )
                    iv.post {
                        if (iv.tag != url) return@post
                        if (bmp != null) {
                            iv.setImageBitmap(bmp)
                            iv.visibility = View.VISIBLE
                        } else {
                            iv.visibility = View.GONE
                        }
                    }
                }.start()
            }
        }
    }
}

/**
 * Parses [response] from `inbox` `viewconv` into rows with alternating background stripes
 * whenever the sender changes (by [senderId], or name if id is 0).
 */
fun parseConversationMessageRows(response: JSONObject?): Pair<String, List<ConversationMessageRow>> {
    if (response == null) return "" to emptyList()
    val subject = response.optString("subject")
    val arr = response.optJSONArray("messages") ?: return subject to emptyList()
    var lastKey: String? = null
    var useAlt = false
    val out = mutableListOf<ConversationMessageRow>()
    for (i in 0 until arr.length()) {
        val m = arr.optJSONObject(i) ?: continue
        val senderId = m.optInt("senderId")
        val senderName = m.optString("senderName")
        val key = if (senderId != 0) "id:$senderId" else "name:${senderName.lowercase()}"
        when {
            lastKey == null -> lastKey = key
            key != lastKey -> {
                lastKey = key
                useAlt = !useAlt
            }
        }
        out.add(
            ConversationMessageRow(
                senderName = senderName,
                sentDate = m.optString("sentDate"),
                body = m.optString("bbBody"),
                imageUrls = emptyList(),
                useAltStripe = useAlt,
            ),
        )
    }
    return subject to out
}

/**
 * Parses [response] from `forum` `viewthread` into rows with alternating stripes when the author changes.
 */
fun parseForumThreadRows(response: JSONObject?): Pair<String, List<ConversationMessageRow>> {
    if (response == null) return "" to emptyList()
    val title = response.optString("threadTitle")
    val posts = response.optJSONArray("posts") ?: return title to emptyList()
    var lastKey: String? = null
    var useAlt = false
    val out = mutableListOf<ConversationMessageRow>()
    for (i in 0 until posts.length()) {
        val p = posts.optJSONObject(i) ?: continue
        val auth = p.optJSONObject("author")
        val authorId = auth?.optInt("authorId") ?: 0
        val authorName = auth?.optString("authorName").orEmpty()
        val key = if (authorId != 0) "id:$authorId" else "name:${authorName.lowercase()}"
        when {
            lastKey == null -> lastKey = key
            key != lastKey -> {
                lastKey = key
                useAlt = !useAlt
            }
        }
        out.add(
            ConversationMessageRow(
                senderName = authorName,
                sentDate = p.optString("addedTime"),
                body = stripBbImgTags(p.optString("bbBody")),
                imageUrls = extractBbImageUrls(p.optString("bbBody")),
                useAltStripe = useAlt,
            ),
        )
    }
    return title to out
}

private fun stripBbImgTags(text: String): String =
    text
        .replace(
            Regex("""\[img=([^\]]+)]\s*(.+?)\s*\[/img]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            "",
        )
        .replace(Regex("""\[img=([^\]]+)]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\[img].*?\[/img]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .trim()

private fun absolutizeBbImgSrcForExtract(raw: String): String {
    val t = raw.trim()
    return when {
        t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) -> t
        t.startsWith("//") -> "https:$t"
        else -> "https://redacted.sh/${t.trimStart('/')}"
    }
}

private fun extractBbImageUrls(text: String): List<String> {
    val out = LinkedHashSet<String>()
    Regex("""\[img=([^\]]+)]\s*(.+?)\s*\[/img]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(text)
        .forEach { m ->
            val arg = m.groupValues[1].trim()
            val body = m.groupValues[2].trim()
            val isDim = Regex("""^\d+\s*x\s*\d+$""", RegexOption.IGNORE_CASE).matches(arg)
            val argIsUrl = arg.startsWith("http://", ignoreCase = true) ||
                arg.startsWith("https://", ignoreCase = true) ||
                arg.startsWith("//", ignoreCase = true)
            val srcRaw = when {
                isDim -> body
                argIsUrl -> arg
                else -> body.ifBlank { arg }
            }
            if (srcRaw.isNotBlank()) out.add(absolutizeBbImgSrcForExtract(srcRaw))
        }
    Regex("""\[img](.*?)\[/img]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(text)
        .mapNotNull { it.groupValues.getOrNull(1)?.trim()?.takeIf { s -> s.isNotBlank() } }
        .forEach { out.add(absolutizeBbImgSrcForExtract(it)) }
    Regex("""\[img=([^\]]+)]""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .forEach { m ->
            val raw = m.groupValues[1].trim()
            if (!Regex("""^\d+\s*x\s*\d+$""", RegexOption.IGNORE_CASE).matches(raw)) {
                out.add(absolutizeBbImgSrcForExtract(raw))
            }
        }
    return out.toList()
}

/**
 * Resolves the other participant’s user id for [RedactedApiClient.sendPm] when replying in-thread (`convid`).
 * Tries common JSON fields, then infers from [currentUserId] and per-message [senderId] values.
 */
fun resolveConversationReplyRecipientId(resp: JSONObject?, currentUserId: Int): Int {
    if (resp == null) return 0
    fun tryDirect(): Int {
        for (key in listOf("recipientId", "recipientUserId", "toUserId", "userId")) {
            val v = resp.optInt(key, 0)
            if (v > 0 && (currentUserId <= 0 || v != currentUserId)) return v
        }
        resp.optJSONObject("recipient")?.optInt("id", 0)?.takeIf { it > 0 }?.let { return it }
        resp.optJSONObject("otherUser")?.optInt("id", 0)?.takeIf { it > 0 }?.let { return it }
        resp.optJSONObject("with")?.optInt("id", 0)?.takeIf { it > 0 }?.let { return it }
        return 0
    }
    val direct = tryDirect()
    if (direct > 0) return direct

    val arr = resp.optJSONArray("messages") ?: return 0
    val senderIds = LinkedHashSet<Int>()
    for (i in 0 until arr.length()) {
        val sid = arr.optJSONObject(i)?.optInt("senderId") ?: 0
        if (sid > 0) senderIds.add(sid)
    }
    if (currentUserId > 0) {
        senderIds.remove(currentUserId)
        if (senderIds.size == 1) return senderIds.first()
        // Last message from someone else (multi-party / edge cases)
        for (i in arr.length() - 1 downTo 0) {
            val sid = arr.optJSONObject(i)?.optInt("senderId") ?: 0
            if (sid > 0 && sid != currentUserId) return sid
        }
    } else {
        if (senderIds.size == 1) return senderIds.first()
    }
    return senderIds.firstOrNull() ?: 0
}
