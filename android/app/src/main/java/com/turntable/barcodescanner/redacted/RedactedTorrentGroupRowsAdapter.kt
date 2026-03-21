package com.turntable.barcodescanner.redacted

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.R

/**
 * Torrent group screen: **edition** header rows (double-tap for edition menu) plus **table** rows
 * like the site torrent table (format, size, snatches, seeders, leechers).
 */
class RedactedTorrentGroupRowsAdapter(
    private val onTorrentClick: (torrentListIndex: Int) -> Unit,
    private val onEditionDoubleTap: (bucketTorrentIndices: List<Int>) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class Edition(val title: String, val bucketTorrentIndices: List<Int>) : Row()
        data class Torrent(
            val formatLine: String,
            val sizeText: String,
            val snatched: Int,
            val seeders: Int,
            val leechers: Int,
            val listIndex: Int,
        ) : Row()
    }

    var rows: List<Row> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
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
        when (val r = rows[position]) {
            is Row.Edition -> {
                val h = holder as EditionVH
                h.text.text = r.title
                val indices = r.bucketTorrentIndices
                val detector = GestureDetector(
                    h.itemView.context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDown(e: MotionEvent): Boolean = true

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
                h.itemView.setOnClickListener { onTorrentClick(r.listIndex) }
            }
        }
    }

    override fun getItemCount(): Int = rows.size

    class EditionVH(val text: TextView) : RecyclerView.ViewHolder(text)
    class TorrentVH(v: View) : RecyclerView.ViewHolder(v) {
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
