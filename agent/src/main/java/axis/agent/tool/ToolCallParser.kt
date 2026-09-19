package axis.agent.tool

import axis.kernel.agent.ToolCallSpec
import axis.kernel.json.MiniJson

/**
 * Parses the text-JSON tool protocol out of an assistant reply.
 *
 * The model is told to answer with exactly one of:
 *  1. prose (normal chat), or
 *  2. a single JSON object: `{"tool": "name", "args": {…}}`
 * possibly wrapped in a ```json fence. Real models add "Sure, here you go:"
 * preambles and fences, so this parser walks the text for the first object
 * that parses *and* carries a known `tool` name.
 */
object ToolCallParser {

    private const val MAX_SCAN = 8_000

    fun parse(text: String, knownTools: Set<String>): ToolCallSpec? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        fromObject(trimmed, knownTools)?.let { return it }

        // Fenced block first (most common wrapping).
        for (fence in listOf("```json", "```JSON", "```")) {
            val start = trimmed.indexOf(fence)
            if (start < 0) continue
            val bodyStart = start + fence.length
            val end = trimmed.indexOf("```", bodyStart)
            if (end > bodyStart) {
                fromObject(trimmed.substring(bodyStart, end).trim(), knownTools)?.let { return it }
            }
        }

        // Otherwise scan for balanced braces.
        val scan = trimmed.take(MAX_SCAN)
        var i = scan.indexOf('{')
        while (i >= 0 && i < scan.length) {
            val obj = extractBalanced(scan, i) ?: break
            fromObject(obj, knownTools)?.let { return it }
            i = scan.indexOf('{', i + 1)
        }
        return null
    }

    private fun fromObject(text: String, knownTools: Set<String>): ToolCallSpec? {
        val doc = MiniJson.objectOrNull(text) ?: return null
        val name = (doc["tool"] as? String)?.trim()
            ?: (doc["tool_call"] as? String)?.trim()
            ?: (doc["function"] as? String)?.trim()
            ?: return null
        if (name.isEmpty()) return null
        // Strict: a name the prompt never advertised is not a tool call.
        if (name !in knownTools) return null

        val args: Map<String, Any?> = when (val a = doc["args"] ?: doc["arguments"] ?: doc["params"]) {
            is Map<*, *> -> @Suppress("UNCHECKED_CAST") (a as Map<String, Any?>)
            is String -> MiniJson.objectOrNull(a).orEmpty()
            else -> emptyMap()
        }
        return ToolCallSpec(
            id = "call_" + System.nanoTime().toString(36),
            name = name,
            args = args,
            summary = (doc["summary"] as? String)?.take(140).orEmpty(),
            raw = text
        )
    }

    /** Returns the balanced `{…}` starting at [start], or null if unterminated. */
    private fun extractBalanced(text: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}

/**
 * Decides — as tokens arrive — whether a reply is prose or a tool call, so
 * the UI never flashes raw JSON at the user. Holds back only the first
 * characters; prose is released as soon as the first letter settles it.
 */
class ToolSniffer(private val holdLimit: Int = 2_000) {

    private val held = StringBuilder()
    private var decided = false
    private var tool = false

    val isToolCall: Boolean get() = tool

    /** Text that is now safe to display (may be empty). */
    fun accept(chunk: String): String {
        if (decided && !tool) return chunk
        held.append(chunk)
        if (decided) {
            // Holding a tool call: give up if it grows absurdly long, so a
            // model that opens with '{' and then rambles still gets shown.
            return if (held.length > holdLimit) flushAsProse() else ""
        }
        val first = held.firstOrNull { !it.isWhitespace() } ?: return ""
        return when {
            first == '{' || first == '`' -> {
                tool = true
                decided = true
                ""
            }
            first.isLetterOrDigit() || first == '"' || first == '#' || first == '-' -> flushAsProse()
            else -> ""
        }
    }

    /** Everything still held (the raw tool JSON), for parsing. */
    fun buffered(): String = held.toString()

    /** Releases held text as prose when the payload can't be a tool call. */
    fun flushAsProse(): String {
        tool = false
        decided = true
        val out = held.toString()
        held.setLength(0)
        return out
    }
}
