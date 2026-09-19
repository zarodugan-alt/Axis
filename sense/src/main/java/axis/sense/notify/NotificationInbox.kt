package axis.sense.notify

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import axis.kernel.events.AxisEvent
import axis.kernel.events.EventBus

/**
 * In-memory notification store for the Notification Center (spec §S5).
 *
 * Bounded at [capacity] records, newest first. The service feeds it, the UI
 * reads it, and pruning happens on every insert — a launcher must never grow
 * without bound while the user is not looking.
 */
class NotificationInbox(
    private val bus: EventBus,
    private val capacity: Int = 200
) {
    private val lock = Any()
    private val buffer = CopyOnWriteArrayList<NotificationRecord>()
    private val _records = MutableStateFlow<List<NotificationRecord>>(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Newest first. */
    val records: StateFlow<List<NotificationRecord>> = _records

    val unreadCount: StateFlow<Int> = _records
        .map { list -> list.count { !it.isOngoing } }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 0)

    fun post(record: NotificationRecord) {
        synchronized(lock) {
            buffer.removeAll { it.key == record.key }
            buffer.add(0, record)
            while (buffer.size > capacity) buffer.removeAt(buffer.size - 1)
            _records.value = buffer.toList()
        }
        bus.tryEmit(
            AxisEvent.NotificationPosted(
                key = record.key,
                pkg = record.pkg,
                title = record.title,
                text = record.text,
                actions = 0
            )
        )
    }

    fun remove(key: String, pkg: String) {
        synchronized(lock) {
            buffer.removeAll { it.key == key }
            _records.value = buffer.toList()
        }
        bus.tryEmit(AxisEvent.NotificationRemoved(key, pkg))
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _records.value = emptyList()
        }
    }

    fun removeAllFrom(pkg: String) {
        synchronized(lock) {
            buffer.removeAll { it.pkg == pkg }
            _records.value = buffer.toList()
        }
    }

    /** One representative per package, for the home "which apps are talking" view. */
    fun byPackage(): Map<String, Int> =
        buffer.groupBy { it.pkg }.mapValues { (_, list) -> list.size }
}

/** Notification-listener access helpers (spec §F2 capability checks). */
object NotificationAccess {

    fun isGranted(context: Context): Boolean = try {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        flat.split(':').any { it.contains(context.packageName) }
    } catch (_: Exception) {
        false
    }

    fun appLabel(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    } catch (_: Exception) {
        pkg
    }
}
