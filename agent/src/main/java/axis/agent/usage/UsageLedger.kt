package axis.agent.usage

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One gateway request, success or failure. Never contains prompt text. */
data class UsageRecord(
    val ts: Long,
    val providerId: String,
    val model: String,
    /** "chat" | "tool" | "tts" | "asr" | "verify" */
    val kind: String,
    val promptTokens: Int,
    val completionTokens: Int,
    /** True when token counts are estimated (chars/4) rather than reported. */
    val approx: Boolean,
    val latencyMs: Long,
    val ok: Boolean,
    val error: String? = null
) {
    val totalTokens: Int get() = promptTokens + completionTokens
}

/** Persistence seam so `:agent` stays free of Android types. */
interface UsageSink {
    suspend fun append(record: UsageRecord)
}

data class ProviderTotals(
    val providerId: String,
    val requests: Int = 0,
    val failures: Int = 0,
    val tokens: Long = 0,
    val avgLatencyMs: Long = 0,
    val lastUsedAt: Long = 0
)

/**
 * In-memory ring of the most recent [capacity] records plus live aggregates —
 * the data behind Settings → Usage. The `:app` layer supplies a [UsageSink]
 * that persists records to disk and seeds the ledger at startup.
 */
class UsageLedger(
    private val sink: UsageSink? = null,
    private val capacity: Int = 500
) {
    private val lock = Any()
    private val buffer = CopyOnWriteArrayList<UsageRecord>()
    private val _records = MutableStateFlow<List<UsageRecord>>(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val records: StateFlow<List<UsageRecord>> = _records

    /** Fills the ring from persistent storage (called once, at app start). */
    fun seed(existing: List<UsageRecord>) {
        val capped = existing.takeLast(capacity)
        buffer.clear()
        buffer.addAll(capped)
        _records.value = capped.toList()
    }

    fun record(record: UsageRecord) {
        synchronized(lock) {
            buffer.add(record)
            while (buffer.size > capacity) buffer.removeAt(0)
            _records.value = buffer.toList()
        }
        val s = sink ?: return
        scope.launch { runCatching { s.append(record) } }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _records.value = emptyList()
        }
    }

    /** Aggregate view keyed by provider, most-used first. */
    fun totals(): Map<String, ProviderTotals> =
        _records.value.groupBy { it.providerId }.mapValues { (id, list) ->
            ProviderTotals(
                providerId = id,
                requests = list.size,
                failures = list.count { !it.ok },
                tokens = list.sumOf { it.totalTokens.toLong() },
                avgLatencyMs = if (list.isEmpty()) 0 else list.sumOf { it.latencyMs } / list.size,
                lastUsedAt = list.maxOf { it.ts }
            )
        }

    /** Rolling totals for the dashboard header. */
    val summary: StateFlow<UsageSummary> = _records
        .map { list ->
            UsageSummary(
                requests = list.size,
                failures = list.count { !it.ok },
                tokens = list.sumOf { it.totalTokens.toLong() },
                avgLatencyMs = if (list.isEmpty()) 0 else list.sumOf { it.latencyMs } / list.size,
                last24h = list.count { System.currentTimeMillis() - it.ts < 24 * 3600_000L }
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, UsageSummary())
}

data class UsageSummary(
    val requests: Int = 0,
    val failures: Int = 0,
    val tokens: Long = 0,
    val avgLatencyMs: Long = 0,
    val last24h: Int = 0
)

/** Per-session latency samples for the Side-Car sparkline. */
fun List<UsageRecord>.latencySeries(limit: Int = 40): List<Float> {
    val recent = takeLast(limit).map { it.latencyMs.toFloat() }
    val max = recent.maxOrNull()?.takeIf { it > 0f } ?: 1f
    return recent.map { it / max }
}

/**
 * Token estimate used when a provider does not report usage (all streaming
 * providers except OpenAI/Gemini). ~4 chars per token is the standard rule
 * of thumb; every record carrying an estimate is flagged `approx`.
 */
object TokenEstimator {
    fun estimate(text: String): Int = if (text.isEmpty()) 1 else (text.length / 4).coerceAtLeast(1)

    fun estimate(messages: List<axis.agent.protocol.ChatMessage>): Int =
        messages.sumOf { estimate(it.content) + 4 }
}
