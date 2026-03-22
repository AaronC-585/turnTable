package com.turntable.barcodescanner.redacted

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.DownloadNetworkPolicy
import com.turntable.barcodescanner.R
import com.turntable.barcodescanner.SearchPrefs

data class AnnouncementRow(
    val title: String,
    val time: String,
    /** HTML for [HtmlCompat.fromHtml] (image tags stripped; see [imageUrls]). */
    val htmlContent: String,
    /** Image URLs from the original body; loaded below the text. */
    val imageUrls: List<String>,
    val useAltStripe: Boolean,
)

/**
 * News / announcements list: renders HTML body and downloads embedded images (e.g. `[img]` / `<img>`).
 */
class AnnouncementsAdapter(
    private val redactedAuthorizationKey: String,
) : RecyclerView.Adapter<AnnouncementsAdapter.VH>() {

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
        holder.bind(rows[position], redactedAuthorizationKey)
    }

    override fun getItemCount(): Int = rows.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.textAnnouncementTitle)
        private val time: TextView = itemView.findViewById(R.id.textAnnouncementTime)
        private val body: TextView = itemView.findViewById(R.id.textAnnouncementBody)
        private val images: LinearLayout = itemView.findViewById(R.id.containerAnnouncementImages)

        fun bind(row: AnnouncementRow, auth: String) {
            itemView.tag = row
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

            images.removeAllViews()
            val ctx = itemView.context
            val prefs = SearchPrefs(ctx)
            val allowImages = !prefs.downloadOverWifiOnly ||
                DownloadNetworkPolicy.allowsLargeDownload(ctx, true)
            if (row.imageUrls.isEmpty() || !allowImages) {
                images.visibility = View.GONE
            } else {
                images.visibility = View.VISIBLE
                val density = itemView.resources.displayMetrics.density
                val marginTop = (8 * density).toInt()
                for (url in row.imageUrls) {
                    val iv = ImageView(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = marginTop
                        }
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription =
                            itemView.context.getString(R.string.redacted_announcement_image_cd)
                    }
                    images.addView(iv)
                    Thread {
                        val bmp = RedactedAvatarLoader.loadBitmap(url, auth, maxSidePx = 1440)
                        itemView.post {
                            if (itemView.tag != row) return@post
                            if (bmp != null) {
                                iv.setImageBitmap(bmp)
                            } else {
                                iv.visibility = View.GONE
                            }
                        }
                    }.start()
                }
            }
        }
    }
}
