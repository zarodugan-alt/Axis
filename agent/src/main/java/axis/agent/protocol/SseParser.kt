package axis.agent.protocol

/**
 * Incremental Server-Sent-Events reader (pure, unit-tested).
 *
 * Feed raw lines one at a time; [feed] returns a payload exactly when an
 * event completes (blank line) — that is where the JSON lives. Multi-line
 * `data:` fields are joined with `\n` per the SSE spec, comments (`:` ping
 * lines) and non-data fields (`event:`, `id:`) are ignored, and the leading
 * space after `data:` is stripped only when present.
 */
class SseParser {

    private val data = StringBuilder()
    private var hasData = false

    fun feed(line: String): String? {
        // Strip a trailing CR from CRLF streams.
        val raw = line.trimEnd('\r')

        if (raw.isEmpty()) return flush()

        if (raw.startsWith(":")) return null // comment / keep-alive

        val colon = raw.indexOf(':')
        val field = if (colon == -1) raw else raw.substring(0, colon)
        var value = if (colon == -1) "" else raw.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)

        if (field == "data") {
            if (hasData) data.append('\n')
            data.append(value)
            hasData = true
        }
        return null
    }

    /** Returns any buffered payload (call at end of stream). */
    fun flush(): String? {
        if (!hasData) return null
        val payload = data.toString()
        data.setLength(0)
        hasData = false
        return payload
    }

    fun reset() {
        data.setLength(0)
        hasData = false
    }
}
