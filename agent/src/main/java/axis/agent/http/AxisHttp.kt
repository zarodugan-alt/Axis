package axis.agent.http

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Response of a buffered (non-streaming) request. */
data class HttpResult(val code: Int, val body: String, val latencyMs: Long) {
    val ok: Boolean get() = code in 200..299
}

/** Thrown by the streaming path so callers can show a real provider error. */
class HttpException(
    val code: Int,
    val body: String,
    message: String = "HTTP $code"
) : Exception(message)

/**
 * The single network seam of the whole app. Implementations must never log
 * request bodies — they carry API keys.
 */
interface AxisHttp {

    /** Streams `data:`-style lines (raw lines, SSE prefixes intact). */
    fun streamLines(url: String, headers: Map<String, String>, body: String): Flow<String>

    /** Buffered POST; returns the response even for non-2xx (callers decide). */
    suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResult

    /** POST expecting binary (audio). Non-2xx yields a failed [Result]. */
    suspend fun postBytes(url: String, headers: Map<String, String>, body: String): Result<ByteArray>
}

/**
 * [HttpURLConnection] implementation — no OkHttp dependency on purpose:
 * AXIS ships as a launcher (tiny, always resident) and only needs POST +
 * SSE line streaming, which the platform stack handles fine. Timeouts are
 * aggressive so the agent fails over quickly instead of hanging the HUD.
 */
class UrlConnectionAxisHttp(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 60_000
) : AxisHttp {

    override fun streamLines(
        url: String,
        headers: Map<String, String>,
        body: String
    ): Flow<String> = flow {
        val conn = open(url, headers, stream = true)
        try {
            writeBody(conn, body)
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = (conn.errorStream ?: conn.inputStream)?.use { it.readBytes().decodeToString() }
                throw HttpException(code, err.orEmpty(), errorMessage(code, err))
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            reader.use { r ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = r.readLine() ?: break
                    emit(line)
                }
            }
        } finally {
            runCatching { conn.disconnect() }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String
    ): HttpResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val conn = open(url, headers, stream = false)
        try {
            writeBody(conn, body)
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.use { it.readBytes().decodeToString() }
                .orEmpty()
            HttpResult(code, text, System.currentTimeMillis() - start)
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    override suspend fun postBytes(
        url: String,
        headers: Map<String, String>,
        body: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val conn = open(url, headers, stream = false)
        try {
            writeBody(conn, body)
            val code = conn.responseCode
            if (code in 200..299) {
                Result.success(conn.inputStream.use { it.readBytes() })
            } else {
                val err = conn.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
                Result.failure(HttpException(code, err, errorMessage(code, err)))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun open(
        url: String,
        headers: Map<String, String>,
        stream: Boolean
    ): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Accept", if (stream) "text/event-stream" else "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return conn
    }

    private fun writeBody(conn: HttpURLConnection, body: String) {
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    }

    /** Provider errors are JSON with a `message`/`error` field; extract it. */
    private fun errorMessage(code: Int, body: String?): String {
        val raw = body?.take(400).orEmpty()
        val hint = when (code) {
            401, 403 -> " — key rejected"
            429 -> " — rate limited"
            404 -> " — model or endpoint not found"
            500, 502, 503, 504 -> " — provider is down"
            else -> ""
        }
        return "HTTP $code$hint${if (raw.isBlank()) "" else ": $raw"}"
    }
}
