package com.turntable.barcodescanner

import com.google.android.material.textfield.TextInputEditText
import com.turntable.barcodescanner.databinding.ContentRedactedCollagesSearchBinding

/**
 * Shared collage search form: order dropdowns and API param list (same as [RedactedCollagesSearchActivity]).
 */
object RedactedCollagesSearchForm {

    fun setupOrderChoices(
        form: ContentRedactedCollagesSearchBinding,
        orderByValues: Array<String>,
        orderWayValues: Array<String>,
    ) {
        ExpandableBulletChoice.bindFromArray(
            form.expandOrderBy,
            null,
            R.array.redacted_collages_order_by,
            1.coerceAtMost(orderByValues.size - 1),
        )
        ExpandableBulletChoice.bindFromArray(
            form.expandOrderWay,
            null,
            R.array.redacted_collages_order_way,
            1.coerceAtMost(orderWayValues.size - 1),
        )
    }

    private fun MutableList<Pair<String, String?>>.putNonBlank(key: String, edit: TextInputEditText) {
        val v = edit.text?.toString()?.trim()
        if (!v.isNullOrEmpty()) add(key to v)
    }

    fun buildParams(form: ContentRedactedCollagesSearchBinding, page: Int): List<Pair<String, String?>> = buildList {
        add("page" to page.toString())
        putNonBlank("search", form.editSearch)
        putNonBlank("tags", form.editTags)
        val tagText = form.editTags.text?.toString()?.trim()
        if (!tagText.isNullOrEmpty()) {
            add("tags_type" to if (form.radioTagsAll.isChecked) "1" else "0")
        }
        val type = if (form.radioSearchNames.isChecked) "name" else "description"
        add("type" to type)

        val checks = listOf(
            form.checkCat0,
            form.checkCat1,
            form.checkCat2,
            form.checkCat3,
            form.checkCat4,
            form.checkCat5,
            form.checkCat6,
            form.checkCat7,
        )
        for (i in checks.indices) {
            if (checks[i].isChecked) {
                add("cats[$i]" to "1")
            }
        }

        form.expandOrderBy.listExpandChoices.apiValue(
            form.root.context.resources.getStringArray(R.array.redacted_collages_order_by_values),
        )?.let { add("order" to it) }
        form.expandOrderWay.listExpandChoices.apiValue(
            form.root.context.resources.getStringArray(R.array.redacted_collages_order_way_values),
        )?.let { add("sort" to it) }
    }
}
