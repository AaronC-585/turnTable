package com.turntable.barcodescanner.redacted

import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.R

data class InboxRow(
    val convId: Int,
    val subject: String,
    val subtitle: String,
    val unread: Boolean,
    val sticky: Boolean,
)

/**
 * Inbox list: sticky pin, unread row highlight + bold title, double-tap to open conversation.
 */
class InboxRowsAdapter(
    private val onOpenConversation: (convId: Int) -> Unit,
) : RecyclerView.Adapter<InboxRowsAdapter.VH>() {

    var rows: List<InboxRow> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private fun resolveSelectableBg(holder: VH): Int {
        val tv = TypedValue()
        return if (holder.itemView.context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground,
                tv,
                true,
            )
        ) {
            tv.resourceId
        } else {
            0
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_inbox_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = rows[position]
        holder.bind(r, onOpenConversation, ::resolveSelectableBg)
    }

    override fun onViewRecycled(holder: VH) {
        holder.clearPendingDoubleTap()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = rows.size

    class VH(private val itemRoot: View) : RecyclerView.ViewHolder(itemRoot) {
        private val pin: ImageView = itemRoot.findViewById(R.id.imageStickyPin)
        private val title: TextView = itemRoot.findViewById(R.id.textTitle)
        private val subtitle: TextView = itemRoot.findViewById(R.id.textSubtitle)
        private var pendingTap: Runnable? = null

        fun clearPendingDoubleTap() {
            pendingTap?.let { itemRoot.removeCallbacks(it) }
            pendingTap = null
        }

        fun bind(
            row: InboxRow,
            onOpen: (Int) -> Unit,
            selectableBg: (VH) -> Int,
        ) {
            clearPendingDoubleTap()
            title.text = row.subject
            subtitle.text = row.subtitle
            subtitle.visibility = if (row.subtitle.isBlank()) View.GONE else View.VISIBLE

            title.typeface = if (row.unread) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (row.unread) {
                itemRoot.setBackgroundResource(R.drawable.bg_inbox_row_unread)
            } else {
                val bg = selectableBg(this)
                if (bg != 0) itemRoot.setBackgroundResource(bg)
            }

            if (row.sticky) {
                pin.visibility = View.VISIBLE
                pin.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
            } else {
                pin.visibility = View.GONE
                pin.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
            }

            val delayMs = 320L
            itemRoot.setOnClickListener {
                if (pendingTap != null) {
                    itemRoot.removeCallbacks(pendingTap!!)
                    pendingTap = null
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                        onOpen(row.convId)
                    }
                } else {
                    pendingTap = Runnable { pendingTap = null }
                    itemRoot.postDelayed(pendingTap!!, delayMs)
                }
            }
        }
    }
}
