package com.turntable.barcodescanner

import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.turntable.barcodescanner.databinding.ActivityHomeBinding
import com.turntable.barcodescanner.redacted.RedactedProfileUiBuilder

/**
 * Inflates the home-style profile header + collapsible sections into [ActivityHomeBinding].
 * Shared by [HomeActivity] and [RedactedUserProfileActivity].
 */
class HomeProfileLayoutController(
    private val activity: AppCompatActivity,
    private val binding: ActivityHomeBinding,
) {

    fun clearProfileContainers() {
        binding.containerHeaderPersonal.removeAllViews()
        binding.containerHeaderStatistics.removeAllViews()
        binding.containerProfileSections.removeAllViews()
    }

    fun inflateProfileSections(sections: List<RedactedProfileUiBuilder.ProfileSection>) {
        clearProfileContainers()

        val statistics = sections.find { it.titleRes == R.string.home_section_statistics }
        val personal = sections.find { it.titleRes == R.string.home_section_personal }
        val rest = sections.filter {
            it.titleRes != R.string.home_section_statistics &&
                it.titleRes != R.string.home_section_personal
        }

        statistics?.let { inflateStaticSection(it, binding.containerHeaderStatistics, narrow = false) }
        personal?.let { inflateStaticSection(it, binding.containerHeaderPersonal, narrow = true) }

        binding.containerHeaderStatistics.visibility =
            if (statistics != null) View.VISIBLE else View.GONE
        binding.containerHeaderPersonal.visibility =
            if (personal != null) View.VISIBLE else View.GONE

        for (section in rest) {
            inflateCollapsibleSection(section, binding.containerProfileSections)
        }
    }

    private fun inflateStaticSection(
        section: RedactedProfileUiBuilder.ProfileSection,
        parent: android.widget.FrameLayout,
        narrow: Boolean,
    ) {
        parent.removeAllViews()
        val layoutRes = if (narrow) {
            R.layout.home_profile_section_narrow
        } else {
            R.layout.home_profile_section
        }
        val secView = activity.layoutInflater.inflate(layoutRes, parent, false)
        bindSectionTitleAndRows(secView, section, compactRows = narrow)
        parent.addView(secView)
    }

    private fun inflateCollapsibleSection(
        section: RedactedProfileUiBuilder.ProfileSection,
        parent: LinearLayout,
    ) {
        val secView = activity.layoutInflater.inflate(R.layout.home_profile_section_collapsible, parent, false)
        val title = if (section.titleArg != null) {
            activity.getString(section.titleRes, section.titleArg)
        } else {
            activity.getString(section.titleRes)
        }
        secView.findViewById<TextView>(R.id.textSectionTitle).text = title
        val headerRow = secView.findViewById<LinearLayout>(R.id.sectionHeaderRow)
        val rowsLayout = secView.findViewById<LinearLayout>(R.id.layoutSectionRows)
        val chevron = secView.findViewById<TextView>(R.id.textChevron)

        rowsLayout.visibility = View.GONE
        chevron.text = activity.getString(R.string.home_section_collapsed_icon)

        addProfileRows(rowsLayout, section.rows, compactRows = false)

        headerRow.setOnClickListener {
            val open = rowsLayout.visibility != View.VISIBLE
            rowsLayout.visibility = if (open) View.VISIBLE else View.GONE
            chevron.text = activity.getString(
                if (open) R.string.home_section_expanded_icon else R.string.home_section_collapsed_icon,
            )
        }
        parent.addView(secView)
    }

    private fun bindSectionTitleAndRows(
        secView: View,
        section: RedactedProfileUiBuilder.ProfileSection,
        compactRows: Boolean = false,
    ) {
        val title = if (section.titleArg != null) {
            activity.getString(section.titleRes, section.titleArg)
        } else {
            activity.getString(section.titleRes)
        }
        secView.findViewById<TextView>(R.id.textSectionTitle).text = title
        val rowsLayout = secView.findViewById<LinearLayout>(R.id.layoutSectionRows)
        addProfileRows(rowsLayout, section.rows, compactRows)
    }

    private fun addProfileRows(
        rowsLayout: LinearLayout,
        rows: List<RedactedProfileUiBuilder.ProfileRow>,
        compactRows: Boolean = false,
    ) {
        val colorMuted = ContextCompat.getColor(activity, R.color.home_text_muted)
        val colorSecondary = ContextCompat.getColor(activity, R.color.home_text_secondary)
        val colorPrimary = ContextCompat.getColor(activity, R.color.home_text_primary)

        val rowLayout = if (compactRows) {
            R.layout.home_profile_row_compact
        } else {
            R.layout.home_profile_row
        }
        for (row in rows) {
            val rv = activity.layoutInflater.inflate(rowLayout, rowsLayout, false)
            val labelTv = rv.findViewById<TextView>(R.id.textLabel)
            val valueTv = rv.findViewById<TextView>(R.id.textValue)
            val fullTv = rv.findViewById<TextView>(R.id.textFullWidth)

            val footerOnly = row.label.isEmpty() && row.value.isEmpty() && row.footer != null
            if (footerOnly) {
                labelTv.visibility = View.GONE
                valueTv.visibility = View.GONE
                fullTv.visibility = View.VISIBLE
                fullTv.text = row.footer
                fullTv.setTextColor(colorPrimary)
                fullTv.setTypeface(null, if (row.footerBold) Typeface.BOLD else Typeface.NORMAL)
            } else {
                labelTv.visibility = View.VISIBLE
                valueTv.visibility = View.VISIBLE
                labelTv.text = row.label
                valueTv.text = row.value
                row.labelColorRes?.let { labelTv.setTextColor(ContextCompat.getColor(activity, it)) }
                    ?: labelTv.setTextColor(colorMuted)
                row.valueColorRes?.let { valueTv.setTextColor(ContextCompat.getColor(activity, it)) }
                    ?: valueTv.setTextColor(colorSecondary)
                valueTv.setTypeface(null, if (row.valueBold) Typeface.BOLD else Typeface.NORMAL)

                if (row.footer != null) {
                    fullTv.visibility = View.VISIBLE
                    fullTv.text = row.footer
                    fullTv.setTextColor(colorPrimary)
                    fullTv.setTypeface(null, if (row.footerBold) Typeface.BOLD else Typeface.NORMAL)
                } else {
                    fullTv.visibility = View.GONE
                }
            }
            rowsLayout.addView(rv)
        }
    }
}
