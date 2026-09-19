package axis.agent.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest {

    private fun feedAll(lines: List<String>): List<String> {
        val p = SseParser()
        val out = mutableListOf<String>()
        lines.forEach { p.feed(it)?.let { payload -> out.add(payload) } }
        p.flush()?.let { out.add(it) }
        return out
    }

    @Test
    fun singleLineEventsAreSplitOnBlankLines() {
        val payloads = feedAll(
            listOf(
                "data: {\"a\":1}",
                "",
                "data: {\"a\":2}",
                "",
                "data: [DONE]",
                ""
            )
        )
        assertEquals(listOf("{\"a\":1}", "{\"a\":2}", "[DONE]"), payloads)
    }

    @Test
    fun ignoresCommentsEventsAndIds() {
        val payloads = feedAll(
            listOf(
                ": ping",
                "event: message",
                "id: 42",
                "data: hello",
                "",
                "",
                ": keep-alive"
            )
        )
        assertEquals(listOf("hello"), payloads)
    }

    @Test
    fun multiLineDataJoinsWithNewline() {
        val payloads = feedAll(listOf("data: line one", "data: line two", ""))
        assertEquals(listOf("line one\nline two"), payloads)
    }

    @Test
    fun crlfStreamsAndMissingSpaceAreHandled() {
        val payloads = feedAll(listOf("data:{\"x\":1}\r", "\r"))
        assertEquals(listOf("{\"x\":1}"), payloads)
    }

    @Test
    fun unterminatedEventIsFlushedAtEndOfStream() {
        val payloads = feedAll(listOf("data: {\"tail\":true}"))
        assertEquals(listOf("{\"tail\":true}"), payloads)
    }

    @Test
    fun noDataYieldsNothing() {
        val p = SseParser()
        assertNull(p.feed(": only comments"))
        assertNull(p.flush())
    }
}
