package com.turntable.barcodescanner.debug

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets

/**
 * Catches uncaught exceptions: writes a report to app-private storage, logs a short [AppEventLog]
 * line, then forwards to the previous handler (system crash dialog / Play reporting).
 * On the next launch, [ingestPendingCrashIfAny] pulls the file into the event log as **ERROR**.
 */
object CrashReporter {

    private const val CRASH_FILE = "turnTable_last_crash.txt"
    private const val MAX_CRASH_FILE_BYTES = 256_000

    fun install(app: Application) {
        val ctx = app.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                persistCrashSync(ctx, thread, throwable)
                val oneLine = buildString {
                    append(throwable.javaClass.simpleName)
                    append(": ")
                    append(throwable.message?.take(400) ?: "")
                }
                AppEventLog.log(AppEventLog.Category.ERROR, "FATAL (process ending) $oneLine")
            } catch (_: Throwable) {
                // Never throw from here
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun persistCrashSync(ctx: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter(4096)
        PrintWriter(sw).use { pw ->
            pw.println("wallClockMs=${System.currentTimeMillis()}")
            pw.println("thread=${thread.name}")
            pw.println("exception=${throwable.javaClass.name}: ${throwable.message}")
            throwable.printStackTrace(pw)
        }
        var bytes = sw.toString().toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_CRASH_FILE_BYTES) {
            bytes = bytes.copyOf(MAX_CRASH_FILE_BYTES)
        }
        ctx.openFileOutput(CRASH_FILE, Context.MODE_PRIVATE).use { it.write(bytes) }
    }

    /** Call once at startup after [install] so the previous run’s crash appears in [AppEventLog]. */
    fun ingestPendingCrashIfAny(context: Context) {
        val f = File(context.filesDir, CRASH_FILE)
        if (!f.isFile || f.length() == 0L) return
        try {
            val text = f.readText(StandardCharsets.UTF_8).trim().replace("\r\n", "\n")
            f.delete()
            if (text.isNotEmpty()) {
                AppEventLog.log(
                    AppEventLog.Category.ERROR,
                    "Previous session uncaught crash ↳ $text",
                )
            }
        } catch (_: Exception) {
            try {
                f.delete()
            } catch (_: Exception) {
            }
        }
    }
}
