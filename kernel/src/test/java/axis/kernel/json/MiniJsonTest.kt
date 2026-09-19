package axis.kernel.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun encodesAndDecodesScalars() {
        assertEquals("null", MiniJson.encode(null))
        assertEquals("true", MiniJson.encode(true))
        assertEquals("42", MiniJson.encode(42))
        assertEquals("1.5", MiniJson.encode(1.5))
        assertEquals("\"hi\"", MiniJson.encode("hi"))
    }

    @Test
    fun roundTripsNestedDocument() {
        val doc = linkedMapOf<String, Any?>(
            "model" to "llama-3.3-70b",
            "stream" to true,
            "temperature" to 0.4,
            "max_tokens" to 1024,
            "messages" to listOf(
                linkedMapOf("role" to "system", "content" to "be brief"),
                linkedMapOf("role" to "user", "content" to "hello \"world\"\nline 2")
            ),
            "stop" to null
        )
        val text = MiniJson.encode(doc)
        val back = MiniJson.parse(text) as Map<*, *>
        assertEquals("llama-3.3-70b", back["model"])
        assertEquals(true, back["stream"])
        assertEquals(1024L, back["max_tokens"])
        val messages = back["messages"] as List<*>
        val user = messages[1] as Map<*, *>
        assertEquals("hello \"world\"\nline 2", user["content"])
    }

    @Test
    fun parsesUnicodeEscapesAndSurrogatePairs() {
        val v = MiniJson.parse("\"caf\\u00e9 \\u2603\"") as String
        assertEquals("café ☃", v)
        // Emoji arrive as a surrogate pair and must survive intact.
        val emoji = MiniJson.parse("\"\\ud83d\\ude80\"") as String
        assertEquals(2, emoji.length)
        assertEquals("\uD83D\uDE80", emoji)
    }

    @Test
    fun integralNumbersStayLongsFractionalStayDoubles() {
        val doc = MiniJson.parse("""{"a":1,"b":1.25,"c":-3,"d":1e3}""") as Map<*, *>
        assertEquals(1L, doc["a"])
        assertEquals(1.25, doc["b"])
        assertEquals(-3L, doc["c"])
        assertEquals(1000.0, doc["d"])
    }

    @Test
    fun malformedInputReturnsNullInsteadOfThrowing() {
        assertNull(MiniJson.parseOrNull("{\"a\":}"))
        assertNull(MiniJson.parseOrNull("{'a':1}"))
        assertNull(MiniJson.parseOrNull("{\"a\":1"))
        assertNull(MiniJson.parseOrNull(""))
        assertNull(MiniJson.parseOrNull(null))
        assertNull(MiniJson.parseOrNull("{\"a\":1} trailing"))
    }

    @Test
    fun typedHelpersReadNestedFields() {
        val doc = MiniJson.parse(
            """{"usage":{"prompt_tokens":12,"total_tokens":30},"choices":[{"delta":{"content":"hey"}}]}"""
        ) as Map<String, Any?>
        assertEquals(12, doc.obj("usage")?.int("prompt_tokens"))
        assertEquals(30, doc.obj("usage")?.long("total_tokens")?.toInt())
        assertEquals("hey", doc.objList("choices").firstOrNull()?.obj("delta")?.str("content"))
        assertEquals("hey", doc.findFirst("content"))
        assertNull(doc.findFirst("nope"))
    }

    @Test
    fun unknownTypesFallBackToStringsRatherThanCrashing() {
        data class Thing(val x: Int)
        assertTrue(MiniJson.encode(Thing(3)).contains("Thing"))
    }
}
