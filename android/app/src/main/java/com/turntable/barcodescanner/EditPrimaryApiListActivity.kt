package com.turntable.barcodescanner

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.turntable.barcodescanner.databinding.ActivityEditPrimaryApiListBinding

class EditPrimaryApiListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditPrimaryApiListBinding

    private val entries: MutableList<SearchPresets.PrimaryApiEntry> = mutableListOf()
    private lateinit var adapter: PrimaryApiListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditPrimaryApiListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupToolbarHome(binding.toolbar)

        binding.textHelp.setText(R.string.edit_primary_list_help)

        entries.clear()
        entries.addAll(SearchPresets.primaryApiEntries(this))

        val recycler = binding.recyclerPrimaryApis
        recycler.layoutManager = LinearLayoutManager(this)
        lateinit var itemTouchHelper: ItemTouchHelper
        adapter = PrimaryApiListAdapter(
            entries = entries,
            startDrag = { holder -> itemTouchHelper.startDrag(holder) },
        )
        recycler.adapter = adapter

        itemTouchHelper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0,
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                    val item = entries.removeAt(from)
                    entries.add(to, item)
                    adapter.notifyItemMoved(from, to)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

                override fun isLongPressDragEnabled(): Boolean = false
            },
        )
        itemTouchHelper.attachToRecyclerView(recycler)

        binding.buttonSave.setOnClickListener {
            if (entries.none { it.cmd.isNotBlank() }) {
                Toast.makeText(this, R.string.primary_save_need_cmd, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            SearchPrefs(this).primaryApiListText = SearchPresets.serializePrimaryApiList(entries)
            setResult(RESULT_OK)
            finish()
        }

        binding.buttonCancel.setOnClickListener { finish() }
    }
}
