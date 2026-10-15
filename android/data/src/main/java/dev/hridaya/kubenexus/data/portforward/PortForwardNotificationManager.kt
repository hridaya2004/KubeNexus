package dev.hridaya.kubenexus.data.portforward

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hridaya.kubenexus.data.R
import dev.hridaya.kubenexus.domain.model.ActivePortForwardSession
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the ongoing system notification that reflects all active Kubernetes
 * port-forwarding sessions, ensuring visibility and user control even when
 * the application transitions into the background.
 */
@Singleton
class PortForwardNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Port Forwarding",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows active Kubernetes port-forwarding sessions"
            setShowBadge(true)
        }
        val sysManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        sysManager?.createNotificationChannel(channel)
    }

    /**
     * Builds the [Notification] representing current active port forwards.
     */
    fun buildNotification(activeSessions: List<ActivePortForwardSession>): Notification {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } ?: Intent()

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, PortForwardStopReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val summaryText = if (activeSessions.isEmpty()) {
            "Port forwarding active"
        } else if (activeSessions.size == 1) {
            val session = activeSessions.first()
            "127.0.0.1:${session.localPort} \u2192 ${session.targetName}:${session.remotePort}"
        } else {
            "${activeSessions.size} active sessions (${activeSessions.joinToString { "${it.localPort}" }})"
        }

        val bigText = buildString {
            if (activeSessions.isEmpty()) {
                append("Waiting for port-forward connections...")
            } else {
                activeSessions.forEachIndexed { index, s ->
                    if (index > 0) append("\n")
                    append("• 127.0.0.1:${s.localPort} \u2192 ${s.targetName}:${s.remotePort} (${s.namespace})")
                }
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            // Status bar icons are drawn as alpha masks; the full-colour launcher icon
            // rendered as a solid blob.
            .setSmallIcon(R.drawable.ic_stat_port_forward)
            .setContentTitle("Port Forwarding Active (${activeSessions.size})")
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop All",
                stopIntent,
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Updates the notification with current active sessions, or dismisses it
     * if no active forwards remain.
     */
    fun updateNotification(activeSessions: List<ActivePortForwardSession>) {
        if (!notificationManager.areNotificationsEnabled()) return

        if (activeSessions.isEmpty()) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }

        val notification = buildNotification(activeSessions)
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission denied by user
        }
    }

    fun dismissNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * Explains why tunnels closed on their own: Android 15+ gives dataSync foreground
     * services 6 hours in every 24, and the budget resets once the app is opened again.
     */
    fun showTimeLimitReached() {
        if (!notificationManager.areNotificationsEnabled()) return
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        val contentIntent = PendingIntent.getActivity(
            context,
            2,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "Android limits background port forwarding to 6 hours a day, so KubeNexus " +
                "closed your tunnels. Open the app to start them again."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_port_forward)
            .setContentTitle("Port forwarding stopped")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            notificationManager.notify(TIME_LIMIT_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission denied by user
        }
    }

    companion object {
        const val CHANNEL_ID = "kubenexus_port_forward"
        const val NOTIFICATION_ID = 42001
        const val TIME_LIMIT_NOTIFICATION_ID = 42002
    }
}
