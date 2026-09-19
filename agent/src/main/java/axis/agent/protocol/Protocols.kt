package axis.agent.protocol

import axis.agent.provider.AiProvider
import axis.agent.provider.ApiStyle
import axis.kernel.json.MiniJson
import axis.kernel.json.int
import axis.kernel.json.list
import axis.kernel.json.obj
import axis.kernel.json.objList
import axis.kernel.json.str

enum class Role { SYSTEM, USER, ASSISTANT }

data class ChatMessage(val role: Role, val content: String)

data class TokenUsage(val promptTokens: Int, val completionTokens: Int) {
    val total: Int get() = promptTokens + completionTokens
}

/** One chat request: provider + model + full message list (stateless API). */
data class ChatRequest(
    val provider: AiProvider,
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.4,
    val maxTokens: Int = 1024,
    val jsonMode: Boolean = false
)

/** One streamed chunk. [done] marks the terminal chunk. */
data class ChatDelta(
    val text: String = "",
    val done: Boolean = false,
    val usage: TokenUsage? = null,
    val finishReason: String? = null
)

/** Non-streaming response plus provider-reported usage when present. */
data class ChatResult(val text: String, val usage: TokenUsage?)

/**
 * Translates AXIS's internal request shape into one provider wire format and
 * parses that provider's stream back. All implementations are pure string
 * functions — they are unit-tested against recorded provider payloads, and
 * the network lives behind [axis.agent.http.AxisHttp].
 */
interface ProtocolAdapter {
    fun endpoint(provider: AiProvider, model: String): String
    fun headers(provider: AiProvider, apiKey: String): Map<String, String>
    fun body(req: ChatRequest, stream: Boolean): String

    /** `null` for keep-alives/ignorable events; [ChatDelta.done] to finish. */
    fun parseDelta(payload: String): ChatDelta?

    fun parseResponse(body: String): ChatResult

    /** Human-readable provider error, or null when the body parsed fine. */
    fun extractError(body: String): String?
}

object ProtocolAdapters {
    fun of(style: ApiStyle): ProtocolAdapter = when (style) {
        ApiStyle.OPENAI -> OpenAiAdapter
        ApiStyle.GEMINI -> GeminiAdapter
        ApiStyle.ANTHROPIC -> AnthropicAdapter
        ApiStyle.UNREAL_SPEECH, ApiStyle.ELEVENLABS ->
            error("speech styles have no chat adapter")
    }
}

// --------------------------------------------------------------- OpenAI-style

/** Groq, Mistral, OpenAI, OpenRouter, Ollama, any compatible server. */
object OpenAiAdapter : ProtocolAdapter {

    override fun endpoint(provider: AiProvider, model: String): String =
        provider.baseUrl.trimEnd('/') + "/chat/completions"

    override fun headers(provider: AiProvider, apiKey: String): Map<String, String> {
        val h = mutableMapOf("Content-Type" to "application/json; charset=utf-8")
        if (apiKey.isNotBlank()) h["Authorization"] = "Bearer $apiKey"
        return h
    }

    override fun body(req: ChatRequest, stream: Boolean): String {
        val messages = req.messages.map { m ->
            linkedMapOf<String, Any?>(
                "role" to when (m.role) {
                    Role.SYSTEM -> "system"
                    Role.USER -> "user"
                    Role.ASSISTANT -> "assistant"
                },
                "content" to m.content
            )
        }
        val payload = linkedMapOf<String, Any?>(
            "model" to req.model,
            "messages" to messages,
            "temperature" to req.temperature,
            "max_tokens" to req.maxTokens,
            "stream" to stream
        )
        if (req.jsonMode) {
            payload["response_format"] = linkedMapOf<String, Any?>("type" to "json_object")
        }
        return MiniJson.encode(payload)
    }

    override fun parseDelta(payload: String): ChatDelta? {
        if (payload.trim() == "[DONE]") return ChatDelta(done = true)
        val doc = MiniJson.objectOrNull(payload) ?: return null
        if (doc["error"] != null) {
            val msg = doc.obj("error")?.str("message") ?: payload
            return ChatDelta(text = "", done = true, finishReason = "error:$msg")
        }
        val choice = doc.objList("choices").firstOrNull()
        val text = choice?.obj("delta")?.str("content").orEmpty()
        val finish = choice?.str("finish_reason")
        val usage = doc.obj("usage")?.let {
            TokenUsage(it.int("prompt_tokens") ?: 0, it.int("completion_tokens") ?: 0)
        }
        if (text.isEmpty() && finish == null && usage == null) return null
        return ChatDelta(
            text = text,
            done = finish != null,
            usage = usage,
            finishReason = finish
        )
    }

    override fun parseResponse(body: String): ChatResult {
        val doc = MiniJson.objectOrNull(body) ?: return ChatResult("", null)
        val text = doc.objList("choices").firstOrNull()
            ?.obj("message")?.str("content").orEmpty()
        val usage = doc.obj("usage")?.let {
            TokenUsage(it.int("prompt_tokens") ?: 0, it.int("completion_tokens") ?: 0)
        }
        return ChatResult(text, usage)
    }

    override fun extractError(body: String): String? {
        val doc = MiniJson.objectOrNull(body) ?: return null
        val err = doc["error"] ?: return null
        return when (err) {
            is String -> err
            is Map<*, *> -> (err["message"] as? String) ?: err.toString()
            else -> err.toString()
        }
    }
}

// --------------------------------------------------------------------- Gemini

object GeminiAdapter : ProtocolAdapter {

