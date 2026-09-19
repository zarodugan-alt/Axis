package axis.safety

import axis.kernel.agent.GateDecision
import axis.kernel.agent.RiskLevel
import axis.kernel.agent.ToolCallSpec
import java.util.ArrayDeque

/** User-facing safety configuration (spec §F14). */
data class SafetyPolicy(
    /** Packages the agent may never touch (banking, work, …). */
    val protectedPackages: Set<String> = emptySet(),
    /** Kill switch: blocks every autonomous action immediately. */
    val killSwitch: Boolean = false,
    /** Risk level at or above which a confirmation is required. */
    val confirmFrom: RiskLevel = RiskLevel.MEDIUM,
    /** Risk level at or above which the call is refused outright. */
    val blockFrom: RiskLevel = RiskLevel.CRITICAL,
    /** Autonomous actions allowed per minute before throttling kicks in. */
    val rateLimitPerMinute: Int = 12,
    /** Once the user approves a tool, approve it again silently for this long. */
    val trustWindowSeconds: Int = 120
)

/** Audit row shown in Settings → Safety → Activity log. */
data class AuditEntry(
    val ts: Long,
    val kind: String,
    val tool: String,
    val detail: String,
    val ok: Boolean
)

/**
 * The safety core (spec §F14). Everything the agent wants to do passes
 * through [evaluate]; nothing else in AXIS decides what may run.
 *
 * Order of checks matters and is deliberate: kill switch → protected
 * package → rate limit → risk thresholds. A protected package is blocked
 * even for TRIVIAL actions, because "protected" is a user promise.
 */
class PolicyEngine(
    private val policyProvider: () -> SafetyPolicy,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val recent = ArrayDeque<Long>()
    private val approvals = HashMap<String, Long>()

    fun evaluate(call: ToolCallSpec, risk: RiskLevel): GateDecision {
        val policy = policyProvider()

        if (policy.killSwitch) {
            return GateDecision.Deny("Kill switch is on — autonomous actions are stopped")
        }

        val target = targetPackage(call)
        if (target != null && target in policy.protectedPackages) {
            return GateDecision.Deny("$target is on your protected list")
        }

        if (!withinRateLimit(policy)) {
            return GateDecision.Deny(
                "Rate limit: more than ${policy.rateLimitPerMinute} actions per minute"
            )
        }

        if (risk.ordinal >= policy.blockFrom.ordinal) {
            return GateDecision.Deny("${call.name} is classified ${risk.name.lowercase()} risk")
        }

        val trusted = approvals[trustKey(call)]?.let { now() - it < policy.trustWindowSeconds * 1000L } == true
        if (trusted) return GateDecision.Allow("approved recently")

        return if (risk.ordinal >= policy.confirmFrom.ordinal) {
            GateDecision.Confirm(
                reason = "${call.name} is ${risk.name.lowercase()} risk",
                risk = risk.ordinal
            )
        } else {
            GateDecision.Allow()
        }
    }

    /** Call after the user approves a Confirm decision. */
    fun rememberApproval(call: ToolCallSpec) {
        approvals[trustKey(call)] = now()
    }

    /** Call by the executor for every action that actually ran. */
    fun noteExecuted() {
        val cutoff = now() - 60_000
        while (recent.isNotEmpty() && recent.peekFirst() < cutoff) recent.pollFirst()
        recent.addLast(now())
    }

    fun resetThrottle() = recent.clear()

    private fun withinRateLimit(policy: SafetyPolicy): Boolean {
        val cutoff = now() - 60_000
        while (recent.isNotEmpty() && recent.peekFirst() < cutoff) recent.pollFirst()
        return recent.size < policy.rateLimitPerMinute
    }

    /** Package an action targets, when it obviously targets one. */
    private fun targetPackage(call: ToolCallSpec): String? =
        (call.args["package"] as? String)
            ?: (call.args["packageName"] as? String)
            ?: (call.args["pkg"] as? String)

    private fun trustKey(call: ToolCallSpec): String = call.name + ":" + targetPackage(call).orEmpty()
}

/**
 * Bounded audit trail: newest first, in memory, mirrored to disk by the app
 * layer. The agent's actions must always be reviewable after the fact.
 */
class AuditLog(private val capacity: Int = 400) {

    private val lock = Any()
    private val entries = ArrayDeque<AuditEntry>()

    fun add(entry: AuditEntry) {
        synchronized(lock) {
            entries.addFirst(entry)
            while (entries.size > capacity) entries.removeLast()
        }
    }

    fun all(): List<AuditEntry> = synchronized(lock) { entries.toList() }

    fun clear() = synchronized(lock) { entries.clear() }

    /** Plain-text export for Settings → Advanced. */
    fun exportText(): String = all().joinToString("\n") { e ->
        "${java.util.Date(e.ts)}  ${if (e.ok) "OK " else "ERR"}  ${e.kind}  ${e.tool}  ${e.detail}"
    }
}
