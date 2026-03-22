package com.turntable.barcodescanner

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.lang.ref.WeakReference

/**
 * Tracks the last [AppCompatActivity] that received [Activity.onResume], for routing
 * splash-time update checks to the visible screen.
 */
object ForegroundActivityHolder {

    private var ref: WeakReference<AppCompatActivity>? = null

    fun current(): AppCompatActivity? = ref?.get()

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(Callbacks)
    }

    private object Callbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity is AppCompatActivity) {
                ref = WeakReference(activity)
            }
        }

        override fun onActivityPaused(activity: Activity) {
            if (ref?.get() === activity) {
                ref = null
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
