package axis.agent.prompt

import axis.agent.tool.ToolRegistry
import axis.kernel.caps.CapabilityManifest

/**
 * Builds the AXIS system prompt. Two rules shape this file:
 *
 *  1. The model only ever hears about tools the device can *actually* run
 *     right now (the capability manifest is compiled from live permission
 *     checks), so it cannot promise an action the launcher will refuse.
 *  2. The tool protocol is plain JSON (see ToolCallParser) — no native
 *     function-calling, because AXIS routes across many providers.
 */
object AgentPrompt {

    fun system(
        manifest: CapabilityManifest,
        registry: ToolRegistry,
        userName: String? = null,
        mode: String = "chat"
    ): String = buildString {
        appendLine("You are AXIS, the assistant built into an Android launcher.")
        appendLine("You live on the home screen; your replies are read there, so be brief.")
        appendLine()
        appendLine("STYLE")
        appendLine("- Answer in the fewest words that fully answer. No preamble, no sign-off.")
        appendLine("- Plain text. No markdown headings, no bullet symbols unless asked for a list.")
        appendLine("- Never invent device state. If you do not know, say so in one line.")
        if (userName != null) appendLine("- Address the user as $userName.")
        appendLine()
        appendLine("DEVICE CAPABILITIES (live)")
        appendLine(capabilityLines(manifest))
        appendLine()
        appendLine("TOOLS YOU MAY CALL")
        if (registry.descriptors.isEmpty()) {
            appendLine("(none right now — answer as a normal chat assistant)")
        } else {
            appendLine(registry.schemaLines())
            appendLine()
            appendLine("TOOL PROTOCOL")
            appendLine("When (and only when) an action is needed, reply with ONE json object and nothing else:")
            appendLine("""{"tool":"<name>","args":{...},"summary":"<3-6 word imperative shown to the user>"}""")
            appendLine("Rules:")
            appendLine("- One tool per reply. Do not wrap it in prose.")
            appendLine("- Only call a tool listed above, and only with declared parameters.")
            appendLine("- Risky or irreversible actions are shown to the user for approval before running;")
            appendLine("  write the summary as if asking permission.")
            appendLine("- After a tool result arrives you will get a message starting with TOOL RESULT:")
            appendLine("  use it to answer the user in prose. Do not call the same tool twice.")
            appendLine("- If no tool is needed, just answer in prose. Never explain the protocol.")
        }
        if (mode == "voice") {
            appendLine()
            appendLine("VOICE MODE: this reply will be spoken. Keep it under 40 words, no URLs, no code.")
        }
    }

    /** Compact, honest description of what the device allows right now. */
    fun capabilityLines(m: CapabilityManifest): String = buildList {
        add(if (m.notifications) "notifications: can read and clear (listener granted)" else "notifications: cannot read (listener not granted)")
        add(if (m.accessibility) "screen: can see and tap UI (accessibility granted)" else "screen: cannot see UI (accessibility not granted)")
        add(if (m.overlay) "overlay: can draw floating panels" else "overlay: cannot draw floating panels")
        add(if (m.usageStats) "app history: available" else "app history: unavailable")
        add(if (m.dnd) "do-not-disturb: controllable" else "do-not-disturb: not controllable")
        add(if (m.writeSettings) "system settings: writable" else "system settings: read-only")
        add(if (m.mic) "microphone: granted" else "microphone: not granted")
        add(if (m.location) "location: granted" else "location: not granted")
        add(
            if (m.connectedProviders.isEmpty()) "ai providers: NONE connected (basic mode — you are the offline fallback)"
            else "ai providers connected: " + m.connectedProviders.sorted().joinToString(", ")
        )
    }.joinToString("\n") { "- $it" }
}
