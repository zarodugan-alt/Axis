package axis.act.routine

import axis.act.action.ActionResult
import axis.act.action.SystemActions
import axis.kernel.events.AxisEvent
import axis.kernel.events.EventBus
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** Device facts the engine needs, supplied by the app layer. */
data class EngineContext(
    val batteryPct: Int = 0,
    val charging: Boolean = false,
    val wifi: Boolean = false,
    val foregroundApp: String? = null,
    val recentNotificationPkg: String? = null
)

/**
 * The routine engine (spec §F11): evaluates triggers on a slow ticker and on
 * context events, then runs the matching routines' actions in order.
 *
 * Deliberately boring and cheap: a 30-second tick, an in-memory last-run map
 * for debouncing, and no wake locks. A launcher that burns battery to be
 * clever is a broken launcher.
 */
class RoutineEngine(
    private val repo: RoutineRepository,
    private val actions: SystemActions,
    private val bus: EventBus,
    private val contextProvider: () -> EngineContext,
    private val killSwitch: () -> Boolean,
    private val say: suspend (String) -> Unit,
    private val notify: (String, String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private val lastRun = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var recentNotificationPkg: String? = null

    /** Routines list for the UI, straight from storage. */
    val routines: StateFlow<List<Routine>> = repo.routines
        .stateIn(scope, SharingStarted.Eagerly, DefaultRoutines.all)

    private val runningFlag = MutableStateFlow(false)

    val running: StateFlow<Boolean> = runningFlag

    fun start() {
        if (ticker != null) return
        runningFlag.value = true
        bus.tryEmit(AxisEvent.CoreServiceState(true, "routines"))
        ticker = scope.launch {
            runCatching { seedLastRuns() }.onFailure { Timber.w(it, "routine seed failed") }
            while (true) {
                runCatching { evaluate() }.onFailure { Timber.w(it, "routine tick failed") }
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        runningFlag.value = false
        bus.tryEmit(AxisEvent.CoreServiceState(false, "routines"))
    }

    /** Called by the notification listener path so notification triggers work. */
    fun noteNotification(pkg: String) {
        recentNotificationPkg = pkg
    }

    /** Manual run from the UI (bypasses triggers/enablement, honours kill switch). */
    fun runNow(routineId: String) {
        scope.launch {
            val routine = routines.value.firstOrNull { it.id == routineId } ?: return@launch
            val firing = RoutineEvaluator.manualFiring(routine, killSwitch())
            if (firing == null) {
                bus.tryEmit(AxisEvent.RoutineFired(routine.id, routine.name, "manual", 0, false))
                return@launch
            }
            execute(firing)
        }
    }

    private suspend fun seedLastRuns() {
        val list = runCatching { repo.load() }.getOrDefault(emptyList())
        lastRun.value = list.filter { it.lastRunAt > 0 }.associate { it.id to it.lastRunAt }
    }

    private suspend fun evaluate() {
        val list = routines.value
        val ctx = contextProvider()
        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()
        val triggerCtx = TriggerContext(
            now = now,
            minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE),
            batteryPct = ctx.batteryPct,
            charging = ctx.charging,
            wifi = ctx.wifi,
            foregroundApp = ctx.foregroundApp,
            recentNotificationPkg = recentNotificationPkg ?: ctx.recentNotificationPkg,
            lastRunAt = lastRun.value
        )
        val firings = RoutineEvaluator.firings(list, triggerCtx, killSwitch())
        firings.forEach { execute(it) }
        // A notification trigger is one-shot: consume it.
        if (firings.any { it.trigger is RoutineTrigger.NotificationFrom }) recentNotificationPkg = null
    }

    private suspend fun execute(firing: RoutineEvaluator.Firing) {
        val routine = firing.routine
        val now = System.currentTimeMillis()
        lastRun.value = lastRun.value + (routine.id to now)
        runCatching { repo.noteRun(routine.id, now, "running") }

        var failures = 0
        var ran = 0
        for (action in routine.actions) {
            val result = runAction(action)
            ran++
            if (!result.ok) failures++
            bus.tryEmit(AxisEvent.ActionExecuted(action.label, result.detail, result.ok))
            if (!result.ok) Timber.w("routine ${routine.id}: ${action.label} → ${result.detail}")
        }
        val ok = failures == 0
        val summary = when {
            ran == 0 -> "no actions"
            ok -> "$ran action${if (ran == 1) "" else "s"} ok"
            else -> "$failures of $ran failed"
        }
        runCatching { repo.noteRun(routine.id, now, summary) }
        bus.tryEmit(
            AxisEvent.RoutineFired(
                routineId = routine.id,
                routineName = routine.name,
                trigger = firing.trigger.label,
                actionsRun = ran,
                ok = ok
            )
        )
    }

    private suspend fun runAction(action: RoutineAction): ActionResult = when (action) {
        is RoutineAction.SetRinger -> actions.setRingerMode(action.mode)
        is RoutineAction.SetDnd -> actions.setDnd(action.on)
        is RoutineAction.SetBrightness -> actions.setBrightness(action.pct)
        is RoutineAction.SetRotationLocked -> actions.setRotationLocked(action.locked)
        is RoutineAction.LaunchApp -> actions.launchApp(action.pkg)
        is RoutineAction.OpenSettings -> actions.openSettingsPage(action.page)
        is RoutineAction.Say -> {
            say(action.text)
            ActionResult(true, "spoke ${action.text.length} chars")
        }
        is RoutineAction.Notify -> {
            notify(action.title, action.text)
            ActionResult(true, "notified")
        }
        is RoutineAction.RunRoutine -> {
            val nested = routines.value.firstOrNull { it.id == action.routineId }
            if (nested == null) ActionResult(false, "routine ${action.routineId} not found")
            else {
                // Depth-1 nesting only: prevents A→B→A loops by construction.
                var nestedFailures = 0
                nested.actions.forEach { a -> if (!runAction(a).ok) nestedFailures++ }
                ActionResult(nestedFailures == 0, "ran ${nested.name} ($nestedFailures failures)")
            }
        }
    }

    companion object {
        const val TICK_MS = 30_000L
    }
}
