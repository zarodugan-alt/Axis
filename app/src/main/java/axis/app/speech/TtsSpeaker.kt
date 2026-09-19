package axis.app.speech

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import axis.agent.speech.TtsClient
import axis.agent.provider.AiProvider
import axis.app.data.ProviderStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Speech output with an honest hierarchy (spec §F16):
 *  1. the cloud voice the user picked (Unreal Speech / ElevenLabs), when a
 *     key exists and the request succeeds,
 *  2. otherwise the on-device TextToSpeech engine,
 *  3. otherwise silence plus a returned reason.
 *
 * Every call reports which path it took so the voice HUD can show it.
 */
class TtsSpeaker(
    private val context: Context,
    private val client: TtsClient,
    private val store: ProviderStore
) {
    private var engine: TextToSpeech? = null
    private var engineReady = false
    private var player: MediaPlayer? = null
    private var lastCloudFile: File? = null

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    /** Lazily boots the platform engine; safe to call repeatedly. */
    fun warmUp() {
        if (engine != null) return
        engine = TextToSpeech(context) { status ->
            engineReady = status == TextToSpeech.SUCCESS
            engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { _speaking.value = true }
                override fun onDone(utteranceId: String?) { _speaking.value = false }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { _speaking.value = false }
                override fun onError(utteranceId: String?, errorCode: Int) { _speaking.value = false }
            })
        }
    }

    suspend fun speak(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "nothing to say"

        warnIfNoEngine()
        val provider = cloudProvider()
        if (provider != null) {
            val result = client.synthesize(provider, trimmed)
            val bytes = result.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                val played = playBytes(bytes)
                if (played) return "spoken by ${provider.label}"
            } else {
                Timber.w("cloud TTS failed: ${result.exceptionOrNull()?.message}")
            }
        }
        return onDevice(trimmed)
    }

    fun stop() {
        runCatching { engine?.stop() }
        runCatching { player?.stop(); player?.release() }
        player = null
        _speaking.value = false
    }

    fun shutdown() {
        stop()
        runCatching { engine?.shutdown() }
        engine = null
        engineReady = false
    }

    /** On-device voices, for the Settings → Voice list. */
    val onDeviceVoices: List<String>
        get() = engine?.voices
            ?.filter { it.locale?.language == java.util.Locale.getDefault().language }
            ?.map { it.name }
            ?.distinct()
            ?.sorted()
            .orEmpty()

    private suspend fun cloudProvider(): AiProvider? {
        val id = store.speechProviderId.value ?: return null
        val provider = store.providers.value.firstOrNull { it.id == id } ?: return null
        if (!provider.isSpeech) return null
        val hasKey = !provider.keyless && store.key(id) != null
        return if (provider.keyless || hasKey) provider else null
    }

    private suspend fun playBytes(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "axis_tts_${UUID.randomUUID()}.mp3")
            file.writeBytes(bytes)
            lastCloudFile = file
            withContext(Dispatchers.Main) {
                player?.release()
                player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        _speaking.value = false
                        runCatching { it.release() }
                    }
                    setOnPreparedListener {
                        _speaking.value = true
                        it.start()
                    }
                    prepareAsync()
                }
            }
            true
        } catch (t: Throwable) {
            Timber.w(t, "cloud playback failed")
            false
        }
    }

    private suspend fun onDevice(text: String): String {
        warmUp()
        val tts = engine ?: return "no TTS engine available"
        if (!engineReady) return "on-device TTS not ready"
        return suspendCancellableCoroutine { cont ->
            val id = "axis_" + System.nanoTime()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { _speaking.value = true }
                override fun onDone(utteranceId: String?) {
                    _speaking.value = false
                    if (cont.isActive) cont.resume("spoken on-device")
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _speaking.value = false
                    if (cont.isActive) cont.resume("on-device TTS error")
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    _speaking.value = false
                    if (cont.isActive) cont.resume("on-device TTS error $errorCode")
                }
            })
            val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (queued != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resume("on-device TTS rejected the request")
            }
        }
    }

    private fun warnIfNoEngine() {
        if (engine == null) warmUp()
    }

    /** Cleans up the last cached cloud clip (called on stop). */
    fun clearCache() {
        lastCloudFile?.let { runCatching { it.delete() } }
        lastCloudFile = null
    }
}
