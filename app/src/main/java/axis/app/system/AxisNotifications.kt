package axis.app.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import axis.app.MainActivity

/**
 * AXIS's own notifications: the CoreService foreground strip, routine
 * confirmations, and agent step updates. Two channels keep the system
 * settings tidy: `core` (low, always-on status) and `agent` (default).
 */
class AxisNotifications(private val context: Context) {

    private val manager: NotificationManager?
        get() = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        val nm = manager ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CORE,
                "Core service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps routines and context tracking alive"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AGENT,
                "Agent activity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Routine results and agent steps" }
        )
    }

    /** Persistent strip while the routine engine runs. */
    fun coreNotification(modeText: String): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, CHANNEL_CORE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("AXIS core active")
            .setContentText(modeText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .build()
    }

    fun postAgent(title: String, text: String) {
        ensureChannels()
        manager?.notify(
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            NotificationCompat.Builder(context, CHANNEL_AGENT)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(text.take(140))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build()
        )
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_CORE = "axis_core"
        const val CHANNEL_AGENT = "axis_agent"
    }
}
