package com.turntable.barcodescanner.redacted

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.R

class FriendsListAdapter(
    private val onOpen: (RedactedFriendsStore.Entry) -> Unit,
    private val onRemove: (RedactedFriendsStore.Entry) -> Unit,
) : RecyclerView.Adapter<FriendsListAdapter.VH>() {

    var rows: List<RedactedFriendsStore.Entry> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_friend_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        val ctx = holder.itemView.context
        holder.title.text = row.username
        val lastSeen = RedactedLastSeenFormatter.lastOnlineSummary(ctx, row.lastAccessRaw)
        val tap = ctx.getString(R.string.redacted_friends_tap_profile, row.userId)
        holder.subtitle.text = "$lastSeen\n$tap"
        holder.itemView.setOnClickListener { onOpen(row) }
        holder.remove.setOnClickListener { onRemove(row) }
    }

    override fun getItemCount(): Int = rows.size

    class VH(root: android.view.View) : RecyclerView.ViewHolder(root) {
        val title: TextView = root.findViewById(R.id.textTitle)
        val subtitle: TextView = root.findViewById(R.id.textSubtitle)
        val remove: ImageButton = root.findViewById(R.id.buttonRemove)
    }
}
