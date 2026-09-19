package axis.app.data

import android.content.Context
import axis.agent.usage.UsageRecord
import axis.agent.usage.UsageSink
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Durable usage log for Settings → Usage.
 *
 * Storage is one JSON object per line (`usage.jsonl`) in the app's private
 * files dir — append-only, trivially exported, and bounded by trimming to
 * [MAX_LINES] on load. No database: the dashboard only ever needs the recent
 * window plus aggregates, which [axis.agent.usage.UsageLedger] computes.
 */
@Singleton
class UsageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : UsageSink {

    private val file: File get() = File(context.filesDir, "usage.jsonl")
    private val lock = Any()

    override suspend fun append(record: UsageRecord) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                runCatching { file.appendText(encode(record) + "\n") }
            }
        }
    }

    /** Loads the most recent records (newest last). Safe to call at startup. */
    suspend fun load(): List<UsageRecord> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching emptyList()
            val lines = file.readLines()
            if (lines.size > MAX_LINES) {
                // Trim once, on load, so the file cannot grow without bound.
                val keep = lines.takeLast(MAX_LINES)
                runCatching { file.writeText(keep.joinToString("\n") + "\n") }
                keep.mapNotNull(::decode)
            } else {
                lines.mapNotNull(::decode)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { file.delete() }
        Unit
    }

    /** Exported text for Settings → Advanced → Export. */
    suspend fun exportText(): String = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")
    }

    private fun encode(r: UsageRecord): String = axis.kernel.json.MiniJson.encode(
        linkedMapOf<String, Any?>(
            "ts" to r.ts,
            "provider" to r.providerId,
            "model" to r.model,
            "kind" to r.kind,
            "pt" to r.promptTokens,
            "ct" to r.completionTokens,
            "approx" to r.approx,
            "ms" to r.latencyMs,
            "ok" to r.ok,
            "err" to r.error
        )
    )

    private fun decode(line: String): UsageRecord? {
        val doc = axis.kernel.json.MiniJson.objectOrNull(line) ?: return null
        val provider = doc["provider"] as? String ?: return null
        return UsageRecord(
            ts = (doc["ts"] as? Number)?.toLong() ?: 0L,
            providerId = provider,
            model = doc["model"] as? String ?: "",
            kind = doc["kind"] as? String ?: "chat",
            promptTokens = (doc["pt"] as? Number)?.toInt() ?: 0,
            completionTokens = (doc["ct"] as? Number)?.toInt() ?: 0,
            approx = doc["approx"] as? Boolean ?: true,
            latencyMs = (doc["ms"] as? Number)?.toLong() ?: 0L,
            ok = doc["ok"] as? Boolean ?: false,
            error = doc["err"] as? String
        )
    }

    companion object {
        private const val MAX_LINES = 800
    }
}
