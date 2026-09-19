package axis.agent.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallParserTest {

    private val known = setOf("launch_app", "set_flashlight", "set_dnd")

    @Test
    fun parsesBareJsonCall() {
        val call = ToolCallParser.parse(
            """{"tool":"launch_app","args":{"package":"com.whatsapp"},"summary":"Open WhatsApp"}""",
            known
        )
        assertEquals("launch_app", call?.name)
        assertEquals("com.whatsapp", call?.args?.get("package"))
        assertEquals("Open WhatsApp", call?.summary)
    }

    @Test
    fun parsesFencedCallWithPreamble() {
        val text = """
            Sure — I'll open that for you.
            ```json
            {"tool": "launch_app", "args": {"package": "com.spotify.music"}, "summary": "Open Spotify"}
            ```
        """.trimIndent()
        val call = ToolCallParser.parse(text, known)
        assertEquals("launch_app", call?.name)
        assertEquals("com.spotify.music", call?.args?.get("package"))
    }

    @Test
    fun ignoresUnknownToolsAndProse() {
        assertNull(ToolCallParser.parse("""{"tool":"rm_rf","args":{}}""", known))
        assertNull(ToolCallParser.parse("It is 4pm and the battery is at 62%.", known))
        assertNull(ToolCallParser.parse("", known))
        assertNull(ToolCallParser.parse("{\"tool\":", known))
    }

    @Test
    fun findsObjectEmbeddedInProseBrackets() {
        val text = "Using [ {\"tool\":\"set_dnd\",\"args\":{\"on\":true}} ] now."
        assertEquals("set_dnd", ToolCallParser.parse(text, known)?.name)
    }

    @Test
    fun nestedArgsDoNotBreakBraceMatching() {
        val text = """{"tool":"launch_app","args":{"package":"a","extra":{"x":[1,{"y":"}"}]}}}"""
        val call = ToolCallParser.parse(text, known)
        assertEquals("launch_app", call?.name)
        assertEquals("a", call?.args?.get("package"))
    }

    @Test
    fun snifferHoldsJsonUntilDecided() {
        val s = ToolSniffer()
        assertEquals("", s.accept("{"))
        assertEquals("", s.accept("\"tool\":\"set_dnd\""))
        assertTrue(s.isToolCall)
        assertTrue(s.buffered().startsWith("{"))
    }

    @Test
    fun snifferReleasesProseImmediately() {
        val s = ToolSniffer()
        assertEquals("Add", s.accept("Add"))
        assertFalse(s.isToolCall)
        assertEquals(" 2 eggs", s.accept(" 2 eggs"))
    }

    @Test
    fun snifferFlushesLeadingWhitespaceThenLetters() {
        val s = ToolSniffer()
        assertEquals("", s.accept("  "))
        assertEquals("  Sure", s.accept("Sure"))
        assertFalse(s.isToolCall)
    }

    @Test
    fun snifferGivesUpHoldingWhenToolJsonGrowsTooLong() {
        val s = ToolSniffer(holdLimit = 12)
        assertEquals("", s.accept("{\"tool\":"))
        assertTrue(s.isToolCall)
        val released = s.accept("\"launch_app_and_keep_talking_forever")
        assertTrue(released.startsWith("{\"tool\":"))
        assertFalse(s.isToolCall)
        // Once flushed, later chunks pass straight through.
        assertEquals("!", s.accept("!"))
    }
}
