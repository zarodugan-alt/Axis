package axis.app.agent

import axis.agent.ToolGate
import axis.agent.tool.AgentTool
import axis.kernel.agent.GateDecision
import axis.kernel.agent.ToolCallSpec
import axis.kernel.events.AxisEvent
import axis.kernel.events.EventBus
import axis.safety.AuditEntry
import axis.safety.AuditLog
import axis.safety.PolicyEngine
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** A confirmation the user must answer before the action runs. */
data class PendingConfirm(
    val callId: String,
    val tool: String,
    val summary: String,
    val reason: String,
    val risk: Int
)

/**
 * Bridges the agent loop to `:safety` (spec §F14).
 *
 * Flow: loop asks → policy evaluates → Allow runs immediately, Deny is fed
 * back to the model, Confirm suspends the agent until the user answers the
 * dialog (or [TIMEOUT_MS] passes, which counts as a refusal — silence must
 * never be consent).
 */
class SafetyGate(
    private val policy: PolicyEngine,
    private val audit: AuditLog,
    private val bus: EventBus
) : ToolGate {

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val _confirmation = MutableStateFlow<PendingConfirm?>(null)

    /** Non-null while the UI should show a confirmation dialog. */
    val confirmation: StateFlow<PendingConfirm?> = _confirmation

    override suspend fun check(call: ToolCallSpec, tool: AgentTool?): GateDecision {
        if (tool == null) return GateDecision.Deny("Unknown tool ${call.name}")
        val risk = tool.descriptor.risk
        val decision = policy.evaluate(call, risk)
        audit.add(
            AuditEntry(
                ts = System.currentTimeMillis(),
                kind = when (decision) {
                    is GateDecision.Allow -> "allow"
                    is GateDecision.Confirm -> "confirm"
                    is GateDecision.Deny -> "deny"
                },
                tool = call.name,
                detail = when (decision) {
                    is GateDecision.Allow -> decision.note.ifBlank { call.summary }
                    is GateDecision.Confirm -> decision.reason
                    is GateDecision.Deny -> decision.reason
                },
                ok = decision !is GateDecision.Deny
            )
        )
        if (decision !is GateDecision.Confirm) return decision

        val request = PendingConfirm(
            callId = call.id,
            tool = call.name,
            summary = call.summary.ifBlank { tool.descriptor.label },
            reason = decision.reason,
            risk = decision.risk
        )
        val deferred = CompletableDeferred<Boolean>()
        pending[call.id] = deferred
        _confirmation.value = request

        val answer = withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
        pending.remove(call.id)
        _confirmation.value = null

        return when (answer) {
            true -> {
                policy.rememberApproval(call)
                bus.tryEmit(AxisEvent.GateResolved(call.id, call.name, true))
                audit.add(
                    AuditEntry(
                        System.currentTimeMillis(), "approved", call.name,
                        request.summary, true
                    )
                )
                GateDecision.Allow("approved by user")
            }
            false -> {
                bus.tryEmit(AxisEvent.GateResolved(call.id, call.name, false))
                GateDecision.Deny("The user declined this action")
            }
            null -> {
                bus.tryEmit(AxisEvent.GateResolved(call.id, call.name, false))
                GateDecision.Deny("No response to the confirmation — not executed")
            }
        }
    }

    /** Called by the confirmation dialog. */
    fun respond(callId: String, approved: Boolean) {
        pending[callId]?.complete(approved)
    }

    fun noteExecuted() = policy.noteExecuted()

    companion object {
        /** How long the agent waits for a human answer before giving up. */
        const val TIMEOUT_MS = 60_000L
    }
}
