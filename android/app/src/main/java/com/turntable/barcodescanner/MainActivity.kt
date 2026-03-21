package com.turntable.barcodescanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.turntable.barcodescanner.databinding.ActivityMainBinding
import com.turntable.barcodescanner.debug.AppEventLog

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastCode: String? = null
    private var lastCodeTime = 0L
    private val throttleMs = 1500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            return
        }
        startCamera()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            bindPreview(provider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(provider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(ContextCompat.getMainExecutor(this), ::analyze) }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        } catch (e: Exception) {
            Log.e(TAG, "Bind failed", e)
        }
    }

    private fun analyze(imageProxy: ImageProxy) {
        if (imageProxy.format != ImageFormat.YUV_420_888) {
            imageProxy.close()
            return
        }
        val yBuffer = imageProxy.planes[0].buffer
        val ySize = yBuffer.remaining()
        val width = imageProxy.width
        val height = imageProxy.height
        val gray = ByteArray(ySize)
        yBuffer.get(gray)
        imageProxy.close()

        val result = BarcodeDecoder.decodeGrayscale(gray, width, height)
        if (!result.isNullOrBlank()) {
            val now = System.currentTimeMillis()
            if (result != lastCode || (now - lastCodeTime) > throttleMs) {
                lastCode = result
                lastCodeTime = now
                runOnUiThread {
                    AppEventLog.log(AppEventLog.Category.SCAN, "barcode=$result")
                    if (SearchPrefs(this).beepOnScan) {
                        playScanBeep()
                    }
                    // Return to home and open search from there (closes scanner).
                    startActivity(
                        Intent(this, HomeActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                            )
                            putExtra(HomeActivity.EXTRA_POST_SCAN_BARCODE, result)
                        },
                    )
                    finish()
                }
            }
        }
    }

    private fun playScanBeep() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { toneGen.release() },
                200
            )
        } catch (_: Exception) { }
    }

    companion object {
        private const val TAG = "BarcodeScanner"
        private const val REQUEST_CAMERA = 1001
    }
}
