package axis.agent.keys

import axis.agent.provider.AiProvider

/** Result of the local (offline) key check run before any network call. */
data class KeyCheck(
    val ok: Boolean,
    val message: String,
    /** Masked form safe to render in the UI / logs. */
    val preview: String
)

/**
 * Offline key hygiene checks. Deliberately permissive: prefixes are hints,
 * not contracts — providers rotate them, and a wrong guess must never block
 * a paying user. The authoritative test is the live "Test key" call.
 */
object KeyValidator {

    private const val MIN_LEN = 16

    /** `gsk_abcd…9f2a` — never render a full key back to the screen. */
    fun preview(key: String): String {
        val k = key.trim()
        if (k.isEmpty()) return ""
        if (k.length <= 8) return "•".repeat(k.length)
        val head = k.take(4)
        val tail = k.takeLast(4)
        return "$head…$tail"
    }

    fun validate(provider: AiProvider, rawKey: String): KeyCheck {
        val key = rawKey.trim()
        val preview = preview(key)

        if (provider.keyless && key.isEmpty()) {
            return KeyCheck(true, "No key needed for ${provider.label}", preview)
        }
        if (key.isEmpty()) {
            return KeyCheck(false, "Paste your ${provider.label} API key", preview)
        }
        if (key.length < MIN_LEN) {
            return KeyCheck(false, "Too short (${key.length} chars) — check the paste", preview)
        }
        if (key.any { it.isWhitespace() }) {
            return KeyCheck(false, "Contains spaces/newlines — re-copy the key", preview)
        }
        if (!key.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }) {
            return KeyCheck(false, "Unexpected characters in key", preview)
        }
        val prefix = provider.keyPrefix
        if (prefix != null && !key.startsWith(prefix)) {
            return KeyCheck(
                true,
                "Saved — key does not start with \"$prefix\", so verify with Test",
                preview
            )
        }
        return KeyCheck(true, "Format looks right — run Test to confirm", preview)
    }
}
