package com.turntable.barcodescanner.redacted

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.R

data class TwoLineRow(val title: String, val subtitle: String = "")

class TwoLineRowsAdapter(
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<TwoLineRowsAdapter.VH>() {

    var rows: List<TwoLineRow> = emptyList()
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
        holder.subtitle.visibility = if (r.subtitle.isBlank()) View.GONE else View.VISIBLE
        holder.itemView.setOnClickListener { onClick(position) }
    }

    override fun getItemCount(): Int = rows.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.textTitle)
        val subtitle: TextView = v.findViewById(R.id.textSubtitle)
    }
}
