package axis.act.routine

/** What starts a routine (spec §F11). */
sealed interface RoutineTrigger {
    val label: String

    data class Time(val hour: Int, val minute: Int, val days: Set<Int> = (1..7).toSet()) : RoutineTrigger {
        override val label: String
            get() = "%02d:%02d · %s".format(
                hour,
                minute,
                when (days.size) {
                    7 -> "daily"
                    5 -> "weekdays"
                    2 -> "weekend"
                    else -> days.sorted().joinToString(",")
                }
            )
    }

    data class BatteryBelow(val pct: Int) : RoutineTrigger {
        override val label: String get() = "battery < $pct%"
    }

    data class BatteryAbove(val pct: Int) : RoutineTrigger {
        override val label: String get() = "battery > $pct%"
    }

    data object Charging : RoutineTrigger {
        override val label: String get() = "on charger"
    }

    data class WifiConnected(val ssid: String? = null) : RoutineTrigger {
        override val label: String get() = "wi-fi" + (ssid?.let { " · $it" } ?: " on")
    }

    data class AppLaunched(val pkg: String) : RoutineTrigger {
        override val label: String get() = "app: $pkg"
    }

    data class NotificationFrom(val pkg: String) : RoutineTrigger {
        override val label: String get() = "notify: $pkg"
    }

    data object Manual : RoutineTrigger {
        override val label: String get() = "manual"
    }
}

/** What a routine does when it fires (spec §F11). */
sealed interface RoutineAction {
    val label: String

    data class SetRinger(val mode: Int) : RoutineAction {
        override val label: String
            get() = "ringer → " + when (mode) {
                0 -> "silent"
                1 -> "vibrate"
                else -> "normal"
            }
    }

    data class SetDnd(val on: Boolean) : RoutineAction {
        override val label: String get() = "DND ${if (on) "on" else "off"}"
    }

    data class SetBrightness(val pct: Int) : RoutineAction {
        override val label: String get() = "brightness → $pct%"
    }

    data class SetRotationLocked(val locked: Boolean) : RoutineAction {
        override val label: String get() = "rotation ${if (locked) "lock" else "auto"}"
    }

    data class LaunchApp(val pkg: String) : RoutineAction {
        override val label: String get() = "launch $pkg"
    }

    data class OpenSettings(val page: String) : RoutineAction {
        override val label: String get() = "open $page settings"
    }

    data class Say(val text: String) : RoutineAction {
        override val label: String get() = "say: \"${text.take(30)}\""
    }

    data class Notify(val title: String, val text: String) : RoutineAction {
        override val label: String get() = "notify: $title"
    }

    data class RunRoutine(val routineId: String) : RoutineAction {
        override val label: String get() = "run routine $routineId"
    }
}

data class Routine(
    val id: String,
    val name: String,
    val icon: String = "bolt",
    val description: String = "",
    val enabled: Boolean = true,
    val triggers: List<RoutineTrigger> = emptyList(),
    val actions: List<RoutineAction> = emptyList(),
    val lastRunAt: Long = 0L,
    val lastResult: String? = null,
    /** Minimum seconds between two automatic firings (trigger debounce). */
    val cooldownSeconds: Int = 300,
    val builtIn: Boolean = false
) {
    val summary: String
        get() = triggers.joinToString(" / ") { it.label }.ifBlank { "manual" }
}

/** Mutable state a trigger evaluation needs — assembled by the engine. */
data class TriggerContext(
    val now: Long,
    val minuteOfDay: Int,
    val batteryPct: Int,
    val charging: Boolean,
    val wifi: Boolean,
    val foregroundApp: String?,
    val recentNotificationPkg: String?,
    val lastRunAt: Map<String, Long>
)
