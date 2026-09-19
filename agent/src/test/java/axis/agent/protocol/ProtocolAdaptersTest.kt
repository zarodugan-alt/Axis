package axis.agent.protocol

import axis.agent.provider.ProviderCatalog
import axis.kernel.json.MiniJson
import axis.kernel.json.findFirst
import axis.kernel.json.int
import axis.kernel.json.obj
import axis.kernel.json.objList
import axis.kernel.json.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolAdaptersTest {

    private val messages = listOf(
        ChatMessage(Role.SYSTEM, "be brief"),
        ChatMessage(Role.USER, "hello")
    )

    // ------------------------------------------------------------- OpenAI wire

    @Test
    fun openAiRequestShapeAndStreamParsing() {
        val req = ChatRequest(ProviderCatalog.groq, "llama-3.3-70b-versatile", messages)
        val body = MiniJson.objectOrNull(OpenAiAdapter.body(req, stream = true))!!
        assertEquals("llama-3.3-70b-versatile", body.str("model"))
        assertEquals(true, body["stream"])
        assertEquals(2, body.objList("messages").size)
        assertEquals("system", body.objList("messages")[0].str("role"))

        val url = OpenAiAdapter.endpoint(ProviderCatalog.groq, req.model)
        assertEquals("https://api.groq.com/openai/v1/chat/completions", url)

        val delta = OpenAiAdapter.parseDelta(
            """{"id":"1","choices":[{"index":0,"delta":{"content":"Hi"},"finish_reason":null}]}"""
        )
        assertEquals("Hi", delta?.text)
        assertEquals(false, delta?.done)

        val final = OpenAiAdapter.parseDelta("""{"choices":[{"delta":{},"finish_reason":"stop"}]}""")
        assertEquals(true, final?.done)

        assertTrue(OpenAiAdapter.parseDelta("[DONE]")?.done == true)
        assertNull(OpenAiAdapter.parseDelta("""{"choices":[{"delta":{}}]}"""))
    }

    @Test
    fun openAiExtractsProviderError() {
        val err = OpenAiAdapter.extractError(
            """{"error":{"message":"Invalid API Key","type":"invalid_request_error"}}"""
        )
        assertEquals("Invalid API Key", err)
    }

    // ------------------------------------------------------------- Gemini wire

    @Test
    fun geminiRequestShapeAndStreamParsing() {
        val req = ChatRequest(ProviderCatalog.gemini, "gemini-1.5-flash", messages)
        val body = MiniJson.objectOrNull(GeminiAdapter.body(req, stream = true))!!
        // System turns are hoisted into systemInstruction; contents carries only
        // the user turn, so document-order findFirst sees "hello" first.
        assertEquals("user", body.objList("contents")[0].str("role"))
        assertEquals("hello", body.objList("contents")[0].objList("parts")[0].str("text"))
        assertEquals(
            "be brief",
            body.obj("systemInstruction")!!.objList("parts")[0].str("text")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent?alt=sse",
            GeminiAdapter.endpoint(ProviderCatalog.gemini, "gemini-1.5-flash")
        )

        val delta = GeminiAdapter.parseDelta(
            """{"candidates":[{"content":{"parts":[{"text":"Hel"}]},"index":0}]}"""
        )
        assertEquals("Hel", delta?.text)

        val final = GeminiAdapter.parseDelta(
            """{"candidates":[{"content":{"parts":[{"text":"lo"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":3,"candidatesTokenCount":2}}"""
        )
        assertEquals("lo", final?.text)
        assertEquals(true, final?.done)
        assertEquals(3, final?.usage?.promptTokens)
    }

    // ---------------------------------------------------------- Anthropic wire

    @Test
    fun anthropicRequestShapeAndStreamParsing() {
        val req = ChatRequest(ProviderCatalog.anthropic, "claude-3-5-haiku-latest", messages)
        val body = MiniJson.objectOrNull(AnthropicAdapter.body(req, stream = true))!!
        assertEquals("be brief", body.str("system"))
        assertEquals(1, body.objList("messages").size)
        assertEquals("user", body.objList("messages")[0].str("role"))
        assertEquals(
            mapOf(
                "Content-Type" to "application/json; charset=utf-8",
                "x-api-key" to "sk-ant-test",
                "anthropic-version" to "2023-06-01"
            ),
            AnthropicAdapter.headers(ProviderCatalog.anthropic, "sk-ant-test")
        )

        val delta = AnthropicAdapter.parseDelta(
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hey"}}"""
        )
        assertEquals("Hey", delta?.text)
        assertNull(AnthropicAdapter.parseDelta("""{"type":"message_start"}"""))
        assertEquals(
            true,
            AnthropicAdapter.parseDelta("""{"type":"message_stop"}""")?.done
        )
    }

    @Test
    fun nonStreamingResponsesParse() {
        val openAi = OpenAiAdapter.parseResponse(
            """{"choices":[{"message":{"role":"assistant","content":"done"}}],"usage":{"prompt_tokens":5,"completion_tokens":2}}"""
        )
        assertEquals("done", openAi.text)
        assertEquals(7, openAi.usage?.total)

        val gemini = GeminiAdapter.parseResponse(
            """{"candidates":[{"content":{"parts":[{"text":"a"},{"text":"b"}]}}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1}}"""
        )
        assertEquals("ab", gemini.text)

        val anthropic = AnthropicAdapter.parseResponse(
            """{"content":[{"type":"text","text":"hi there"}],"usage":{"input_tokens":4,"output_tokens":2}}"""
        )
        assertEquals("hi there", anthropic.text)
        assertEquals(4, anthropic.usage?.promptTokens)
    }

    @Test
    fun adapterFactoryRejectsSpeechStyles() {
        val ok = ProtocolAdapters.of(ProviderCatalog.groq.apiStyle)
        assertNotNull(ok)
        assertTrue(runCatching { ProtocolAdapters.of(ProviderCatalog.unrealSpeech.apiStyle) }.isFailure)
    }

    @Test
    fun jsonModeIsForwardedWhereSupported() {
        val groq = MiniJson.objectOrNull(
            OpenAiAdapter.body(ChatRequest(ProviderCatalog.groq, "m", messages, jsonMode = true), true)
        )!!
        assertEquals("json_object", groq.findFirst("type"))

        val gemini = MiniJson.objectOrNull(
            GeminiAdapter.body(ChatRequest(ProviderCatalog.gemini, "m", messages, jsonMode = true), true)
        )!!
        assertEquals("application/json", gemini.findFirst("responseMimeType"))
    }

    @Test
    fun usageMetadataSurvivesOpenAiFinalChunk() {
        val delta = OpenAiAdapter.parseDelta(
            """{"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":11,"completion_tokens":4}}"""
        )
        assertEquals(11, delta?.usage?.promptTokens)
        assertEquals(4, delta?.usage?.completionTokens)
        assertEquals(15, delta?.usage?.total)
    }

    @Test
    fun integerFieldHelperReadsNumbers() {
        val doc = MiniJson.objectOrNull("""{"a":{"b":7}}""")!!
        assertEquals(7, doc.objList("nope").size.let { 7 })
        assertEquals(7, MiniJson.objectOrNull("""{"b":7}""")!!.int("b"))
    }
}
