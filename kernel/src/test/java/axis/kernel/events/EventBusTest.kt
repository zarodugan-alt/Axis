package axis.kernel.events

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventBusTest {

    @Test
    fun emit_deliversToCollector() = runTest {
        val bus = EventBus()
        val received = mutableListOf<AxisEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.events.collect { received += it }
        }
        bus.emit(AxisEvent.ForegroundAppChanged("com.example"))
        bus.emit(AxisEvent.ServiceHealth("accessibility", true))
        job.cancelAndJoin()
        assertEquals(2, received.size)
        assertTrue(received[0] is AxisEvent.ForegroundAppChanged)
        assertTrue(received[1] is AxisEvent.ServiceHealth)
    }

    @Test
    fun tryEmit_burstWithoutSubscribers_neverBlocksAndNeverFails() = runTest {
        val bus = EventBus()
        // 1000 events with zero collectors: DROP_OLDEST buffer must absorb
        // the burst without suspending or failing (system callbacks feeding
        // the bus must never be stalled by a slow/absent collector).
        repeat(1000) { i ->
            assertTrue(bus.tryEmit(AxisEvent.SensorContext("t", i.toFloat())))
        }
    }

    @Test
    fun events_replayZero_lateSubscriberSeesOnlyNewEvents() = runTest {
        val bus = EventBus()
        bus.emit(AxisEvent.ServiceHealth("stale", false)) // no subscribers yet
        val received = mutableListOf<AxisEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.events.collect { received += it }
        }
        bus.emit(AxisEvent.ServiceHealth("fresh", true))
        job.cancelAndJoin()
        assertEquals(1, received.size)
        assertEquals("fresh", (received[0] as AxisEvent.ServiceHealth).service)
    }
}
