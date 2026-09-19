package axis.agent

import axis.agent.protocol.ChatMessage
import axis.agent.protocol.Role
import axis.agent.tool.AgentTool
import axis.agent.tool.ToolCallParser
import axis.agent.tool.ToolRegistry
import axis.agent.tool.ToolSniffer
import axis.kernel.agent.GateDecision
import axis.kernel.agent.ToolCallSpec
import axis.kernel.agent.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Everything the chat UI needs to render one agent turn.
 */
sealed interface AgentEvent {
    /** Prose delta — append to the transcript. */
    data class Delta(val text: String) : AgentEvent

    /** The model asked for a tool; the gate has already approved it. */
    data class ToolStarted(val call: ToolCallSpec, val label: String, val step: Int) : AgentEvent

    /** Tool finished; [result.output] is also fed back to the model. */
    data class ToolFinished(val result: ToolResult, val step: Int) : AgentEvent

    /** The gate (or the kill switch) refused the call. */
    data class ToolDenied(val call: ToolCallSpec, val reason: String, val step: Int) : AgentEvent

    /** A provider/transport failure the user should see verbatim. */
    data class Failed(val message: String) : AgentEvent

    /** Turn complete: [text] is the full prose answer. */
    data class Done(val text: String, val steps: Int, val providerId: String) : AgentEvent
}

/**
 * Decides whether a tool call may run. `:app` implements this over
 * `:safety`'s policy engine and a UI confirmation dialog; tests use fakes.
 */
interface ToolGate {
    suspend fun check(call: ToolCallSpec, tool: AgentTool?): GateDecision
}

/**
 * The agent loop (spec §S6 / §F13): stream a reply; if it is a tool call,
 * run it, feed the result back, and continue — up to [maxSteps] tool steps
 * per turn. Prose replies end the turn immediately.
 */
class AgentLoop(
    private val gateway: LlmGateway,
    private val registry: ToolRegistry,
    private val gate: ToolGate,
    private val maxSteps: Int = 4
) {

    fun run(
        system: String,
        history: List<ChatMessage>,
        routes: List<Route>,
        temperature: Double = 0.4,
        maxTokens: Int = 1024
    ): Flow<AgentEvent> = flow {
        val conversation = ArrayList<ChatMessage>()
        conversation.add(ChatMessage(Role.SYSTEM, system))
        conversation.addAll(history)

        val known = registry.descriptors.map { it.name }.toSet()
        val providerId = routes.firstOrNull()?.provider?.id ?: "none"
        var step = 0

        while (true) {
            val sniffer = ToolSniffer()
            val raw = StringBuilder()
            val prose = StringBuilder()

            try {
                gateway.stream(conversation, routes, temperature, maxTokens).collect { delta ->
                    if (delta.text.isEmpty()) return@collect
                    raw.append(delta.text)
                    val display = sniffer.accept(delta.text)
                    if (display.isNotEmpty()) {
                        prose.append(display)
                        emit(AgentEvent.Delta(display))
                    }
                }
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (t: Throwable) {
                val message = t.message ?: "Provider error"
                emit(AgentEvent.Failed(message))
                // Whatever prose arrived before the failure stays on screen.
                if (prose.isNotEmpty()) emit(AgentEvent.Done(prose.toString(), step, providerId))
                return@flow
            }

            val fullText = raw.toString()
            val call = ToolCallParser.parse(fullText, known)

            if (call == null) {
                // Not a tool call: release anything the sniffer held back.
                val tail = sniffer.buffered()
                if (tail.isNotEmpty() && prose.isEmpty()) {
                    prose.append(tail)
                    emit(AgentEvent.Delta(tail))
                }
                emit(AgentEvent.Done(prose.toString(), step, providerId))
                return@flow
            }

            if (step >= maxSteps) {
                emit(AgentEvent.ToolDenied(call, "Step limit ($maxSteps) reached", step))
                emit(AgentEvent.Done(prose.toString(), step, providerId))
                return@flow
            }

            step++
            val tool = registry.byName(call.name)
            val decision = gate.check(call, tool)

            when (decision) {
                is GateDecision.Deny -> {
                    emit(AgentEvent.ToolDenied(call, decision.reason, step))
                    conversation.add(ChatMessage(Role.ASSISTANT, fullText))
                    conversation.add(
                        ChatMessage(Role.USER, "TOOL RESULT ${call.name}: denied — ${decision.reason}")
                    )
                }
                is GateDecision.Confirm -> {
                    // A Confirm decision that reaches here means the UI could
                    // not ask (no screen). Treat as denied rather than guessing.
                    val reason = "Needs confirmation: ${decision.reason}"
                    emit(AgentEvent.ToolDenied(call, reason, step))
                    conversation.add(ChatMessage(Role.ASSISTANT, fullText))
                    conversation.add(ChatMessage(Role.USER, "TOOL RESULT ${call.name}: $reason"))
                }
                is GateDecision.Allow -> {
                    if (tool == null) {
                        emit(AgentEvent.ToolDenied(call, "Unknown tool ${call.name}", step))
                        conversation.add(ChatMessage(Role.ASSISTANT, fullText))
                        conversation.add(
                            ChatMessage(Role.USER, "TOOL RESULT ${call.name}: unknown tool")
                        )
                    } else {
                        val label = call.summary.ifBlank { tool.summarize(call.args) }
                        emit(AgentEvent.ToolStarted(call, label, step))
                        val started = System.currentTimeMillis()
                        val outcome = try {
                            tool.execute(call.args)
                        } catch (t: Throwable) {
                            axis.agent.tool.ToolOutcome.fail(t.message ?: "tool crashed")
                        }
                        val result = ToolResult(
                            callId = call.id,
                            name = call.name,
                            ok = outcome.ok,
                            output = outcome.output,
                            durationMs = System.currentTimeMillis() - started
                        )
                        emit(AgentEvent.ToolFinished(result, step))
                        conversation.add(ChatMessage(Role.ASSISTANT, fullText))
                        conversation.add(
                            ChatMessage(
                                Role.USER,
                                "TOOL RESULT ${call.name}: " +
                                    (if (outcome.ok) outcome.output else "error — ${outcome.output}")
                            )
                        )
                    }
                }
            }
        }
    }
}
