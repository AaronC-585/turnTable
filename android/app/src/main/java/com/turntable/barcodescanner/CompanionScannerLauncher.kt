package com.turntable.barcodescanner

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

/** Opens the turnTable Scanner companion via deep link. */
object CompanionScannerLauncher {
    const val SCANNER_PACKAGE = "com.secondlifetech.turntablescanner"
    private val SCAN_URI: Uri = Uri.parse("turntablescanner://scan")

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SCANNER_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun open(context: Context) {
        if (!isInstalled(context)) {
            Toast.makeText(context, R.string.scanner_companion_missing, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, SCAN_URI).apply {
            setPackage(SCANNER_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.scanner_companion_missing, Toast.LENGTH_LONG).show()
        }
    }
}
