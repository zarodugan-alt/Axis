package axis.agent

import axis.agent.http.AxisHttp
import axis.agent.protocol.ChatDelta
import axis.agent.protocol.ChatMessage
import axis.agent.protocol.ChatRequest
import axis.agent.protocol.ProtocolAdapters
import axis.agent.protocol.Role
import axis.agent.protocol.SseParser
import axis.agent.provider.AiProvider
import axis.agent.usage.TokenEstimator
import axis.agent.usage.UsageLedger
import axis.agent.usage.UsageRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A provider+model pair chosen for one request. */
data class Route(val provider: AiProvider, val model: String)

/**
 * The one place all model traffic flows through: streaming chat with
 * failover, key verification, and usage accounting.
 *
 * Key handling: the gateway never stores keys. [keyProvider] is a suspending
 * lookup the `:app` layer implements over the encrypted vault, so the plain
 * key exists only for the duration of a request.
 */
class LlmGateway(
    private val http: AxisHttp,
    private val ledger: UsageLedger,
    private val keyProvider: suspend (String) -> String?,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Streams a reply, trying [routes] in order. Failover only happens while
     * nothing has been shown to the user yet — once text has streamed, a
     * mid-flight provider error is surfaced instead of silently restarted
     * (which would duplicate the partial answer).
     */
    fun stream(
        messages: List<ChatMessage>,
        routes: List<Route>,
        temperature: Double = 0.4,
        maxTokens: Int = 1024,
        jsonMode: Boolean = false
    ): Flow<ChatDelta> = flow {
        if (routes.isEmpty()) {
            throw GatewayException("No AI provider is connected. Add a key in Settings → AI Providers.")
        }
        val promptTokens = TokenEstimator.estimate(messages)
        var lastError: Throwable? = null

        for (route in routes) {
            val started = now()
            var shown = false
            val completion = StringBuilder()
            var reportedUsage: axis.agent.protocol.TokenUsage? = null
            try {
                val req = ChatRequest(
                    provider = route.provider,
                    model = route.model,
                    messages = messages,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    jsonMode = jsonMode
                )
                streamOne(req).collect { delta ->
                    if (delta.usage != null) reportedUsage = delta.usage
                    if (delta.text.isNotEmpty()) {
                        shown = true
                        completion.append(delta.text)
                    }
                    emit(delta)
                }
                record(route, promptTokens, completion.length, reportedUsage, started, ok = true)
                return@flow
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (t: Throwable) {
                lastError = t
                record(
                    route, promptTokens, completion.length, reportedUsage, started,
                    ok = false, error = t.message ?: t::class.java.simpleName
                )
                if (shown) throw t
            }
        }
        throw GatewayException(
            "All providers failed. Last error: ${lastError?.message ?: "unknown"}"
        )
    }

    private fun record(
        route: Route,
        promptTokens: Int,
        completionChars: Int,
        usage: axis.agent.protocol.TokenUsage?,
        started: Long,
        ok: Boolean,
        error: String? = null
    ) {
        val completion = usage?.completionTokens ?: TokenEstimator.estimate("x".repeat(completionChars))
        ledger.record(
            UsageRecord(
                ts = now(),
                providerId = route.provider.id,
                model = route.model,
                kind = "chat",
                promptTokens = usage?.promptTokens ?: promptTokens,
                completionTokens = completion,
                approx = usage == null,
                latencyMs = now() - started,
                ok = ok,
                error = error
            )
        )
    }

    /** Not part of the public API: used by [stream]'s failover loop. */
    private fun streamOne(req: ChatRequest): Flow<ChatDelta> = flow {
        val adapter = ProtocolAdapters.of(req.provider.apiStyle)
        val key = if (req.provider.keyless) "" else (keyProvider(req.provider.id) ?: "")
        if (!req.provider.keyless && key.isBlank()) {
            throw GatewayException("No API key stored for ${req.provider.label}.")
        }
        val url = adapter.endpoint(req.provider, req.model)
        val parser = SseParser()
        var sawAny = false

        http.streamLines(url, adapter.headers(req.provider, key), adapter.body(req, stream = true))
            .collect { line ->
                val payload = parser.feed(line) ?: return@collect
                val delta = adapter.parseDelta(payload) ?: return@collect
                if (delta.finishReason?.startsWith("error:") == true) {
                    throw GatewayException(delta.finishReason.removePrefix("error:"))
                }
                sawAny = true
                emit(delta)
            }
        parser.flush()?.let { tail ->
            adapter.parseDelta(tail)?.let { emit(it) }
        }
        if (!sawAny) throw GatewayException("${req.provider.label} returned an empty stream.")
    }

    /** One-shot completion (title generation, key tests, summaries). */
    suspend fun complete(
        messages: List<ChatMessage>,
        route: Route,
        temperature: Double = 0.2,
        maxTokens: Int = 512,
        jsonMode: Boolean = false
    ): String {
        val adapter = ProtocolAdapters.of(route.provider.apiStyle)
        val key = if (route.provider.keyless) "" else (keyProvider(route.provider.id) ?: "")
        if (!route.provider.keyless && key.isBlank()) {
            throw GatewayException("No API key stored for ${route.provider.label}.")
        }
        val req = ChatRequest(route.provider, route.model, messages, temperature, maxTokens, jsonMode)
        val started = now()
        val res = http.post(
            adapter.endpoint(route.provider, route.model).replace(":streamGenerateContent?alt=sse", ":generateContent"),
            adapter.headers(route.provider, key),
            adapter.body(req, stream = false)
        )
        val error = if (res.ok) adapter.extractError(res.body) else (adapter.extractError(res.body) ?: "HTTP ${res.code}")
        if (!res.ok || error != null) {
            ledger.record(
                UsageRecord(
                    ts = now(), providerId = route.provider.id, model = route.model, kind = "chat",
                    promptTokens = TokenEstimator.estimate(messages), completionTokens = 0,
                    approx = true, latencyMs = res.latencyMs, ok = false, error = error
                )
            )
            throw GatewayException(error ?: "Request failed")
        }
        val parsed = adapter.parseResponse(res.body)
        ledger.record(
            UsageRecord(
                ts = now(),
                providerId = route.provider.id,
                model = route.model,
                kind = "chat",
                promptTokens = parsed.usage?.promptTokens ?: TokenEstimator.estimate(messages),
                completionTokens = parsed.usage?.completionTokens ?: TokenEstimator.estimate(parsed.text),
                approx = parsed.usage == null,
                latencyMs = res.latencyMs,
                ok = true
            )
        )
        return parsed.text
    }

    /**
     * Live key check: the smallest possible real request ("ping"). Returns
     * the model's reply on success, or the provider's own error message —
     * which is far more useful than "invalid key".
     */
    suspend fun verifyKey(provider: AiProvider, model: String, key: String): Result<String> {
        val adapter = ProtocolAdapters.of(provider.apiStyle)
        val started = now()
        return try {
            val req = ChatRequest(
                provider = provider,
                model = model,
                messages = listOf(
                    ChatMessage(Role.SYSTEM, "Reply with the single word: ok"),
                    ChatMessage(Role.USER, "ping")
                ),
                temperature = 0.0,
                maxTokens = 8
            )
            val url = adapter.endpoint(provider, model)
                .replace(":streamGenerateContent?alt=sse", ":generateContent")
            val res = http.post(url, adapter.headers(provider, key), adapter.body(req, stream = false))
            val err = if (res.ok) adapter.extractError(res.body) else (adapter.extractError(res.body) ?: "HTTP ${res.code}")
            if (err != null) {
                ledger.record(
                    UsageRecord(now(), provider.id, model, "verify", 0, 0, true, res.latencyMs, false, err)
                )
                Result.failure(GatewayException(err))
            } else {
                val text = adapter.parseResponse(res.body).text.ifBlank { "ok" }
                ledger.record(
                    UsageRecord(now(), provider.id, model, "verify", 0, 0, true, res.latencyMs, true)
                )
                Result.success("Key works · ${text.trim().take(24)} · ${res.latencyMs}ms")
            }
        } catch (t: Throwable) {
            ledger.record(
                UsageRecord(now(), provider.id, model, "verify", 0, 0, true, now() - started, false, t.message)
            )
            Result.failure(t)
        }
    }

    /**
     * Ordered routes for a request (spec §F6). [preferred] wins if enabled;
     * otherwise the enabled list order is used, honouring the routing mode.
     */
    fun routes(
        enabled: List<AiProvider>,
        mode: axis.agent.provider.ProviderCatalog.RoutingMode,
        preferredId: String? = null
    ): List<Route> {
        val ordered = axis.agent.provider.ProviderCatalog.order(enabled, mode)
        val sorted = if (preferredId == null) ordered
        else ordered.sortedByDescending { if (it.id == preferredId) 1 else 0 }
        return sorted.map { Route(it, it.defaultModel) }
    }
}

/** Provider/gateway-level failure with a message safe to show the user. */
class GatewayException(message: String) : Exception(message)
