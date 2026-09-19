package axis.act.routine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import axis.kernel.json.MiniJson
import axis.kernel.json.bool
import axis.kernel.json.int
import axis.kernel.json.list
import axis.kernel.json.long
import axis.kernel.json.obj
import axis.kernel.json.objList
import axis.kernel.json.str
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.routineDataStore by preferencesDataStore(name = "axis_routines")

/**
 * Routine persistence (spec §F11): the whole routine list is one JSON
 * document in DataStore. Small (tens of routines), rewritten atomically on
 * each edit, and shared with Flow Studio — no database needed.
 */
class RoutineRepository(private val context: Context) {

    private val key = stringPreferencesKey("routines_json")

    val routines: Flow<List<Routine>> = context.routineDataStore.data
        .map { decode(it[key]) }

    suspend fun load(): List<Routine> = decode(context.routineDataStore.data.first()[key])

    /** Seeds the gallery once; existing user routines are never overwritten. */
    suspend fun seedDefaultsOnce() {
        val prefs = context.routineDataStore.data.first()
        if (prefs[key] != null) return
        save(DefaultRoutines.all)
    }

    suspend fun save(list: List<Routine>) {
        context.routineDataStore.edit { it[key] = encode(list) }
    }

    suspend fun upsert(routine: Routine) {
        val list = load().toMutableList()
        val index = list.indexOfFirst { it.id == routine.id }
        if (index >= 0) list[index] = routine else list.add(routine)
        save(list)
    }

    suspend fun delete(id: String) {
        save(load().filterNot { it.id == id })
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        save(load().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    suspend fun noteRun(id: String, at: Long, result: String) {
        save(load().map { if (it.id == id) it.copy(lastRunAt = at, lastResult = result) else it })
    }

    // ------------------------------------------------------------ codec

    private fun encode(list: List<Routine>): String = MiniJson.encode(list.map { r ->
        linkedMapOf<String, Any?>(
            "id" to r.id,
            "name" to r.name,
            "icon" to r.icon,
            "desc" to r.description,
            "on" to r.enabled,
            "cooldown" to r.cooldownSeconds,
            "lastRun" to r.lastRunAt,
            "lastResult" to r.lastResult,
            "triggers" to r.triggers.map(::encodeTrigger),
            "actions" to r.actions.map(::encodeAction)
        )
    })

    private fun encodeTrigger(t: RoutineTrigger): Map<String, Any?> = when (t) {
        is RoutineTrigger.Time -> linkedMapOf(
            "type" to "time", "hour" to t.hour, "minute" to t.minute,
            "days" to t.days.sorted()
        )
        is RoutineTrigger.BatteryBelow -> linkedMapOf("type" to "battery_below", "pct" to t.pct)
        is RoutineTrigger.BatteryAbove -> linkedMapOf("type" to "battery_above", "pct" to t.pct)
        RoutineTrigger.Charging -> linkedMapOf("type" to "charging")
        is RoutineTrigger.WifiConnected -> linkedMapOf("type" to "wifi", "ssid" to t.ssid)
        is RoutineTrigger.AppLaunched -> linkedMapOf("type" to "app", "pkg" to t.pkg)
        is RoutineTrigger.NotificationFrom -> linkedMapOf("type" to "notif", "pkg" to t.pkg)
        RoutineTrigger.Manual -> linkedMapOf("type" to "manual")
    }

    private fun encodeAction(a: RoutineAction): Map<String, Any?> = when (a) {
        is RoutineAction.SetRinger -> linkedMapOf("type" to "ringer", "mode" to a.mode)
        is RoutineAction.SetDnd -> linkedMapOf("type" to "dnd", "on" to a.on)
        is RoutineAction.SetBrightness -> linkedMapOf("type" to "brightness", "pct" to a.pct)
        is RoutineAction.SetRotationLocked -> linkedMapOf("type" to "rotation", "locked" to a.locked)
        is RoutineAction.LaunchApp -> linkedMapOf("type" to "launch", "pkg" to a.pkg)
        is RoutineAction.OpenSettings -> linkedMapOf("type" to "settings", "page" to a.page)
        is RoutineAction.Say -> linkedMapOf("type" to "say", "text" to a.text)
        is RoutineAction.Notify -> linkedMapOf("type" to "notify", "title" to a.title, "text" to a.text)
        is RoutineAction.RunRoutine -> linkedMapOf("type" to "run", "id" to a.routineId)
    }

    private fun decode(json: String?): List<Routine> {
        val root = MiniJson.listOrNull(json) ?: return DefaultRoutines.all
        return root.filterIsInstance<Map<String, Any?>>().mapNotNull { doc ->
            val id = doc.str("id") ?: return@mapNotNull null
            Routine(
                id = id,
                name = doc.str("name") ?: id,
                icon = doc.str("icon") ?: "bolt",
                description = doc.str("desc").orEmpty(),
                enabled = doc.bool("on") ?: false,
                cooldownSeconds = doc.int("cooldown") ?: 300,
                lastRunAt = doc.long("lastRun") ?: 0L,
                lastResult = doc.str("lastResult"),
                triggers = doc.objList("triggers").mapNotNull(::decodeTrigger),
                actions = doc.objList("actions").mapNotNull(::decodeAction),
                builtIn = DefaultRoutines.all.any { it.id == id }
            )
        }.ifEmpty { DefaultRoutines.all }
    }

    private fun decodeTrigger(doc: Map<String, Any?>): RoutineTrigger? = when (doc.str("type")) {
        "time" -> RoutineTrigger.Time(
            doc.int("hour") ?: 7,
            doc.int("minute") ?: 0,
            doc.list("days").filterIsInstance<Number>().map { it.toInt() }.toSet().ifEmpty { (1..7).toSet() }
        )
        "battery_below" -> RoutineTrigger.BatteryBelow(doc.int("pct") ?: 20)
        "battery_above" -> RoutineTrigger.BatteryAbove(doc.int("pct") ?: 80)
        "charging" -> RoutineTrigger.Charging
        "wifi" -> RoutineTrigger.WifiConnected(doc.str("ssid"))
        "app" -> doc.str("pkg")?.let { RoutineTrigger.AppLaunched(it) }
        "notif" -> doc.str("pkg")?.let { RoutineTrigger.NotificationFrom(it) }
        "manual" -> RoutineTrigger.Manual
        else -> null
    }

    private fun decodeAction(doc: Map<String, Any?>): RoutineAction? = when (doc.str("type")) {
        "ringer" -> RoutineAction.SetRinger(doc.int("mode") ?: 2)
        "dnd" -> RoutineAction.SetDnd(doc.bool("on") ?: false)
        "brightness" -> RoutineAction.SetBrightness(doc.int("pct") ?: 50)
        "rotation" -> RoutineAction.SetRotationLocked(doc.bool("locked") ?: false)
        "launch" -> doc.str("pkg")?.let { RoutineAction.LaunchApp(it) }
        "settings" -> doc.str("page")?.let { RoutineAction.OpenSettings(it) }
        "say" -> doc.str("text")?.let { RoutineAction.Say(it) }
        "notify" -> RoutineAction.Notify(doc.str("title") ?: "AXIS", doc.str("text").orEmpty())
        "run" -> doc.str("id")?.let { RoutineAction.RunRoutine(it) }
        else -> null
    }

    /** Stable id for routines created in the UI. */
    fun newId(): String = "r" + System.currentTimeMillis().toString(36)
}
