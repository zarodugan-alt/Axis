package axis.sense.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import axis.kernel.di.locate
import axis.kernel.events.AxisEvent
import axis.kernel.events.EventBus
import timber.log.Timber

/**
 * Notification listener (spec §F8). Captures every posted/removed
 * notification into [NotificationInbox] and republishes it on the event bus
 * for the notification center and routine triggers.
 *
 * The service is declared in the manifest and only starts when the user
 * grants Notification Access in system settings — AXIS never prompts from
 * code and never silently re-requests.
 */
class AxisNotificationListenerService : NotificationListenerService() {

    private val inbox: NotificationInbox? get() = locate()
    private val bus: EventBus? get() = locate()

    override fun onListenerConnected() {
        super.onListenerConnected()
        bus?.tryEmit(AxisEvent.ServiceHealth("notification_listener", true))
        // Backfill whatever is already on screen when the listener attaches.
        runCatching {
            activeNotifications?.forEach { capture(it) }
        }.onFailure { Timber.w(it, "backfill failed") }
    }

    override fun onListenerDisconnected() {
        bus?.tryEmit(AxisEvent.ServiceHealth("notification_listener", false))
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { capture(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        inbox?.remove(sbn.key ?: "${sbn.packageName}:${sbn.id}", sbn.packageName)
    }

    private fun capture(sbn: StatusBarNotification) {
        runCatching {
            val extras = sbn.notification?.extras
            val record = NotificationRecord(
                key = sbn.key ?: "${sbn.packageName}:${sbn.id}",
                pkg = sbn.packageName,
                appLabel = NotificationAccess.appLabel(this, sbn.packageName),
                title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                channelId = sbn.notification?.channelId,
                category = sbn.notification?.category,
                postedAt = sbn.postTime,
                isOngoing = sbn.isOngoing,
                isClearable = sbn.isClearable,
                isGroupSummary = (sbn.notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY != 0
            )
            inbox?.post(record)
        }.onFailure { Timber.w(it, "capture failed") }
    }
}