    override fun endpoint(provider: AiProvider, model: String): String {
        val m = model.removePrefix("models/")
        return provider.baseUrl.trimEnd('/') + "/models/" + m + ":streamGenerateContent?alt=sse"
    }

    override fun headers(provider: AiProvider, apiKey: String): Map<String, String> =
        mapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "x-goog-api-key" to apiKey
        )

    override fun body(req: ChatRequest, stream: Boolean): String {
        val system = req.messages.filter { it.role == Role.SYSTEM }
            .joinToString("\n") { it.content }
        val contents = req.messages.filter { it.role != Role.SYSTEM }.map { m ->
            linkedMapOf<String, Any?>(
                "role" to if (m.role == Role.ASSISTANT) "model" else "user",
                "parts" to listOf(linkedMapOf<String, Any?>("text" to m.content))
            )
        }
        val payload = linkedMapOf<String, Any?>(
            "contents" to contents,
            "generationConfig" to linkedMapOf<String, Any?>(
                "temperature" to req.temperature,
                "maxOutputTokens" to req.maxTokens,
                "topP" to 0.95
            )
        )
        if (req.jsonMode) {
            (payload["generationConfig"] as MutableMap<String, Any?>)["responseMimeType"] = "application/json"
        }
        if (system.isNotBlank()) {
            payload["systemInstruction"] =
                linkedMapOf<String, Any?>("parts" to listOf(linkedMapOf<String, Any?>("text" to system)))
        }
        return MiniJson.encode(payload)
    }

    override fun parseDelta(payload: String): ChatDelta? {
        val doc = MiniJson.objectOrNull(payload) ?: return null
        val text = contentText(doc)
        val finish = doc.objList("candidates").firstOrNull()?.str("finishReason")
        val usage = doc.obj("usageMetadata")?.let {
            TokenUsage(
                it.int("promptTokenCount") ?: 0,
                it.int("candidatesTokenCount") ?: 0
            )
        }
        if (text.isEmpty() && finish == null && usage == null) return null
        return ChatDelta(
            text = text,
            done = finish != null,
            usage = usage,
            finishReason = finish
        )
    }

    override fun parseResponse(body: String): ChatResult {
        val doc = MiniJson.objectOrNull(body) ?: return ChatResult("", null)
        val usage = doc.obj("usageMetadata")?.let {
            TokenUsage(it.int("promptTokenCount") ?: 0, it.int("candidatesTokenCount") ?: 0)
        }
        return ChatResult(contentText(doc), usage)
    }

    override fun extractError(body: String): String? {
        val doc = MiniJson.objectOrNull(body) ?: return null
        val err = doc.obj("error") ?: return null
        return err.str("message") ?: err.toString()
    }

    private fun contentText(doc: Map<String, Any?>): String {
        val candidate = doc.objList("candidates").firstOrNull() ?: return ""
        val parts = candidate.obj("content")?.list("parts").orEmpty()
        return parts.filterIsInstance<Map<String, Any?>>()
            .mapNotNull { it.str("text") }
            .joinToString("")
    }
}

// ------------------------------------------------------------------ Anthropic

object AnthropicAdapter : ProtocolAdapter {

    override fun endpoint(provider: AiProvider, model: String): String =
        provider.baseUrl.trimEnd('/') + "/messages"

    override fun headers(provider: AiProvider, apiKey: String): Map<String, String> = mapOf(
        "Content-Type" to "application/json; charset=utf-8",
        "x-api-key" to apiKey,
        "anthropic-version" to "2023-06-01"
    )

    override fun body(req: ChatRequest, stream: Boolean): String {
        val system = req.messages.filter { it.role == Role.SYSTEM }
            .joinToString("\n") { it.content }
        val messages = req.messages.filter { it.role != Role.SYSTEM }.map { m ->
            linkedMapOf<String, Any?>(
                "role" to if (m.role == Role.ASSISTANT) "assistant" else "user",
                "content" to m.content
            )
        }
        val payload = linkedMapOf<String, Any?>(
            "model" to req.model,
            "max_tokens" to req.maxTokens,
            "temperature" to req.temperature,
            "messages" to messages,
            "stream" to stream
        )
        if (system.isNotBlank()) payload["system"] = system
        return MiniJson.encode(payload)
    }

    override fun parseDelta(payload: String): ChatDelta? {
        val doc = MiniJson.objectOrNull(payload) ?: return null
        return when (doc.str("type")) {
            "content_block_delta" -> ChatDelta(text = doc.obj("delta")?.str("text").orEmpty())
            "message_delta" -> ChatDelta(
                done = true,
                usage = null,
                finishReason = doc.obj("delta")?.str("stop_reason")
            )
            "message_stop" -> ChatDelta(done = true)
            "error" -> ChatDelta(
                done = true,
                finishReason = "error:" + (doc.obj("error")?.str("message") ?: "unknown")
            )
            else -> null
        }
    }

    override fun parseResponse(body: String): ChatResult {
        val doc = MiniJson.objectOrNull(body) ?: return ChatResult("", null)
        val text = doc.list("content")
            .filterIsInstance<Map<String, Any?>>()
            .filter { it.str("type") == "text" }
            .mapNotNull { it.str("text") }
            .joinToString("")
        val usage = doc.obj("usage")?.let {
            TokenUsage(it.int("input_tokens") ?: 0, it.int("output_tokens") ?: 0)
        }
        return ChatResult(text, usage)
    }

    override fun extractError(body: String): String? {
        val doc = MiniJson.objectOrNull(body) ?: return null
        val err = doc.obj("error") ?: return null
        return err.str("message") ?: err.toString()
    }
}
