package com.turntable.barcodescanner

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.atomic.AtomicInteger

class TurnTableApp : Application() {

    /** Bumped when the user changes theme so activities refresh on next [Activity.onResume]. */
    private val themeEpoch = AtomicInteger(0)

    fun bumpThemeEpoch() {
        themeEpoch.incrementAndGet()
    }

    override fun onCreate() {
        AppTheme.applyPersistentNightMode(this)
        super.onCreate()
        registerActivityLifecycleCallbacks(ThemeEpochCoordinator(themeEpoch))
        AppBottomBars.registerIn(this)
    }
}

private class ThemeEpochCoordinator(
    private val themeEpoch: AtomicInteger
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityResumed(activity: Activity) {
        val compat = activity as? AppCompatActivity ?: return
        val decor = compat.window.decorView
        val last = decor.getTag(DECOR_TAG_KEY) as? Int ?: -1
        val current = themeEpoch.get()
        if (last != current) {
            decor.setTag(DECOR_TAG_KEY, current)
            compat.delegate.applyDayNight()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        private const val DECOR_TAG_KEY = 0x7e031577
    }
}
