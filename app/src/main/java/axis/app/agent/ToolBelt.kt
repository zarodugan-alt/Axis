package axis.app.agent

import axis.act.action.SystemActions
import axis.agent.tool.AgentTool
import axis.agent.tool.ToolDescriptor
import axis.agent.tool.ToolOutcome
import axis.app.data.SettingsStore
import axis.app.drawer.AppRepository
import axis.app.speech.TtsSpeaker
import axis.act.routine.RoutineEngine
import axis.kernel.agent.RiskLevel
import axis.kernel.agent.ToolParam
import axis.sense.DeviceStateRepository
import axis.sense.notify.NotificationInbox
import kotlinx.coroutines.flow.first

/**
 * The tools the agent may call (spec §F12). Every tool is a thin wrapper over
 * a capability that already exists elsewhere in AXIS, and every one declares
 * its risk so [axis.safety.PolicyEngine] can gate it. Nothing here executes
 * on its own — the loop calls, the gate decides.
 */
class ToolBelt(
    private val apps: AppRepository,
    private val device: DeviceStateRepository,
    private val systemActions: SystemActions,
    private val inbox: NotificationInbox,
    private val routines: RoutineEngine,
    private val speaker: TtsSpeaker,
    private val settings: SettingsStore
) {

    fun all(): List<AgentTool> = listOf(
        launchApp(),
        openUrl(),
        webSearch(),
        deviceStatus(),
        readNotifications(),
        clearNotifications(),
        setFlashlight(),
        setDnd(),
        setRinger(),
        setBrightness(),
        setRotationLock(),
        openSettingsPage(),
        runRoutine(),
        listRoutines(),
        say()
    )

    // ------------------------------------------------------------- apps

    private fun launchApp() = tool(
        name = "launch_app",
        description = "Open an installed app by name or package. Search matches loosely.",
        risk = RiskLevel.LOW,
        params = listOf(ToolParam("app", "string", "App name or package id, e.g. 'whatsapp'"))
    ) { args ->
        val query = argString(args, "app") ?: return@tool ToolOutcome.fail("missing 'app'")
        val list = apps.visibleApps.value
        val byPackage = list.firstOrNull { it.packageName.equals(query, ignoreCase = true) }
        val match = byPackage
            ?: axis.kernel.search.FuzzySearch.filter(query, list) { it.label }.firstOrNull()
            ?: list.firstOrNull { it.label.contains(query, ignoreCase = true) }
            ?: return@tool ToolOutcome.fail("No app matches \"$query\".")
        val ok = apps.launch(match.packageName)
        if (ok) ToolOutcome.ok("Launched ${match.label}") else ToolOutcome.fail("Could not launch ${match.label}")
    }

    private fun openUrl() = tool(
        name = "open_url",
        description = "Open a web link in the default browser.",
        risk = RiskLevel.LOW,
        params = listOf(ToolParam("url", "string", "https URL or bare domain"))
    ) { args ->
        val url = argString(args, "url") ?: return@tool ToolOutcome.fail("missing 'url'")
        if (url.startsWith("http://") && url.contains("bank")) {
            return@tool ToolOutcome.fail("Refusing to open insecure banking URLs")
        }
        val r = systemActions.openUrl(url)
        if (r.ok) ToolOutcome.ok(r.detail) else ToolOutcome.fail(r.detail)
    }

    private fun webSearch() = tool(
        name = "web_search",
        description = "Run a web search in the browser.",
        risk = RiskLevel.TRIVIAL,
        params = listOf(ToolParam("query", "string", "Search terms"))
    ) { args ->
        val q = argString(args, "query") ?: return@tool ToolOutcome.fail("missing 'query'")
        apps.webSearch(q)
        ToolOutcome.ok("Searched the web for \"$q\"")
    }

    // --------------------------------------------------------- context

    private fun deviceStatus() = tool(
        name = "device_status",
        description = "Read battery, charging, network, memory, storage and screen state.",
        risk = RiskLevel.TRIVIAL,
        params = emptyList()
    ) {
        val s = device.sample()
        ToolOutcome.ok(
            "battery ${s.batteryPct}%${if (s.charging) " (charging)" else ""}, " +
                "${s.batteryTempC}°C, network ${s.network}, " +
                "ram ${s.ramUsedPct}% used, storage ${s.storageUsedPct}% used " +
                "(${"%.1f".format(s.storageFreeGb)} GB free), screen ${if (s.screenOn) "on" else "off"}, " +
                "torch ${if (s.torchOn) "on" else "off"}, DND ${if (s.dndOn) "on" else "off"}, " +
                "uptime ${s.uptimeMs / 3_600_000}h"
        )
    }

    private fun readNotifications() = tool(
        name = "read_notifications",
        description = "List the most recent notifications with app, title and text.",
        risk = RiskLevel.TRIVIAL,
        params = listOf(ToolParam("count", "number", "How many to read (1-20), default 5", required = false))
    ) { args ->
        val count = (args["count"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 5
        val list = inbox.records.value.take(count)
        if (list.isEmpty()) {
            ToolOutcome.ok("No notifications captured. (Notification access may not be granted.)")
        } else {
            ToolOutcome.ok(
                list.joinToString("\n") { n ->
                    "- [${n.appLabel}] ${n.displayTitle}: ${n.displayText.take(120)}"
                }
            )
        }
    }

    private fun clearNotifications() = tool(
        name = "clear_notifications",
        description = "Clear the AXIS notification inbox (does not dismiss system notifications).",
        risk = RiskLevel.LOW,
        params = emptyList()
    ) {
        val before = inbox.records.value.size
        inbox.clear()
        ToolOutcome.ok("Cleared $before captured notification${if (before == 1) "" else "s"}")
    }

    // ---------------------------------------------------------- system

    private fun setFlashlight() = tool(
        name = "set_flashlight",
        description = "Turn the flashlight on or off.",
        risk = RiskLevel.MEDIUM,
        params = listOf(ToolParam("on", "boolean", "true to turn on, false to turn off"))
    ) { args ->
        val on = (args["on"] as? Boolean) ?: (argString(args, "on")?.toBooleanStrictOrNull())
            ?: return@tool ToolOutcome.fail("missing 'on' (true/false)")
        val r = systemActions.setTorch(on)
        if (r.ok) ToolOutcome.ok(r.detail) else ToolOutcome.fail(r.detail)
    }

    private fun setDnd() = tool(
        name = "set_dnd",
        description = "Turn Do Not Disturb on or off.",
        risk = RiskLevel.MEDIUM,
        params = listOf(ToolParam("on", "boolean", "true to enable DND"))
    ) { args ->
        val on = (args["on"] as? Boolean) ?: return@tool ToolOutcome.fail("missing 'on'")
        val r = systemActions.setDnd(on)
        if (r.ok) ToolOutcome.ok(r.detail) else ToolOutcome.fail(r.detail)
    }

    private fun setRinger() = tool(
        name = "set_ringer",
        description = "Set the ringer mode.",
        risk = RiskLevel.MEDIUM,
        params = listOf(
            ToolParam("mode", "string", "silent | vibrate | normal", enum = listOf("silent", "vibrate", "normal"))
        )
    ) { args ->
        val mode = when (argString(args, "mode")?.lowercase()) {
            "silent" -> 0
            "vibrate" -> 1
            "normal" -> 2
            else -> return@tool ToolOutcome.fail("mode must be silent, vibrate or normal")
        }
        val r = systemActions.setRingerMode(mode)
        if (r.ok) ToolOutcome.ok(r.detail) else ToolOutcome.fail(r.detail)
    }

    private fun setBrightness() = tool(
        name = "set_brightness",
        description = "Set screen brightness as a percentage (5-100).",
        risk = RiskLevel.MEDIUM,
        params = listOf(ToolParam("percent", "number", "5-100"))
    ) { args ->
        val pct = (args["percent"] as? Number)?.toInt()
            ?: argString(args, "percent")?.toIntOrNull()
            ?: return@tool ToolOutcome.fail("missing 'percent'")
        val r = systemActions.setBrightness(pct)
        if (r.ok) ToolOutcome.ok(r.detail) else ToolOutcome.fail(r.detail)
    }

    private fun setRotationLock() = tool(
        name = "set_rotation_lock",
        description = "Lock or unlock screen rotation.",
        risk = RiskLevel.MEDIUM,
        params = listOf(ToolParam("locked", "boolean", "true to lock rotation"))
    ) { args ->
        val locked = (args["locked"] as? Boolean) ?: return@tool ToolOutcome.fail("missing 'locked'")
        val r = systemActions.setRotationLocked(locked)
        if (r.ok) ToolOutcome.ok(r.detail) else ToolOutcome.fail(r.detail)
    }

    private fun openSettingsPage() = tool(
        name = "open_settings",
        description = "Open a system settings page (wifi, bluetooth, battery, apps, display, sound, accessibility…).",
        risk = RiskLevel.TRIVIAL,
        params = listOf(ToolParam("page", "string", "Page keyword, e.g. wifi"))
    ) { args ->
        val page = argString(args, "page") ?: return@tool ToolOutcome.fail("missing 'page'")
        val r = systemActions.openSettingsPage(page)
        if (r.ok) ToolOutcome.ok(r.detail) else ToolOutcome.fail(r.detail)
    }

    // --------------------------------------------------------- routines

    private fun runRoutine() = tool(
        name = "run_routine",
        description = "Run a saved routine by name or id.",
        risk = RiskLevel.MEDIUM,
        params = listOf(ToolParam("routine", "string", "Routine name or id"))
    ) { args ->
        val needle = argString(args, "routine")?.lowercase() ?: return@tool ToolOutcome.fail("missing 'routine'")
        val routine = routines.routines.value.firstOrNull {
            it.id.equals(needle, true) || it.name.lowercase() == needle
        } ?: routines.routines.value.firstOrNull { it.name.lowercase().contains(needle) }
        ?: return@tool ToolOutcome.fail("No routine named \"$needle\".")
        routines.runNow(routine.id)
        ToolOutcome.ok("Started routine ${routine.name}")
    }

    private fun listRoutines() = tool(
        name = "list_routines",
        description = "List saved routines with their triggers and enabled state.",
        risk = RiskLevel.TRIVIAL,
        params = emptyList()
    ) {
        val list = routines.routines.value
        if (list.isEmpty()) ToolOutcome.ok("No routines saved yet.")
        else ToolOutcome.ok(
            list.joinToString("\n") { r ->
                "- ${r.name} [${if (r.enabled) "on" else "off"}] triggers: ${r.summary}; " +
                    "actions: ${r.actions.joinToString(", ") { it.label }}"
            }
        )
    }

    // ------------------------------------------------------------ voice

    private fun say() = tool(
        name = "say",
        description = "Speak a short sentence out loud on the device.",
        risk = RiskLevel.LOW,
        params = listOf(ToolParam("text", "string", "What to say (max 240 chars)"))
    ) { args ->
        val text = argString(args, "text") ?: return@tool ToolOutcome.fail("missing 'text'")
        val how = speaker.speak(text.take(240))
        ToolOutcome.ok(how)
    }

    // ------------------------------------------------------------ helpers

    private fun argString(args: Map<String, Any?>, key: String): String? =
        (args[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: (args[key] as? Number)?.toString()

    private fun tool(
        name: String,
        description: String,
        risk: RiskLevel,
        params: List<ToolParam>,
        label: String = name.replace('_', ' ').replaceFirstChar { it.uppercase() },
        body: suspend (Map<String, Any?>) -> ToolOutcome
    ): AgentTool = object : AgentTool {
        override val descriptor = ToolDescriptor(
            name = name,
            description = description,
            params = params,
            risk = risk,
            label = label
        )

        override suspend fun execute(args: Map<String, Any?>): ToolOutcome = body(args)

        override fun summarize(args: Map<String, Any?>): String {
            val hint = descriptor.params
                .mapNotNull { p -> argString(args, p.name)?.let { "${p.name} \"$it\"" } }
                .joinToString(", ")
            return if (hint.isEmpty()) descriptor.label else "${descriptor.label} · $hint"
        }
    }

    /** Provider status text used by the chat header and Side-Car. */
    suspend fun providerSummary(): String {
        val name = settings.userName.first()
        return if (name.isNullOrBlank()) "agent ready" else "ready for $name"
    }
}
