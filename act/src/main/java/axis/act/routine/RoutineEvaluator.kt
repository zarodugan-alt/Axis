package axis.act.routine

/**
 * Pure trigger evaluation for the routine engine (spec §F11). Keeping it
 * free of Android means the whole firing policy — time matching, battery
 * thresholds, app triggers, cooldowns, kill switch — is unit-testable.
 */
object RoutineEvaluator {

    /** A routine that should run now, and why. */
    data class Firing(val routine: Routine, val trigger: RoutineTrigger, val manual: Boolean = false)

    /**
     * Which routines fire for [ctx]. Rules:
     *  - a routine must be enabled and have at least one trigger,
     *  - time triggers match to the minute,
     *  - battery/wifi/charging triggers are edge-free (state-based) but
     *    debounced by [Routine.cooldownSeconds],
     *  - app/notification triggers are event-based and use a short debounce,
     *  - [killSwitch] suppresses everything automatic (manual runs still work).
     */
    fun firings(
        routines: List<Routine>,
        ctx: TriggerContext,
        killSwitch: Boolean = false
    ): List<Firing> {
        val out = mutableListOf<Firing>()
        routines.filter { it.enabled }.forEach { routine ->
            if (routine.triggers.any { it is RoutineTrigger.Manual } && routine.triggers.size == 1) return@forEach
            val last = ctx.lastRunAt[routine.id] ?: 0L
            val ageSeconds = (ctx.now - last) / 1000
            val cooled = ageSeconds >= routine.cooldownSeconds

            for (trigger in routine.triggers) {
                val matches = when (trigger) {
                    is RoutineTrigger.Time -> {
                        val minutes = trigger.hour * 60 + trigger.minute
                        // Fire within the matching minute, once, thanks to cooldown.
                        minutes == ctx.minuteOfDay &&
                            (trigger.days.isEmpty() || dayOfWeek(ctx.now) in trigger.days) &&
                            ageSeconds >= 60
                    }
                    is RoutineTrigger.BatteryBelow -> ctx.batteryPct < trigger.pct && !ctx.charging && cooled
                    is RoutineTrigger.BatteryAbove -> ctx.batteryPct > trigger.pct && cooled
                    RoutineTrigger.Charging -> ctx.charging && cooled
                    is RoutineTrigger.WifiConnected -> ctx.wifi && cooled
                    is RoutineTrigger.AppLaunched -> ctx.foregroundApp == trigger.pkg && ageSeconds >= 30
                    is RoutineTrigger.NotificationFrom -> ctx.recentNotificationPkg == trigger.pkg && ageSeconds >= 30
                    RoutineTrigger.Manual -> false
                }
                if (!matches) continue
                if (killSwitch && trigger !is RoutineTrigger.Manual) continue
                out += Firing(routine, trigger)
                break // one firing per routine per evaluation
            }
        }
        return out
    }

    /** Manual runs bypass enablement and triggers but honour the kill switch. */
    fun manualFiring(routine: Routine, killSwitch: Boolean): Firing? =
        if (killSwitch) null
        else Firing(routine, RoutineTrigger.Manual, manual = true)

    private fun dayOfWeek(nowMillis: Long): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        return cal.get(java.util.Calendar.DAY_OF_WEEK)
    }
}

/** Default gallery shown on first launch (spec §S9 routine list). */
object DefaultRoutines {

    val all: List<Routine> = listOf(
        Routine(
            id = "good_night",
            name = "Good Night",
            icon = "moon",
            description = "Silence everything, dim the screen, lock rotation.",
            enabled = false,
            triggers = listOf(RoutineTrigger.Time(23, 0)),
            actions = listOf(
                RoutineAction.SetDnd(true),
                RoutineAction.SetRinger(0),
                RoutineAction.SetBrightness(20),
                RoutineAction.SetRotationLocked(true),
                RoutineAction.Say("Good night. Everything is quiet.")
            ),
            builtIn = true
        ),
        Routine(
            id = "good_morning",
            name = "Good Morning",
            icon = "sun",
            description = "Back to normal, brighter screen, alarm volume up.",
            enabled = false,
            triggers = listOf(RoutineTrigger.Time(7, 0)),
            actions = listOf(
                RoutineAction.SetDnd(false),
                RoutineAction.SetRinger(2),
                RoutineAction.SetBrightness(70)
            ),
            builtIn = true
        ),
        Routine(
            id = "focus",
            name = "Focus",
            icon = "target",
            description = "Do not disturb while you work, rotation stays locked.",
            enabled = false,
            triggers = listOf(RoutineTrigger.Manual),
            actions = listOf(
                RoutineAction.SetDnd(true),
                RoutineAction.SetRinger(1),
                RoutineAction.Notify("Focus", "DND is on — notifications are held in AXIS.")
            ),
            builtIn = true
        ),
        Routine(
            id = "drive",
            name = "Driving",
            icon = "car",
            description = "Loud ringer, DND off, rotation locked for the mount.",
            enabled = false,
            triggers = listOf(RoutineTrigger.Manual),
            actions = listOf(
                RoutineAction.SetDnd(false),
                RoutineAction.SetRinger(2),
                RoutineAction.SetRotationLocked(true)
            ),
            builtIn = true
        ),
        Routine(
            id = "battery_saver",
            name = "Low Battery",
            icon = "battery",
            description = "When the battery drops below 20%, dim and silence.",
            enabled = false,
            triggers = listOf(RoutineTrigger.BatteryBelow(20)),
            actions = listOf(
                RoutineAction.SetBrightness(30),
                RoutineAction.SetRinger(1)
            ),
            builtIn = true
        )
    )
}
