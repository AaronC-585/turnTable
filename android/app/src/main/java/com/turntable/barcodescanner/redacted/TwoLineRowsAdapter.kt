package com.turntable.barcodescanner.redacted

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.R

data class TwoLineRow(
    val title: String,
    val subtitle: String = "",
    /** Optional cover from Redacted `browse` / similar JSON (`cover` field). */
    val coverUrl: String? = null,
    /** When [coverUrl] is empty, show this drawable in the cover slot (e.g. collage list vs torrent browse). */
    val coverPlaceholderResId: Int? = null,
    /** Acorn when this row is a torrent you are seeding (e.g. user torrents list). */
    val showSeedingUtorrentIcon: Boolean = false,
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

        if (r.showSeedingUtorrentIcon) {
            holder.seedingMark.visibility = View.VISIBLE
            holder.seedingMark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        } else {
            holder.seedingMark.visibility = View.GONE
            holder.seedingMark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        }

        val raw = r.coverUrl?.trim().orEmpty()
        val ph = r.coverPlaceholderResId
        when {
            raw.isNotEmpty() -> {
                holder.cover.visibility = View.VISIBLE
                holder.cover.setImageDrawable(null)
                ImageViewCompat.setImageTintList(holder.cover, null)
                holder.cover.alpha = 1f
                holder.cover.tag = raw
                val auth = redactedAuthorizationKey
                Thread {
                    val bmp = RedactedAvatarLoader.loadBitmap(raw, auth, maxSidePx = 128)
                    holder.itemView.post {
                        if (holder.cover.tag != raw) return@post
                        if (bmp != null) {
                            ImageViewCompat.setImageTintList(holder.cover, null)
                            holder.cover.alpha = 1f
                            holder.cover.setImageBitmap(bmp)
                            holder.cover.visibility = View.VISIBLE
                        } else if (ph != null) {
                            holder.cover.tag = "__ph__"
                            holder.cover.setImageResource(ph)
                            ImageViewCompat.setImageTintList(
                                holder.cover,
                                ColorStateList.valueOf(
                                    ContextCompat.getColor(holder.cover.context, R.color.app_text_label),
                                ),
                            )
                            holder.cover.alpha = 0.55f
                            holder.cover.visibility = View.VISIBLE
                        } else {
                            holder.cover.setImageDrawable(null)
                            holder.cover.visibility = View.GONE
                        }
                    }
                }.start()
            }
            ph != null -> {
                holder.cover.visibility = View.VISIBLE
                holder.cover.tag = "__ph__"
                holder.cover.setImageResource(ph)
                ImageViewCompat.setImageTintList(
                    holder.cover,
                    ColorStateList.valueOf(
                        ContextCompat.getColor(holder.cover.context, R.color.app_text_label),
                    ),
                )
                holder.cover.alpha = 0.55f
            }
            else -> {
                holder.cover.visibility = View.GONE
                holder.cover.setImageDrawable(null)
                holder.cover.tag = null
                ImageViewCompat.setImageTintList(holder.cover, null)
                holder.cover.alpha = 1f
            }
        }
    }

    override fun getItemCount(): Int = rows.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val seedingMark: ImageView = v.findViewById(R.id.imageSeedingUtorrent)
        val cover: ImageView = v.findViewById(R.id.imageCover)
        val title: TextView = v.findViewById(R.id.textTitle)
        val subtitle: TextView = v.findViewById(R.id.textSubtitle)
    }
}
