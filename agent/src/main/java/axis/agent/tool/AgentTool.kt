package axis.agent.tool

import axis.kernel.agent.RiskLevel
import axis.kernel.agent.ToolParam
import axis.kernel.json.MiniJson

/** What a tool reports back. `output` is fed verbatim to the model. */
data class ToolOutcome(val ok: Boolean, val output: String) {
    companion object {
        fun ok(output: String) = ToolOutcome(true, output)
        fun fail(output: String) = ToolOutcome(false, output)
    }
}

data class ToolDescriptor(
    val name: String,
    val description: String,
    val params: List<ToolParam>,
    val risk: RiskLevel,
    /** Shown to the user in the confirm gate ("Open WhatsApp"). */
    val label: String = name
)

/**
 * One capability the agent can invoke. Implementations live in `:app`
 * (they need Android context); `:agent` only knows this interface, which
 * keeps the loop unit-testable with fakes.
 */
interface AgentTool {
    val descriptor: ToolDescriptor

    suspend fun execute(args: Map<String, Any?>): ToolOutcome

    /** Dry-run summary for the confirmation gate. */
    fun summarize(args: Map<String, Any?>): String = descriptor.label
}

/** Name → tool lookup + prompt schema generation. */
class ToolRegistry(tools: List<AgentTool>) {

    private val byName: Map<String, AgentTool> = tools.associateBy { it.descriptor.name }

    val descriptors: List<ToolDescriptor> get() = byName.values.map { it.descriptor }

    fun byName(name: String): AgentTool? = byName[name]

    /** The JSON schema block embedded in the system prompt. */
    fun schemaJson(): String = MiniJson.encode(
        descriptors.map { d ->
            linkedMapOf<String, Any?>(
                "name" to d.name,
                "description" to d.description,
                "risk" to d.risk.name.lowercase(),
                "parameters" to d.params.map { p ->
                    linkedMapOf<String, Any?>(
                        "name" to p.name,
                        "type" to p.type,
                        "required" to p.required,
                        "description" to p.description
                    ).also { m -> if (p.enum.isNotEmpty()) m["enum"] = p.enum }
                }
            )
        }
    )

    /** Human-readable one-per-line list, used in the degraded prompt. */
    fun schemaLines(): String = descriptors.joinToString("\n") { d ->
        "- ${d.name}(${d.params.joinToString(", ") { it.name }}) — ${d.description}"
    }
}
