package axis.agent.speech

import axis.agent.GatewayException
import axis.agent.http.AxisHttp
import axis.agent.provider.AiProvider
import axis.agent.provider.ApiStyle
import axis.agent.usage.UsageLedger
import axis.agent.usage.UsageRecord
import axis.agent.usage.TokenEstimator
import axis.kernel.json.MiniJson

/**
 * Cloud text-to-speech for the voice HUD. AXIS always falls back to the
 * on-device [android.speech.tts.TextToSpeech] engine, so a missing key or a
 * dead endpoint degrades to the offline voice instead of silence.
 *
 * Both supported providers return raw MP3 bytes from a single POST.
 */
class TtsClient(
    private val http: AxisHttp,
    private val ledger: UsageLedger,
    private val keyProvider: suspend (String) -> String?,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun synthesize(
        provider: AiProvider,
        text: String,
        voiceId: String = provider.defaultModel
    ): Result<ByteArray> {
        if (!provider.isSpeech) {
            return Result.failure(GatewayException("${provider.label} is not a speech provider"))
        }
        val key = if (provider.keyless) "" else (keyProvider(provider.id) ?: "")
        if (!provider.keyless && key.isBlank()) {
            return Result.failure(GatewayException("No API key stored for ${provider.label}."))
        }

        val started = now()
        val url: String
        val body: String
        val headers: MutableMap<String, String>

        when (provider.apiStyle) {
            ApiStyle.UNREAL_SPEECH -> {
                url = provider.baseUrl.trimEnd('/') + "/stream"
                headers = mutableMapOf(
                    "Authorization" to "Bearer $key",
                    "Content-Type" to "application/json; charset=utf-8"
                )
                body = MiniJson.encode(
                    linkedMapOf<String, Any?>(
                        "Text" to text.take(3000),
                        "VoiceId" to voiceId,
                        "Bitrate" to "192k",
                        "Speed" to 0.0,
                        "Pitch" to 1.0,
                        "TimestampType" to "none"
                    )
                )
            }
            ApiStyle.ELEVENLABS -> {
                url = provider.baseUrl.trimEnd('/') + "/text-to-speech/$voiceId"
                headers = mutableMapOf(
                    "xi-api-key" to key,
                    "Content-Type" to "application/json; charset=utf-8",
                    "Accept" to "audio/mpeg"
                )
                body = MiniJson.encode(
                    linkedMapOf<String, Any?>(
                        "text" to text.take(3000),
                        "model_id" to "eleven_turbo_v2",
                        "voice_settings" to linkedMapOf<String, Any?>(
                            "stability" to 0.4,
                            "similarity_boost" to 0.8
                        )
                    )
                )
            }
            else -> return Result.failure(GatewayException("Unsupported speech style"))
        }

        val result = http.postBytes(url, headers, body)
        val chars = text.length
        if (result.isSuccess) {
            ledger.record(
                UsageRecord(
                    ts = now(), providerId = provider.id, model = voiceId, kind = "tts",
                    promptTokens = TokenEstimator.estimate("x".repeat(chars)), completionTokens = 0,
                    approx = true, latencyMs = now() - started, ok = true
                )
            )
        } else {
            ledger.record(
                UsageRecord(
                    ts = now(), providerId = provider.id, model = voiceId, kind = "tts",
                    promptTokens = chars, completionTokens = 0, approx = true,
                    latencyMs = now() - started, ok = false,
                    error = result.exceptionOrNull()?.message
                )
            )
        }
        return result
    }
}
