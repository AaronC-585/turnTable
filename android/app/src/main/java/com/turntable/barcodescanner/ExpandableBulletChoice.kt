package com.turntable.barcodescanner

import android.view.View
import androidx.annotation.ArrayRes
import com.turntable.barcodescanner.databinding.ExpandableBulletChoiceBinding

/**
 * Collapsible header + bullet-prefixed single-choice [android.widget.ListView].
 * Used on Redacted browse (string-array filters) and list screens (in-memory label lists).
 */
object ExpandableBulletChoice {

    fun bindFromArray(
        binding: ExpandableBulletChoiceBinding,
        titleOverride: String?,
        @ArrayRes displayArrayRes: Int,
        initialIndex: Int,
    ) {
        val labels = binding.root.context.resources.getStringArray(displayArrayRes).toList()
        val title = titleOverride?.takeIf { it.isNotBlank() }
            ?: labels.firstOrNull()?.trim().orEmpty()
        binding.textExpandTitle.text = title
        fun summaryLabel(idx: Int): String =
            labels.getOrNull(idx)?.trim().orEmpty().ifBlank { "—" }
        binding.textExpandSummary.text = summaryLabel(initialIndex)

        ListViewSingleChoice.bindBulletFromResource(
            binding.listExpandChoices,
            displayArrayRes,
            initialIndex,
        ) { pos ->
            binding.textExpandSummary.text = summaryLabel(pos)
        }
        wireToggle(binding)
    }

    fun bindLabelList(
        binding: ExpandableBulletChoiceBinding,
        title: String,
        labels: List<String>,
        initialIndex: Int,
        onItemClick: (position: Int) -> Unit = {},
    ) {
        binding.textExpandTitle.text = title
        fun summaryLabel(idx: Int): String =
            labels.getOrNull(idx)?.trim().orEmpty().ifBlank { "—" }
        binding.textExpandSummary.text = summaryLabel(initialIndex)
        val bulleted = labels.map { label -> "•\u00A0${label.trim()}" }
        ListViewSingleChoice.bindStrings(
            binding.listExpandChoices,
            bulleted,
            initialIndex,
        ) { pos ->
            binding.textExpandSummary.text = summaryLabel(pos)
            onItemClick(pos)
        }
        wireToggle(binding)
    }

    private fun wireToggle(binding: ExpandableBulletChoiceBinding) {
        binding.expandHeader.setOnClickListener {
            val open = binding.listExpandChoices.visibility != View.VISIBLE
            binding.listExpandChoices.visibility = if (open) View.VISIBLE else View.GONE
            binding.imageExpandChevron.rotation = if (open) 180f else 0f
        }
        binding.listExpandChoices.visibility = View.GONE
        binding.imageExpandChevron.rotation = 0f
    }
}
