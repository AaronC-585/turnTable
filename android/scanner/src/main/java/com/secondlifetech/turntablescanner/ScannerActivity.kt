package com.secondlifetech.turntablescanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.ImageFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.secondlifetech.turntablescanner.databinding.ActivityScannerBinding

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private var lastCode: String? = null
    private var lastCodeTime = 0L
    private val throttleMs = 1500L
    private var camera: Camera? = null
    private var torchEnabled = false
    private var resolving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonFlashlight.setOnClickListener { toggleFlashlight() }

        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            return
        }
        startCamera()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
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
            bindPreview(cameraProviderFuture.get())
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

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
            torchEnabled = false
            runOnUiThread { updateFlashlightAppearance() }
        } catch (e: Exception) {
            Log.e(TAG, "Bind failed", e)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) updateFlashlightAppearance()
    }

    override fun onPause() {
        camera?.cameraControl?.enableTorch(false)
        torchEnabled = false
        super.onPause()
    }

    private fun updateFlashlightAppearance() {
        val c = camera
        val hasFlash = c?.cameraInfo?.hasFlashUnit() == true
        binding.buttonFlashlight.isEnabled = hasFlash
        binding.buttonFlashlight.alpha = if (hasFlash) 1f else 0.45f
        val colorRes = when {
            !hasFlash -> R.color.app_text_on_button
            torchEnabled -> R.color.app_accent
            else -> R.color.app_text_on_button
        }
        binding.buttonFlashlight.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, colorRes),
        )
        binding.buttonFlashlight.contentDescription = when {
            !hasFlash -> getString(R.string.flashlight_unavailable)
            torchEnabled -> getString(R.string.flashlight_cd_on)
            else -> getString(R.string.flashlight_cd_off)
        }
    }

    private fun toggleFlashlight() {
        val c = camera ?: return
        if (!c.cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, R.string.flashlight_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        torchEnabled = !torchEnabled
        c.cameraControl.enableTorch(torchEnabled)
        updateFlashlightAppearance()
    }

    private fun analyze(imageProxy: ImageProxy) {
        if (resolving) {
            imageProxy.close()
            return
        }
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
        if (result.isNullOrBlank()) return
        val now = System.currentTimeMillis()
        if (result == lastCode && (now - lastCodeTime) <= throttleMs) return
        lastCode = result
        lastCodeTime = now
        runOnUiThread { onBarcodeScanned(result) }
    }

    private fun onBarcodeScanned(barcode: String) {
        if (resolving) return
        resolving = true
        if (SharedTurnTablePrefs.beepOnScan(this)) playScanBeep()
        binding.progressResolve.visibility = View.VISIBLE
        binding.textStatus.visibility = View.VISIBLE
        binding.textStatus.setText(R.string.resolving_release)

        Thread {
            val key = SharedTurnTablePrefs.redactedApiKey(this)
            val hit = if (!key.isNullOrBlank()) {
                ScannerRedactedAssist.firstHit(key, barcode)
            } else {
                null
            }
            runOnUiThread {
                binding.textStatus.setText(R.string.opening_turntable)
                openTurnTable(barcode, hit?.artist, hit?.album)
            }
        }.start()
    }

    private fun openTurnTable(barcode: String, artist: String?, album: String?) {
        val uri = ScannerRedactedAssist.turnTableSearchUri(barcode, artist, album)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            setPackage(TURNTABLE_PACKAGE)
        }
        try {
            startActivity(intent)
            finish()
        } catch (_: Exception) {
            // Fallback without setPackage
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                finish()
            } catch (_: Exception) {
                resolving = false
                binding.progressResolve.visibility = View.GONE
                binding.textStatus.visibility = View.GONE
                Toast.makeText(this, R.string.turntable_missing, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun playScanBeep() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { toneGen.release() },
                200,
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "TurnTableScanner"
        private const val REQUEST_CAMERA = 1001
        const val TURNTABLE_PACKAGE = "com.secondlifetech.turntable"
    }
}
