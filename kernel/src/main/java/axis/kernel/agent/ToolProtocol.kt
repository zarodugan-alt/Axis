package axis.kernel.agent

/**
 * Tool-call protocol shared by `:agent` (which produces calls) and `:safety`
 * (which gates them). Kept in the kernel so neither module has to depend on
 * the other.
 *
 * Wire format: AXIS uses a *text* JSON protocol instead of native function
 * calling. Reasoning: AXIS routes across Groq / Mistral / Gemini / OpenAI /
 * Anthropic / OpenRouter / local Ollama, and native tool-calling support is
 * inconsistent (and absent on several free tiers). A single JSON convention
 * in the system prompt works identically everywhere, and degrades to plain
 * chat when the model ignores it.
 */
data class ToolCallSpec(
    val id: String,
    val name: String,
    val args: Map<String, Any?>,
    /** Human sentence shown in confirmation gates ("Launch WhatsApp"). */
    val summary: String = name,
    val raw: String = ""
)

/** One parameter of an [axis.agent.tool.AgentTool]. */
data class ToolParam(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val enum: List<String> = emptyList()
)

/** What the safety layer decided about a [ToolCallSpec]. */
sealed interface GateDecision {
    /** Execute immediately. */
    data class Allow(val note: String = "") : GateDecision

    /** Ask the user; the call runs only after [axis.safety.Gate] approval. */
    data class Confirm(val reason: String, val risk: Int) : GateDecision

    /** Refuse; the reason is fed back to the model as the tool result. */
    data class Deny(val reason: String) : GateDecision

    val isAllowed: Boolean get() = this is Allow
}

/** Result handed back to the model after a tool ran (or did not). */
data class ToolResult(
    val callId: String,
    val name: String,
    val ok: Boolean,
    val output: String,
    val durationMs: Long = 0L
)

/**
 * Risk taxonomy for actions (spec §F14). Ordered: the enum ordinal is the
 * risk score used by the policy engine's thresholds.
 */
enum class RiskLevel { TRIVIAL, LOW, MEDIUM, HIGH, CRITICAL }
