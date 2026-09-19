package axis.kernel.events

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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
        runCurrent() // flush delivery before cancelling the collector
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
        // Prime: emit one event and fully consume it with a one-shot
        // collector, so nothing lingers in the 256-deep buffer for the
        // late subscriber. `await` proves consumption — no races.
        val prime = async(UnconfinedTestDispatcher(testScheduler)) { bus.events.first() }
        bus.emit(AxisEvent.ServiceHealth("stale", false))
        assertEquals("stale", (prime.await() as AxisEvent.ServiceHealth).service)
        runCurrent()
        val received = mutableListOf<AxisEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.events.collect { received += it }
        }
        bus.emit(AxisEvent.ServiceHealth("fresh", true))
        runCurrent() // flush delivery before cancelling the collector
        job.cancelAndJoin()
        assertEquals(1, received.size)
        assertEquals("fresh", (received[0] as AxisEvent.ServiceHealth).service)
    }
}
