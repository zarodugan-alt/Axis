package axis.agent

import axis.agent.http.AxisHttp
import axis.agent.http.HttpResult
import axis.agent.protocol.ChatMessage
import axis.agent.protocol.Role
import axis.agent.provider.ProviderCatalog
import axis.agent.tool.AgentTool
import axis.agent.tool.ToolDescriptor
import axis.agent.tool.ToolOutcome
import axis.agent.tool.ToolRegistry
import axis.agent.usage.UsageLedger
import axis.kernel.agent.GateDecision
import axis.kernel.agent.RiskLevel
import axis.kernel.agent.ToolCallSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake transport: each call returns the next canned SSE script. */
private class ScriptedHttp(private val scripts: MutableList<List<String>>, private val fail: Boolean = false) : AxisHttp {
    var calls = 0
    override fun streamLines(url: String, headers: Map<String, String>, body: String): Flow<String> = flow {
        calls++
        if (fail) throw IllegalStateException("boom")
        val script = scripts.removeFirst()
        script.forEach { emit(it) }
    }

    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResult =
        HttpResult(200, "{}", 1)

    override suspend fun postBytes(url: String, headers: Map<String, String>, body: String): Result<ByteArray> =
        Result.success(ByteArray(0))
}

private class FakeTool(private val name: String, private val risk: RiskLevel = RiskLevel.LOW) : AgentTool {
    var executions = 0
    override val descriptor = ToolDescriptor(
        name = name,
        description = "test tool",
        params = emptyList(),
        risk = risk
    )

    override suspend fun execute(args: Map<String, Any?>): ToolOutcome {
        executions++
        return ToolOutcome.ok("did $name")
    }
}

class AgentLoopTest {

    private fun sse(vararg payloads: String): List<String> =
        payloads.flatMap { listOf("data: $it", "") }

    private fun gateway(http: AxisHttp, ledger: UsageLedger = UsageLedger()) = LlmGateway(
        http = http,
        ledger = ledger,
        keyProvider = { "test-key-1234567890" }
    )

    private fun loop(
        http: AxisHttp,
        tools: List<AgentTool>,
        decision: GateDecision = GateDecision.Allow(),
        ledger: UsageLedger = UsageLedger()
    ) = AgentLoop(
        gateway = gateway(http, ledger),
        registry = ToolRegistry(tools),
        gate = object : ToolGate {
            override suspend fun check(call: ToolCallSpec, tool: AgentTool?) = decision
        }
    )

    @Test
    fun proseReplyStreamsThroughAndEndsTurn() = runTest {
        val http = ScriptedHttp(
            mutableListOf(
                sse(
                    """{"choices":[{"delta":{"content":"Battery "}}]}""",
                    """{"choices":[{"delta":{"content":"is 62%."},"finish_reason":"stop"}]}"""
                )
            )
        )
        val events = loop(http, emptyList()).run(
            system = "sys",
            history = listOf(ChatMessage(Role.USER, "how much battery?")),
            routes = listOf(Route(ProviderCatalog.groq, "llama-3.3-70b-versatile"))
        ).toList()

        val text = events.filterIsInstance<AgentEvent.Delta>().joinToString("") { it.text }
        assertEquals("Battery is 62%.", text)
        val done = events.filterIsInstance<AgentEvent.Done>().single()
        assertEquals(0, done.steps)
        assertTrue(events.none { it is AgentEvent.Failed })
    }

