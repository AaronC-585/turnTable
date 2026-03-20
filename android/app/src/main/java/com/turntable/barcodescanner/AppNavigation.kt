package com.turntable.barcodescanner

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/** Opens [HomeActivity], clearing activities above it when Home is already in the task. */
fun Context.navigateToHome() {
    startActivity(
        Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        },
    )
}

/** Adds the Home toolbar action (house icon) that calls [navigateToHome]. */
fun AppCompatActivity.setupToolbarHome(toolbar: MaterialToolbar) {
    toolbar.inflateMenu(R.menu.menu_home_action)
    toolbar.setOnMenuItemClickListener { item ->
        if (item.itemId == R.id.action_home) {
            navigateToHome()
            true
        } else {
            false
        }
    }
}
