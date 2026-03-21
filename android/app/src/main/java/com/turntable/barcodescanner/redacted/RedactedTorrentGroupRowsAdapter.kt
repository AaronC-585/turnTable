package com.turntable.barcodescanner.redacted

import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.R

/**
 * Torrent group screen: **edition / pressing** header rows plus torrent rows.
 * [torrentIndex] indexes into the activity's parallel torrent id/object lists; null = non-clickable header.
 */
class RedactedTorrentGroupRowsAdapter(
    private val onTorrentClick: (torrentIndex: Int) -> Unit,
) : RecyclerView.Adapter<RedactedTorrentGroupRowsAdapter.VH>() {

    data class Row(
        val title: String,
        val subtitle: String = "",
        val torrentIndex: Int? = null,
    )

    var rows: List<Row> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_redacted_two_line, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = rows[position]
        holder.title.text = r.title
        holder.subtitle.text = r.subtitle
        holder.cover.visibility = View.GONE
        holder.cover.setImageDrawable(null)

        if (r.torrentIndex == null) {
            holder.subtitle.visibility = View.GONE
            holder.title.typeface = Typeface.DEFAULT_BOLD
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.app_text_primary))
            holder.itemView.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.app_card_browse),
            )
            holder.itemView.isClickable = false
            holder.itemView.setOnClickListener(null)
        } else {
            holder.subtitle.visibility = if (r.subtitle.isBlank()) View.GONE else View.VISIBLE
            holder.title.typeface = Typeface.DEFAULT
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.app_text_primary))
            val out = TypedValue()
            holder.itemView.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            holder.itemView.setBackgroundResource(out.resourceId)
            val idx = r.torrentIndex
            holder.itemView.isClickable = true
            holder.itemView.setOnClickListener { onTorrentClick(idx) }
        }
    }

    override fun getItemCount(): Int = rows.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cover: ImageView = v.findViewById(R.id.imageCover)
        val title: TextView = v.findViewById(R.id.textTitle)
        val subtitle: TextView = v.findViewById(R.id.textSubtitle)
    }
}
