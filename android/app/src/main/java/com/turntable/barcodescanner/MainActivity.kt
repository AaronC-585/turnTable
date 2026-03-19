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
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.turntable.barcodescanner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastCode: String? = null
    private var lastCodeTime = 0L
    private val throttleMs = 1500L
    private var camera: Camera? = null
    private var torchEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topToolbar.inflateMenu(R.menu.main_toolbar_menu)
        binding.topToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.action_history -> {
                    startActivity(Intent(this, SearchHistoryActivity::class.java))
                    true
                }
                R.id.action_stats -> {
                    startActivity(Intent(this, UserStatsActivity::class.java))
                    true
                }
                R.id.action_flashlight -> {
                    toggleFlashlight()
                    true
                }
                else -> false
            }
        }

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
            camera = provider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            torchEnabled = false
        } catch (e: Exception) {
            Log.e(TAG, "Bind failed", e)
        }
    }

    private fun toggleFlashlight() {
        val c = camera ?: return
        val hasFlash = c.cameraInfo.hasFlashUnit()
        if (!hasFlash) {
            Log.w(TAG, "Flash unit not available on this camera")
            return
        }
        torchEnabled = !torchEnabled
        c.cameraControl.enableTorch(torchEnabled)
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
                    if (SearchPrefs(this).beepOnScan) {
                        playScanBeep()
                    }
                    startActivity(
                        Intent(this, SearchActivity::class.java)
                            .putExtra(SearchActivity.EXTRA_BARCODE, result)
                    )
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
