package com.turntable.barcodescanner.redacted

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.turntable.barcodescanner.R

/**
 * Shows a system notification with a tap action to open the Redacted site (inbox/dashboard).
 */
object RedactedSiteNotificationHelper {

    private const val CHANNEL_ID = "redacted_site_alerts"
    private const val NOTIFICATION_ID = 94021

    /** Default landing page when opening from the notification. */
    const val DEFAULT_SITE_URL = "https://redacted.sh/index.php"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_redacted_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notif_channel_redacted_description)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    private fun openSitePendingIntent(context: Context, url: String): PendingIntent {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /**
     * Posts a notification if permission allows; content lists [reasons] from the API.
     */
    fun showSiteAlerts(context: Context, reasons: List<String>, url: String = DEFAULT_SITE_URL) {
        if (reasons.isEmpty()) return
        ensureChannel(context)
        val text = reasons.joinToString(", ")
        val contentPi = openSitePendingIntent(context, url)
        val openLabel = context.getString(R.string.notif_redacted_open_site)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(context.getString(R.string.notif_redacted_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                android.R.drawable.ic_menu_view,
                openLabel,
                contentPi,
            )
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}
