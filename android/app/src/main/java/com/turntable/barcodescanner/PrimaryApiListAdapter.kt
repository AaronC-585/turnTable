package com.turntable.barcodescanner

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.databinding.ItemPrimaryApiBinding

class PrimaryApiListAdapter(
    private val entries: MutableList<SearchPresets.PrimaryApiEntry>,
    private val startDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onRemoveBlocked: () -> Unit,
) : RecyclerView.Adapter<PrimaryApiListAdapter.Vh>() {

    inner class Vh(val binding: ItemPrimaryApiBinding) : RecyclerView.ViewHolder(binding.root) {
        var nameWatcher: TextWatcher? = null
        var cmdWatcher: TextWatcher? = null
        var suppress = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemPrimaryApiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: Vh, position: Int) {
        val binding = holder.binding
        binding.editDisplayName.removeTextChangedListener(holder.nameWatcher)
        binding.editCmd.removeTextChangedListener(holder.cmdWatcher)
        binding.switchEnabled.setOnCheckedChangeListener(null)

        val entry = entries[position]
        holder.suppress = true
        binding.switchEnabled.isChecked = entry.enabled
        binding.editDisplayName.setText(entry.displayName)
        binding.editCmd.setText(entry.cmd)
        holder.suppress = false

        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            if (holder.suppress) return@setOnCheckedChangeListener
            val pos = holder.bindingAdapterPosition
            if (pos in entries.indices) {
                entries[pos] = entries[pos].copy(enabled = checked)
            }
        }

        holder.nameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (holder.suppress) return
                val pos = holder.bindingAdapterPosition
                if (pos !in entries.indices) return
                val name = s?.toString()?.trim().orEmpty()
                val cur = entries[pos]
                val cmd = binding.editCmd.text?.toString()?.trim().orEmpty()
                val defaultName = SearchPresets.findPrimaryPresetByCmd(cmd)?.name ?: cmd
                val displayName = if (name.isBlank()) defaultName else name
                entries[pos] = cur.copy(cmd = cmd, displayName = displayName)
            }
        }
        binding.editDisplayName.addTextChangedListener(holder.nameWatcher)

        holder.cmdWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (holder.suppress) return
                val pos = holder.bindingAdapterPosition
                if (pos !in entries.indices) return
                val cmd = s?.toString()?.trim().orEmpty()
                val cur = entries[pos]
                val nameField = binding.editDisplayName.text?.toString()?.trim().orEmpty()
                val defaultName = SearchPresets.findPrimaryPresetByCmd(cmd)?.name ?: cmd
                val displayName = if (nameField.isBlank()) defaultName else nameField
                entries[pos] = cur.copy(cmd = cmd, displayName = displayName)
            }
        }
        binding.editCmd.addTextChangedListener(holder.cmdWatcher)

        binding.buttonDelete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos !in entries.indices) return@setOnClickListener
            if (entries.size <= 1) {
                onRemoveBlocked()
                return@setOnClickListener
            }
            entries.removeAt(pos)
            notifyItemRemoved(pos)
            notifyItemRangeChanged(pos, entries.size - pos)
        }

        binding.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                startDrag(holder)
            }
            false
        }
    }

    override fun onViewRecycled(holder: Vh) {
        holder.binding.editDisplayName.removeTextChangedListener(holder.nameWatcher)
        holder.binding.editCmd.removeTextChangedListener(holder.cmdWatcher)
        holder.binding.switchEnabled.setOnCheckedChangeListener(null)
        holder.nameWatcher = null
        holder.cmdWatcher = null
        super.onViewRecycled(holder)
    }
}
