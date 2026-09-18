package axis.kernel.events

/**
 * Envelope for everything flowing on the event bus (spec §F1).
 *
 * UI collects via `collectAsStateWithLifecycle`; engines collect in the
 * CoreService scope (P2). Every event carries a wall-clock timestamp so
 * consumers can order, debounce and expire without extra bookkeeping.
 */
sealed interface AxisEvent {
    val ts: Long

    /** A notification was posted (or updated) by [pkg]. */
    data class NotificationPosted(
        val key: String,
        val pkg: String,
        val title: String?,
        val text: String?,
        val actions: Int,
        override val ts: Long = System.currentTimeMillis()
    ) : AxisEvent

    /** Foreground app changed (accessibility window events, P2). */
    data class ForegroundAppChanged(
        val pkg: String,
        override val ts: Long = System.currentTimeMillis()
    ) : AxisEvent

    /** Accessibility tree changed materially in [pkg] (P2). */
    data class ScreenContentChanged(
        val pkg: String,
        val nodeHash: Int,
        override val ts: Long = System.currentTimeMillis()
    ) : AxisEvent

    /** Sensor / context sample: "battery", "light", "motion", … (P2/P4). */
    data class SensorContext(
        val type: String,
        val value: Float,
        override val ts: Long = System.currentTimeMillis()
    ) : AxisEvent

    /** Agent / routine task made progress (P3/P4). */
    data class TaskProgress(
        val taskId: String,
        val step: Int,
        val total: Int,
        val status: String,
        override val ts: Long = System.currentTimeMillis()
    ) : AxisEvent

    /** A platform service changed liveness (watchdog, P2). */
    data class ServiceHealth(
        val service: String,
        val alive: Boolean,
        override val ts: Long = System.currentTimeMillis()
    ) : AxisEvent

    /** Installed-app inventory changed (drawer index refresh hint). */
    data class AppInventoryChanged(
        val reason: String,
        override val ts: Long = System.currentTimeMillis()
    ) : AxisEvent
}
