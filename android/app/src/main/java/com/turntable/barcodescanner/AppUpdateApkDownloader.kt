package com.turntable.barcodescanner

import android.content.Context
import com.turntable.barcodescanner.debug.OutgoingUrlInterceptor
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** User chose to cancel the in-app update download. */
class AppUpdateDownloadCancelledException : Exception()

/**
 * Streams a release APK to app cache and reports progress for the update UI.
 */
object AppUpdateApkDownloader {

    private val http = OkHttpClient.Builder()
        .addInterceptor(OutgoingUrlInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    /**
     * @param callRef optional; set while the request is in flight so UI can [Call.cancel].
     * @param onProgress [progressPercent] is 0–99 while downloading, 100 when complete; null if size unknown.
     */
    fun downloadApk(
        context: Context,
        url: String,
        callRef: AtomicReference<Call?>,
        cancelled: AtomicBoolean,
        onProgress: (progressPercent: Int?, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "turntable-update-${System.currentTimeMillis()}.apk")

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "turnTable/1.0 (Android)")
            .build()

        val call = http.newCall(req)
        callRef.set(call)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    out.delete()
                    return Result.failure(
                        IllegalStateException("HTTP ${response.code}"),
                    )
                }
                val body = response.body ?: run {
                    out.delete()
                    return Result.failure(IllegalStateException("Empty response"))
                }
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(out).use { fos ->
                        val buf = ByteArray(96 * 1024)
                        var done = 0L
                        var lastUi = 0L
                        while (true) {
                            if (cancelled.get()) {
                                out.delete()
                                return Result.failure(AppUpdateDownloadCancelledException())
                            }
                            val n = input.read(buf)
                            if (n == -1) break
                            fos.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastUi >= 80L) {
                                lastUi = now
                                val p = if (total > 0) {
                                    ((done * 100L) / total).toInt().coerceIn(0, 99)
                                } else {
                                    null
                                }
                                onProgress(p, done, total)
                            }
                        }
                    }
                }
                val finalTotal = if (total > 0) total else out.length()
                onProgress(100, out.length(), finalTotal)
                Result.success(out)
            }
        } catch (e: IOException) {
            out.delete()
            if (cancelled.get() || call.isCanceled()) {
                Result.failure(AppUpdateDownloadCancelledException())
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            out.delete()
            Result.failure(e)
        } finally {
            if (callRef.get() === call) {
                callRef.set(null)
            }
        }
    }
}
