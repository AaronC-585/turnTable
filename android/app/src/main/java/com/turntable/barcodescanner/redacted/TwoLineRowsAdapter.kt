package com.turntable.barcodescanner.redacted

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.R

data class TwoLineRow(
    val title: String,
    val subtitle: String = "",
    /** Optional cover from Redacted `browse` / similar JSON (`cover` field). */
    val coverUrl: String? = null,
)

class TwoLineRowsAdapter(
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<TwoLineRowsAdapter.VH>() {

    var rows: List<TwoLineRow> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /**
     * When set (e.g. on torrent browse), cover thumbnails use [RedactedAvatarLoader] so redacted.sh paths work.
     */
    var redactedAuthorizationKey: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_redacted_two_line, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = rows[position]
        holder.title.text = r.title
        holder.subtitle.text = r.subtitle
        holder.subtitle.visibility = if (r.subtitle.isBlank()) View.GONE else View.VISIBLE
        holder.itemView.setOnClickListener { onClick(position) }

        val raw = r.coverUrl?.trim().orEmpty()
        if (raw.isEmpty()) {
            holder.cover.visibility = View.GONE
            holder.cover.setImageDrawable(null)
            holder.cover.tag = null
        } else {
            holder.cover.visibility = View.VISIBLE
            holder.cover.setImageDrawable(null)
            holder.cover.tag = raw
            val auth = redactedAuthorizationKey
            Thread {
                val bmp = RedactedAvatarLoader.loadBitmap(raw, auth, maxSidePx = 128)
                holder.itemView.post {
                    if (holder.cover.tag != raw) return@post
                    if (bmp != null) {
                        holder.cover.setImageBitmap(bmp)
                        holder.cover.visibility = View.VISIBLE
                    } else {
                        holder.cover.setImageDrawable(null)
                        holder.cover.visibility = View.GONE
                    }
                }
            }.start()
        }
    }

    override fun getItemCount(): Int = rows.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cover: ImageView = v.findViewById(R.id.imageCover)
        val title: TextView = v.findViewById(R.id.textTitle)
        val subtitle: TextView = v.findViewById(R.id.textSubtitle)
    }
}
