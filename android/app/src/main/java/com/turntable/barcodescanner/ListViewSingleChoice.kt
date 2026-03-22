package com.turntable.barcodescanner

import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView

/**
 * Binds a [ListView] as a single-choice “listbox” (no dropdown): all options scroll in-place.
 */
object ListViewSingleChoice {

    fun bindStrings(
        listView: ListView,
        items: List<String>,
        initialIndex: Int,
        onItemClick: (position: Int) -> Unit = {},
    ) {
        listView.adapter = ArrayAdapter(
            listView.context,
            android.R.layout.simple_list_item_single_choice,
            items,
        )
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            listView.setItemChecked(position, true)
            onItemClick(position)
        }
        listView.post {
            if (items.isNotEmpty()) {
                val i = initialIndex.coerceIn(0, items.lastIndex)
                listView.setItemChecked(i, true)
            }
        }
    }

    fun bindFromResource(
        listView: ListView,
        displayArrayRes: Int,
        initialIndex: Int,
        onItemClick: (position: Int) -> Unit = {},
    ) {
        val items = listView.context.resources.getStringArray(displayArrayRes).toList()
        bindStrings(listView, items, initialIndex, onItemClick)
    }

    fun selectedIndex(listView: ListView): Int {
        val p = listView.checkedItemPosition
        return if (p >= 0) p else 0
    }
}

/** Map listbox row to API value array (same indexing as former Spinner). */
fun ListView.apiValue(values: Array<String>): String? {
    val i = ListViewSingleChoice.selectedIndex(this)
    if (i < 0 || i >= values.size) return null
    return values[i].takeIf { it.isNotEmpty() }
}
