package com.turntable.barcodescanner

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.redacted.RedactedExtras
import com.turntable.barcodescanner.redacted.RedactedFormat
import com.turntable.barcodescanner.redacted.RedactedUiHelper

data class RequestSearchRow(
    val requestId: Int,
    val titleLine: String,
    val tagsLine: String,
    val votes: Int,
    val bountyBytes: Long,
    val filledYes: Boolean,
    val fillerName: String,
    val requestorName: String,
    val createdRel: String,
    val lastVoteRel: String,
)

class RedactedRequestResultsAdapter(
    private val activity: androidx.appcompat.app.AppCompatActivity,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var rows: List<RequestSearchRow> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount(): Int = if (rows.isEmpty()) 0 else rows.size + 1

    override fun getItemViewType(position: Int): Int =
        if (position == 0) VIEW_HEADER else VIEW_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_HEADER) {
            HeaderVH(inf.inflate(R.layout.item_redacted_request_header, parent, false))
        } else {
            RowVH(inf.inflate(R.layout.item_redacted_request_result, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is RowVH) {
            val row = rows[position - 1]
            holder.bind(activity, row)
        }
    }

    private class HeaderVH(view: View) : RecyclerView.ViewHolder(view)

    private class RowVH(private val view: View) : RecyclerView.ViewHolder(view) {
        private val textTitle = view.findViewById<TextView>(R.id.textTitle)
        private val textTags = view.findViewById<TextView>(R.id.textTags)
        private val textVotes = view.findViewById<TextView>(R.id.textVotes)
        private val textVotePlus = view.findViewById<TextView>(R.id.textVotePlus)
        private val textBounty = view.findViewById<TextView>(R.id.textBounty)
        private val textFilled = view.findViewById<TextView>(R.id.textFilled)
        private val textFilledBy = view.findViewById<TextView>(R.id.textFilledBy)
        private val textRequestedBy = view.findViewById<TextView>(R.id.textRequestedBy)
        private val textCreated = view.findViewById<TextView>(R.id.textCreated)
        private val textLastVote = view.findViewById<TextView>(R.id.textLastVote)

        fun bind(activity: androidx.appcompat.app.AppCompatActivity, row: RequestSearchRow) {
            textTitle.text = row.titleLine
            textTags.text = row.tagsLine.ifBlank { " " }
            textVotes.text = row.votes.toString()
            textVotePlus.text = activity.getString(R.string.redacted_requests_vote_plus)
            textBounty.text = RedactedFormat.formatBytes(row.bountyBytes)
            textFilled.text = if (row.filledYes) {
                activity.getString(R.string.redacted_requests_filled_yes)
            } else {
                activity.getString(R.string.redacted_requests_filled_no)
            }
            textFilledBy.text = row.fillerName.ifBlank { "—" }
            textRequestedBy.text = row.requestorName.ifBlank { "—" }
            textCreated.text = row.createdRel
            textLastVote.text = row.lastVoteRel

            val openDetail = View.OnClickListener {
                activity.startActivity(
                    Intent(activity, RedactedRequestDetailActivity::class.java)
                        .putExtra(RedactedExtras.REQUEST_ID, row.requestId),
                )
            }
            view.setOnClickListener(openDetail)
            textTitle.setOnClickListener(openDetail)

            textVotePlus.setOnClickListener {
                RedactedUiHelper.openSite(activity, "requests.php?action=view&id=${row.requestId}")
            }

            textRequestedBy.setOnClickListener {
                val u = row.requestorName.trim()
                if (u.isNotEmpty()) {
                    activity.startActivity(
                        Intent(activity, RedactedUserProfileActivity::class.java)
                            .putExtra(RedactedExtras.USERNAME, u),
                    )
                }
            }
        }
    }

    companion object {
        private const val VIEW_HEADER = 0
        private const val VIEW_ROW = 1
    }
}
