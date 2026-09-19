package axis.app.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import axis.app.chat.ChatHandoff
import axis.app.data.ProviderStore
import axis.app.data.SettingsStore
import axis.app.speech.TtsSpeaker
import axis.ui.components.AxisButton
import axis.ui.components.AxisButtonStyle
import axis.ui.components.AxisOrb
import axis.ui.components.GlassChip
import axis.ui.components.GlassCard
import axis.ui.components.OrbState
import axis.ui.components.SettingsRadioRow
import axis.ui.components.SettingsSwitchRow
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.TextSecondary
import axis.ui.theme.Warning
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val speaker: TtsSpeaker,
    private val settings: SettingsStore,
    store: ProviderStore
) : ViewModel() {

    val speaking: StateFlow<Boolean> = speaker.speaking
    val voiceReplies: StateFlow<Boolean> = settings.voiceReplies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val asrMode: StateFlow<String> = settings.asrMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")
    val speechProvider: StateFlow<String?> = store.speechProviderId

    fun setVoiceReplies(on: Boolean) {
        viewModelScope.launch { settings.setVoiceReplies(on) }
    }

    fun setAsrMode(mode: String) {
        viewModelScope.launch { settings.setAsrMode(mode) }
    }

    fun speak(text: String) {
        viewModelScope.launch { speaker.speak(text) }
    }

    fun stop() = speaker.stop()

    /** On-device voices available from the platform engine. */
    fun voices(): List<String> = speaker.onDeviceVoices
}

/**
 * Voice console (spec §S7 · §F16). Uses the platform SpeechRecognizer (no
 * extra dependency, works offline when the device has an offline model) and
 * the same speaker the agent uses, so "does voice work?" is answerable here
 * in one tap.
 */
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    vm: VoiceViewModel = hiltViewModel()
) {
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val voiceReplies by vm.voiceReplies.collectAsStateWithLifecycle()
    val asrMode by vm.asrMode.collectAsStateWithLifecycle()
    val speechProvider by vm.speechProvider.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var listening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context)
        else null
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { recognizer?.destroy() } }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) error = "Microphone permission denied"
        else listening = true
    }

    fun startListening() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val rec = recognizer ?: run {
            error = "No speech recognizer on this device"
            return
        }
        error = null
        transcript = ""
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                transcript = text
                if (text.isNotBlank()) ChatHandoff.prompt.value = text
            }

            override fun onError(errorCode: Int) {
                listening = false
                error = when (errorCode) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
                    SpeechRecognizer.ERROR_NETWORK -> "Recognizer needs a network"
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone busy"
                    else -> "Recognizer error $errorCode"
                }
            }

            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        rec.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        )
    }

    AxisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AxisSpacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Text("Voice", style = AxisType.Title)
            }

            Spacer(Modifier.height(AxisSpacing.section))
            AxisOrb(
                state = when {
                    listening -> OrbState.LISTENING
                    speaking -> OrbState.ACTING
                    else -> OrbState.IDLE
                },
                orbSize = 132.dp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    listening -> "listening…"
                    speaking -> "speaking…"
                    else -> "tap to speak"
                },
                style = AxisType.BodyStrong
            )
            Spacer(Modifier.height(12.dp))
            GlassChip(
                label = if (listening) "STOP" else "TALK",
                active = listening,
                onClick = {
                    if (listening) {
                        runCatching { recognizer?.stopListening() }
                        listening = false
                    } else {
                        startListening()
                    }
                }
            )

            if (transcript.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("HEARD", style = AxisType.Caption)
                    Text(transcript, style = AxisType.Body)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Open chat to answer it (the prompt is queued).",
                        style = AxisType.Caption,
                        color = TextSecondary
                    )
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AxisType.Caption, color = Warning)
            }

            Spacer(Modifier.height(AxisSpacing.section))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SettingsSwitchRow(
                    title = "Speak replies",
                    subtitle = "Voice answers are read aloud automatically",
                    checked = voiceReplies,
                    onCheckedChange = vm::setVoiceReplies
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speech engine", style = AxisType.BodyStrong, modifier = Modifier.weight(1f))
                    Text(
                        speechProvider ?: "on-device",
                        style = AxisType.Telemetry.copy(color = AccentCyan)
                    )
                }
                Spacer(Modifier.height(6.dp))
                AxisButton(
                    text = speechProvider?.let { "Change voice provider" } ?: "Add a cloud voice",
                    style = AxisButtonStyle.SECONDARY,
                    onClick = onOpenProviders
                )
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("RECOGNITION", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SettingsRadioRow(
                    title = "Device recognizer",
                    subtitle = "Offline-capable, no data leaves the phone",
                    selected = asrMode == "auto",
                    onClick = { vm.setAsrMode("auto") }
                )
                SettingsRadioRow(
                    title = "Prefer offline",
                    subtitle = "Only on-device models (may be less accurate)",
                    selected = asrMode == "offline",
                    onClick = { vm.setAsrMode("offline") }
                )
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("TEST THE SPEAKER", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AxisButton(
                    text = "Say hello",
                    style = AxisButtonStyle.SECONDARY,
                    onClick = { vm.speak("Hello. AXIS is online and listening.") }
                )
                AxisButton(
                    text = "Stop",
                    style = AxisButtonStyle.DANGER,
                    onClick = vm::stop
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
