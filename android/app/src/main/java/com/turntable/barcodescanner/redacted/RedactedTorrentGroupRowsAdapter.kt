package com.turntable.barcodescanner.redacted

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.R

/**
 * Torrent group screen: **edition** header rows (single-tap collapse/expand, double-tap for edition menu)
 * plus **table** rows like the site torrent table.
 */
class RedactedTorrentGroupRowsAdapter(
    /** Single tap: e.g. open torrent detail. */
    private val onTorrentClick: (torrentListIndex: Int) -> Unit,
    private val onEditionDoubleTap: (bucketTorrentIndices: List<Int>) -> Unit,
    /** Long-press: e.g. download / client actions menu (optional). */
    private val onTorrentLongClick: ((torrentListIndex: Int) -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        /**
         * @param editionAnchorIndex Index of this edition row in the full [rows] list (stable while this group is shown).
         */
        data class Edition(
            val title: String,
            val bucketTorrentIndices: List<Int>,
            val editionAnchorIndex: Int,
        ) : Row()
        data class Torrent(
            val formatLine: String,
            val sizeText: String,
            val snatched: Int,
            val seeders: Int,
            val leechers: Int,
            val listIndex: Int,
            /** Show acorn when this release is one you are seeding. */
            val isUserSeeding: Boolean = false,
        ) : Row()
    }

    /** Full row list (editions + torrents); collapse state hides torrent blocks under an edition header. */
    private var fullRows: List<Row> = emptyList()
    private val collapsedEditionAnchors = mutableSetOf<Int>()
    private var displayRows: List<Row> = emptyList()

    var rows: List<Row>
        get() = fullRows
        set(value) {
            fullRows = value
            collapsedEditionAnchors.clear()
            rebuildDisplayRows()
            notifyDataSetChanged()
        }

    private fun rebuildDisplayRows() {
        val result = mutableListOf<Row>()
        var i = 0
        while (i < fullRows.size) {
            when (val row = fullRows[i]) {
                is Row.Edition -> {
                    result.add(row)
                    val hideTorrents = collapsedEditionAnchors.contains(row.editionAnchorIndex)
                    i++
                    while (i < fullRows.size && fullRows[i] is Row.Torrent) {
                        if (!hideTorrents) result.add(fullRows[i] as Row.Torrent)
                        i++
                    }
                }
                is Row.Torrent -> {
                    result.add(row)
                    i++
                }
            }
        }
        displayRows = result
    }

    private fun toggleEditionCollapse(editionAnchorIndex: Int) {
        if (!collapsedEditionAnchors.remove(editionAnchorIndex)) {
            collapsedEditionAnchors.add(editionAnchorIndex)
        }
        rebuildDisplayRows()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (displayRows[position]) {
        is Row.Edition -> VIEW_EDITION
        is Row.Torrent -> VIEW_TORRENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_EDITION -> {
                val v = inf.inflate(R.layout.item_torrent_edition_header, parent, false) as TextView
                EditionVH(v)
            }
            else -> {
                val v = inf.inflate(R.layout.item_torrent_table_row, parent, false)
                TorrentVH(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val r = displayRows[position]) {
            is Row.Edition -> {
                val h = holder as EditionVH
                val collapsed = collapsedEditionAnchors.contains(r.editionAnchorIndex)
                val chevron = if (collapsed) "▶ " else "▼ "
                h.text.text = "$chevron${r.title}"
                val ctx = h.itemView.context
                val stateHint = ctx.getString(
                    if (collapsed) R.string.redacted_edition_collapsed else R.string.redacted_edition_expanded,
                )
                h.itemView.contentDescription = ctx.getString(
                    R.string.redacted_edition_header_accessibility,
                    r.title,
                    stateHint,
                )
                val indices = r.bucketTorrentIndices
                val detector = GestureDetector(
                    h.itemView.context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDown(e: MotionEvent): Boolean = true

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            toggleEditionCollapse(r.editionAnchorIndex)
                            return true
                        }

                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            if (indices.isNotEmpty()) onEditionDoubleTap(indices)
                            return true
                        }
                    },
                )
                h.itemView.isClickable = true
                h.itemView.setOnTouchListener { _, ev -> detector.onTouchEvent(ev) }
            }
            is Row.Torrent -> {
                val h = holder as TorrentVH
                h.format.text = r.formatLine
                h.size.text = r.sizeText
                h.snatched.text = r.snatched.toString()
                h.seeders.text = r.seeders.toString()
                h.leechers.text = r.leechers.toString()
                if (r.isUserSeeding) {
                    h.seedingMark.visibility = View.VISIBLE
                    h.seedingMark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
                } else {
                    h.seedingMark.visibility = View.GONE
                    h.seedingMark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
                }
                h.itemView.setOnClickListener { onTorrentClick(r.listIndex) }
                if (onTorrentLongClick != null) {
                    h.itemView.setOnLongClickListener {
                        onTorrentLongClick.invoke(r.listIndex)
                        true
                    }
                } else {
                    h.itemView.setOnLongClickListener(null)
                }
            }
        }
    }

    override fun getItemCount(): Int = displayRows.size

    class EditionVH(val text: TextView) : RecyclerView.ViewHolder(text)
    class TorrentVH(v: View) : RecyclerView.ViewHolder(v) {
        val seedingMark: ImageView = v.findViewById(R.id.imageSeedingUtorrent)
        val format: TextView = v.findViewById(R.id.textFormat)
        val size: TextView = v.findViewById(R.id.textSize)
        val snatched: TextView = v.findViewById(R.id.textSnatched)
        val seeders: TextView = v.findViewById(R.id.textSeeders)
        val leechers: TextView = v.findViewById(R.id.textLeechers)
    }

    companion object {
        private const val VIEW_EDITION = 0
        private const val VIEW_TORRENT = 1
    }
}
