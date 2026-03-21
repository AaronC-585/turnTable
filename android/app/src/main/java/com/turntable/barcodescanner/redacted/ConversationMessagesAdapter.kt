package com.turntable.barcodescanner.redacted

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.R
import org.json.JSONObject

data class ConversationMessageRow(
    val senderName: String,
    val sentDate: String,
    val body: String,
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

        fun bind(row: ConversationMessageRow) {
            meta.text = itemView.context.getString(
                R.string.redacted_conversation_meta_fmt,
                row.senderName,
                row.sentDate,
            )
            body.text = row.body
            val bg = if (row.useAltStripe) {
                R.color.conv_message_stripe_b
            } else {
                R.color.conv_message_stripe_a
            }
            itemView.setBackgroundColor(ContextCompat.getColor(itemView.context, bg))
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
                useAltStripe = useAlt,
            ),
        )
    }
    return subject to out
}