    @Test
    fun toolCallRunsThenSecondTurnAnswersInProse() = runTest {
        val tool = FakeTool("launch_app")
        val http = ScriptedHttp(
            mutableListOf(
                sse(
                    """{"choices":[{"delta":{"content":"{\"tool\":\"launch_app\",\"args\":"}}]}""",
                    """{"choices":[{"delta":{"content":"{\"package\":\"com.x\"},\"summary\":\"Open X\"}"},"finish_reason":"stop"}]}"""
                ),
                sse("""{"choices":[{"delta":{"content":"Opened X."},"finish_reason":"stop"}]}""")
            )
        )
        val events = loop(http, listOf(tool)).run(
            system = "sys",
            history = listOf(ChatMessage(Role.USER, "open X")),
            routes = listOf(Route(ProviderCatalog.groq, "llama-3.3"))
        ).toList()

        assertEquals(1, tool.executions)
        // Raw tool JSON must never be shown as prose.
        val text = events.filterIsInstance<AgentEvent.Delta>().joinToString("") { it.text }
        assertEquals("Opened X.", text)
        assertTrue(events.filterIsInstance<AgentEvent.ToolStarted>().single().label == "Open X")
        assertEquals("did launch_app", events.filterIsInstance<AgentEvent.ToolFinished>().single().result.output)
        assertEquals(1, events.filterIsInstance<AgentEvent.Done>().single().steps)
    }

    @Test
    fun deniedToolIsReportedAndDoesNotExecute() = runTest {
        val tool = FakeTool("launch_app")
        val http = ScriptedHttp(
            mutableListOf(
                sse("""{"choices":[{"delta":{"content":"{\"tool\":\"launch_app\",\"args\":{}}"},"finish_reason":"stop"}]}"""),
                sse("""{"choices":[{"delta":{"content":"Okay, I won't."},"finish_reason":"stop"}]}""")
            )
        )
        val events = loop(http, listOf(tool), GateDecision.Deny("kill switch")).run(
            system = "sys",
            history = listOf(ChatMessage(Role.USER, "open X")),
            routes = listOf(Route(ProviderCatalog.groq, "m"))
        ).toList()

        assertEquals(0, tool.executions)
        assertEquals("kill switch", events.filterIsInstance<AgentEvent.ToolDenied>().single().reason)
        assertEquals("Okay, I won't.", events.filterIsInstance<AgentEvent.Delta>().joinToString("") { it.text })
    }

    @Test
    fun providerFailureSurfacesAsFailedEvent() = runTest {
        val http = ScriptedHttp(mutableListOf(), fail = true)
        val events = loop(http, emptyList()).run(
            system = "sys",
            history = listOf(ChatMessage(Role.USER, "hi")),
            routes = listOf(Route(ProviderCatalog.groq, "m"))
        ).toList()

        assertTrue(events.filterIsInstance<AgentEvent.Failed>().single().message.contains("boom"))
        assertTrue(events.none { it is AgentEvent.Done })
    }

    @Test
    fun unknownToolNameIsDeniedNotCrashed() = runTest {
        val http = ScriptedHttp(
            mutableListOf(
                sse("""{"choices":[{"delta":{"content":"{\"tool\":\"ghost\",\"args\":{}}"},"finish_reason":"stop"}]}""")
            )
        )
        val events = loop(http, emptyList()).run(
            system = "sys",
            history = listOf(ChatMessage(Role.USER, "do it")),
            routes = listOf(Route(ProviderCatalog.groq, "m"))
        ).toList()
        // Not a known tool → parser ignores it, turn ends as prose (empty).
        assertTrue(events.filterIsInstance<AgentEvent.ToolDenied>().isEmpty())
        assertTrue(events.last() is AgentEvent.Done)
    }

    @Test
    fun usageIsRecordedForEveryRequest() = runTest {
        val ledger = UsageLedger()
        val http = ScriptedHttp(
            mutableListOf(sse("""{"choices":[{"delta":{"content":"hi"},"finish_reason":"stop"}]}"""))
        )
        loop(http, emptyList(), ledger = ledger).run(
            system = "sys",
            history = listOf(ChatMessage(Role.USER, "hi")),
            routes = listOf(Route(ProviderCatalog.groq, "llama-3.3"))
        ).toList()

        val rec = ledger.records.value.single()
        assertEquals("groq", rec.providerId)
        assertTrue(rec.ok)
        assertTrue(rec.totalTokens > 0)
    }
}
