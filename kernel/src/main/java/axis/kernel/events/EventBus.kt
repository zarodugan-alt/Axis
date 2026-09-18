package axis.kernel.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Single process-wide event bus (spec §F1).
 *
 * Buffer of 256 with DROP_OLDEST: producers (accessibility, listeners) must
 * never suspend the system callbacks that feed them, and a slow collector
 * must never stall producers. Provided as a `@Singleton` by `:app`.
 */
class EventBus {
    private val _events = MutableSharedFlow<AxisEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AxisEvent> = _events.asSharedFlow()

    suspend fun emit(event: AxisEvent) = _events.emit(event)

    /** Non-suspending emit for system callbacks. Returns false if dropped. */
    fun tryEmit(event: AxisEvent): Boolean = _events.tryEmit(event)
}
