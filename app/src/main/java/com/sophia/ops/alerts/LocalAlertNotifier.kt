package com.sophia.ops.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sophia.ops.data.IncidentRecord

class LocalAlertNotifier(private val context: Context) {
    fun notifyIncident(incident: IncidentRecord): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        createChannel()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("S0PHIA OPS · ${incident.severity.name} local alert")
            .setContentText(incident.headline.take(160))
            .setStyle(NotificationCompat.BigTextStyle().bigText("${incident.headline}\n${incident.recommendedAction}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(incident.id.hashCode(), notification)
        return true
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "S0PHIA local alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Focused on-device alerts generated during active S0PHIA OPS scans."
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "sophia_local_alerts"
    }
}
