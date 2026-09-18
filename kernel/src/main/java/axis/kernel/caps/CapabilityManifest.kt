package axis.kernel.caps

/**
 * Snapshot of everything AXIS is currently allowed and able to do (spec §F2).
 *
 * Compiled at session start and on any permission/provider change. The agent
 * serializes this into its system prompt so the LLM only ever sees tools
 * that exist *right now*.
 *
 * P1 note: permission flags are real (checked by `:app`), but the Tier→
 * provider routing map lives in `:agent` (P3) to keep `:kernel` free of
 * network types. Until then [connectedProviders] carries provider ids.
 */
data class CapabilityManifest(
    val accessibility: Boolean = false,
    val notifications: Boolean = false,
    val overlay: Boolean = false,
    val usageStats: Boolean = false,
    val writeSettings: Boolean = false,
    val dnd: Boolean = false,
    val location: Boolean = false,
    val mic: Boolean = false,
    val sms: Boolean = false,
    val phone: Boolean = false,
    val contacts: Boolean = false,
    val calendar: Boolean = false,
    /** Ids of providers with a validated key, e.g. "groq", "mistral". Empty in P1. */
    val connectedProviders: Set<String> = emptySet(),
    val visionEnabled: Boolean = false
) {
    /** True when the app must run as launcher + routines only (spec §10). */
    val basicMode: Boolean get() = connectedProviders.isEmpty()
}
