package axis.kernel.json

/**
 * Zero-dependency JSON codec (pure Kotlin, unit-tested).
 *
 * AXIS talks to a dozen LLM/TTS providers and persists its own settings as
 * JSON. Adding a serialization framework for that would be a large dependency
 * surface inside the launcher process for a handful of shapes, so the kernel
 * ships this ~200-line codec instead. It supports exactly the JSON that
 * providers emit and AXIS stores:
 *
 *  - objects → [LinkedHashMap]<String, Any?> (insertion order preserved so
 *    round-tripped settings files stay diff-friendly),
 *  - arrays  → [ArrayList]<Any?>,
 *  - strings, booleans, null,
 *  - numbers → [Long] when integral, [Double] otherwise,
 *  - all standard escapes incl. \uXXXX, and `\/`.
 *
 * Deliberately NOT a full RFC 8259 validator: duplicate keys keep the last
 * value, and lone surrogates are passed through.
 */
object MiniJson {

    // ---------------------------------------------------------------- encode

    /** Encodes any of the supported shapes; unsupported types throw. */
    fun encode(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Float -> if (value.isFinite()) sb.append(value.toString()) else sb.append("null")
            is Double -> if (value.isFinite()) sb.append(value.toString()) else sb.append("null")
            is Number -> sb.append(value.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k?.toString() ?: "null")
                    sb.append(':')
                    write(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, v)
                }
                sb.append(']')
            }
            is Array<*> -> write(sb, value.toList())
            is BooleanArray -> write(sb, value.toList())
            is IntArray -> write(sb, value.toList())
            is LongArray -> write(sb, value.toList())
            else -> writeString(sb, value.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    // ---------------------------------------------------------------- decode

    /** Parses [text]; returns null when the document is malformed. */
    fun parseOrNull(text: String?): Any? =
        try {
            if (text.isNullOrBlank()) null else Parser(text).parseDocument()
        } catch (_: Exception) {
            null
        }

    /** Parses [text] or throws [IllegalArgumentException] with position info. */
    fun parse(text: String): Any? = Parser(text).parseDocument()

    fun objectOrNull(text: String?): Map<String, Any?>? =
        parseOrNull(text) as? Map<String, Any?>

    fun listOrNull(text: String?): List<Any?>? = parseOrNull(text) as? List<Any?>

    private class Parser(private val src: String) {
        private var i = 0

        fun parseDocument(): Any? {
            skipWs()
            val v = parseValue()
            skipWs()
            if (i != src.length) fail("trailing content")
            return v
        }

        private fun parseValue(): Any? {
            skipWs()
            if (i >= src.length) fail("unexpected end")
            return when (val c = src[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) parseNumber() else fail("unexpected '$c'")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') { i++; return out }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                out[key] = parseValue()
                skipWs()
                when (val c = next()) {
                    ',' -> continue
                    '}' -> return out
                    else -> fail("expected ',' or '}', got '$c'")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') { i++; return out }
            while (true) {
                out.add(parseValue())
                skipWs()
                when (val c = next()) {
                    ',' -> continue
                    ']' -> return out
                    else -> fail("expected ',' or ']', got '$c'")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= src.length) fail("unterminated string")
                when (val c = src[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= src.length) fail("unterminated escape")
                        when (val e = src[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > src.length) fail("bad \\u escape")
                                val hex = src.substring(i, i + 4)
                                i += 4
                                val code = hex.toIntOrNull(16) ?: fail("bad \\u escape '$hex'")
                                sb.append(code.toChar())
                            }
                            else -> fail("bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Any {
            val start = i
            if (peek() == '-') i++
            while (i < src.length && src[i].isDigit()) i++
            var integral = true
            if (i < src.length && src[i] == '.') {
                integral = false
                i++
                while (i < src.length && src[i].isDigit()) i++
            }
            if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
                integral = false
                i++
                if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
                while (i < src.length && src[i].isDigit()) i++
            }
            val raw = src.substring(start, i)
            if (raw.isEmpty() || raw == "-") fail("bad number")
            return if (integral) raw.toLongOrNull() ?: raw.toDouble() else raw.toDouble()
        }

        private fun <T> literal(token: String, value: T): T {
            if (!src.startsWith(token, i)) fail("expected '$token'")
            i += token.length
            return value
        }

        private fun skipWs() {
            while (i < src.length && (src[i] == ' ' || src[i] == '\n' || src[i] == '\r' || src[i] == '\t')) i++
        }

        private fun peek(): Char = if (i < src.length) src[i] else '\u0000'

        private fun next(): Char = if (i < src.length) src[i++] else fail("unexpected end")

        private fun expect(c: Char) {
            if (i >= src.length || src[i] != c) fail("expected '$c'")
            i++
        }

        private fun fail(msg: String): Nothing =
            throw IllegalArgumentException("MiniJson: $msg at index $i")
    }
}

// ------------------------------------------------------------------ helpers

/** Typed, null-safe navigation helpers used all over the provider code. */
fun Map<String, Any?>.str(key: String): String? = (this[key] as? String)

fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

fun Map<String, Any?>.double(key: String): Double? = (this[key] as? Number)?.toDouble()

fun Map<String, Any?>.bool(key: String): Boolean? = (this[key] as? Boolean)

fun Map<String, Any?>.obj(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.objList(key: String): List<Map<String, Any?>> =
    (this[key] as? List<Any?>)?.filterIsInstance<Map<String, Any?>>().orEmpty()

fun Map<String, Any?>.list(key: String): List<Any?> = (this[key] as? List<Any?>).orEmpty()

/**
 * Depth-first search for the first value stored under [key] anywhere in a
 * nested document. Provider APIs bury the interesting fields (Gemini's
 * `text`, Anthropic's `content`, OpenAI's `delta`) at different depths and
 * wrap them in arrays, so the streaming parsers sometimes need this.
 */
fun Any?.findFirst(key: String): Any? {
    when (this) {
        is Map<*, *> -> {
            if (containsKey(key)) return this[key]
            for (v in values) v.findFirst(key)?.let { return it }
        }
        is List<*> -> for (v in this) v.findFirst(key)?.let { return it }
    }
    return null
}
