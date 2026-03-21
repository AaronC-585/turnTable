package com.turntable.barcodescanner.debug

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.KeyEvent
import android.view.Window
import androidx.appcompat.view.WindowCallbackWrapper
import com.turntable.barcodescanner.DebugEventLogActivity
import java.util.concurrent.atomic.AtomicLong

/**
 * Test-only shortcut: press **Volume Up**, then within a few seconds **shake** the device
 * to open [DebugEventLogActivity]. Also tracks volume-up for [DebugShortcutGate].
 */
object DebugShortcutGate {
    private val lastVolumeUpDownMs = AtomicLong(0L)

    /** Call from window callback on KEYCODE_VOLUME_UP ACTION_DOWN. */
    fun onVolumeUpDown() {
        lastVolumeUpDownMs.set(System.currentTimeMillis())
    }

    fun wasVolumeUpRecent(withinMs: Long = 3500L): Boolean {
        val t = lastVolumeUpDownMs.get()
        if (t <= 0L) return false
        return System.currentTimeMillis() - t <= withinMs
    }
}

private class VolumeSniffCallback(delegate: Window.Callback) : WindowCallbackWrapper(delegate) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.action == KeyEvent.ACTION_DOWN) {
            DebugShortcutGate.onVolumeUpDown()
        }
        return super.dispatchKeyEvent(event)
    }
}

private class VolumeWindowInstaller : Application.ActivityLifecycleCallbacks {
    override fun onActivityResumed(activity: Activity) {
        if (activity is DebugEventLogActivity) return
        val w = activity.window
        val cb = w.callback
        if (cb is VolumeSniffCallback) return
        w.callback = VolumeSniffCallback(cb)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

private class ShakeOpener(private val appContext: Context) : SensorEventListener {

    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val shakeTimes = ArrayList<Long>(8)
    private var lastFireMs = 0L
    private var lastGx = 0f
    private var lastGy = 0f
    private var lastGz = 0f
    private var lastAccelTs = 0L

    fun register() {
        sensor ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val mag = when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            }
            else -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val now = System.currentTimeMillis()
                if (lastAccelTs == 0L) {
                    lastGx = x
                    lastGy = y
                    lastGz = z
                    lastAccelTs = now
                    return
                }
                val dt = (now - lastAccelTs).coerceAtLeast(1L)
                val dx = x - lastGx
                val dy = y - lastGy
                val dz = z - lastGz
                lastGx = x
                lastGy = y
                lastGz = z
                lastAccelTs = now
                kotlin.math.abs(dx + dy + dz) / dt * 10000f
            }
        }

        val threshold = if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) 14f else 450f
        if (mag < threshold) return

        val now = System.currentTimeMillis()
        synchronized(shakeTimes) {
            shakeTimes.add(now)
            val cutoff = now - 1200L
            shakeTimes.removeAll { it < cutoff }
            if (shakeTimes.size < 4) return
            shakeTimes.clear()
        }

        if (now - lastFireMs < 2500L) return
        if (!DebugShortcutGate.wasVolumeUpRecent()) return

        lastFireMs = now
        AppEventLog.log(AppEventLog.Category.SYSTEM, "Debug log opened (Vol↑ + shake)")
        val i = Intent(appContext, DebugEventLogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        appContext.startActivity(i)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

object DebugShortcutCoordinator {
    private var shake: ShakeOpener? = null

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(VolumeWindowInstaller())
        if (shake == null) {
            shake = ShakeOpener(app.applicationContext).also { it.register() }
        }
        AppEventLog.log(AppEventLog.Category.SYSTEM, "App started (event log active; Vol↑ then shake for viewer)")
    }
}
