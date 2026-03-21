package com.turntable.barcodescanner.redacted

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.R

data class AnnouncementRow(
    val title: String,
    val time: String,
    /** HTML for [HtmlCompat.fromHtml]. */
    val htmlContent: String,
    val useAltStripe: Boolean,
)

class AnnouncementsAdapter : RecyclerView.Adapter<AnnouncementsAdapter.VH>() {

    var rows: List<AnnouncementRow> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_announcement_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(rows[position])
    }

    override fun getItemCount(): Int = rows.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.textAnnouncementTitle)
        private val time: TextView = itemView.findViewById(R.id.textAnnouncementTime)
        private val body: TextView = itemView.findViewById(R.id.textAnnouncementBody)

        fun bind(row: AnnouncementRow) {
            title.text = row.title
            time.text = row.time
            body.text =
                if (row.htmlContent.isBlank()) {
                    ""
                } else {
                    HtmlCompat.fromHtml(row.htmlContent, HtmlCompat.FROM_HTML_MODE_LEGACY)
                }
            body.movementMethod = LinkMovementMethod.getInstance()
            val bg = if (row.useAltStripe) R.color.conv_message_stripe_b else R.color.conv_message_stripe_a
            itemView.setBackgroundColor(ContextCompat.getColor(itemView.context, bg))
        }
    }
}
