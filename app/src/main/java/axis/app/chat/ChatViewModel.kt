package axis.app.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import axis.agent.AgentEvent
import axis.agent.AgentLoop
import axis.agent.LlmGateway
import axis.agent.prompt.AgentPrompt
import axis.agent.protocol.ChatMessage
import axis.agent.protocol.Role
import axis.agent.tool.ToolRegistry
import axis.app.agent.PendingConfirm
import axis.app.agent.SafetyGate
import axis.app.data.ProviderStore
import axis.app.data.SettingsStore
import axis.app.speech.TtsSpeaker
import axis.kernel.caps.CapabilityManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One rendered line of the conversation. */
sealed interface ChatItem {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatItem
    data class Assistant(override val id: Long, val text: String, val streaming: Boolean) : ChatItem
    data class Step(
        override val id: Long,
        val label: String,
        val ok: Boolean,
        val detail: String
    ) : ChatItem
    data class Notice(override val id: Long, val text: String, val error: Boolean) : ChatItem
}

data class ChatUiState(
    val items: List<ChatItem> = emptyList(),
    val busy: Boolean = false,
    val providerLabel: String = "no provider",
    val basicMode: Boolean = true,
    val speakReplies: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val loop: AgentLoop,
    private val gateway: LlmGateway,
    private val registry: ToolRegistry,
    private val store: ProviderStore,
    private val settings: SettingsStore,
    private val gate: SafetyGate,
    private val speaker: TtsSpeaker,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val items = MutableStateFlow<List<ChatItem>>(emptyList())
    private var nextId = 1L
    private var turn: Job? = null
    private val history = mutableListOf<ChatMessage>()

    val confirmation: StateFlow<PendingConfirm?> = gate.confirmation

    val uiState: StateFlow<ChatUiState> = items
        .let { flow ->
            kotlinx.coroutines.flow.combine(
                flow,
                store.enabledProviders,
                settings.voiceReplies
            ) { list, providers, voice ->
                ChatUiState(
                    items = list,
                    busy = turn?.isActive == true,
                    providerLabel = providers.firstOrNull()?.label ?: "no provider",
                    basicMode = providers.isEmpty(),
                    speakReplies = voice
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || turn?.isActive == true) return
        add(ChatItem.User(nextId++, prompt))
        startTurn(prompt)
    }

    fun stop() {
        turn?.cancel()
        speaker.stop()
        add(ChatItem.Notice(nextId++, "Stopped.", error = false))
    }

    fun clear() {
        history.clear()
        items.value = emptyList()
    }

    fun reRunLast() {
        val lastUser = items.value.filterIsInstance<ChatItem.User>().lastOrNull() ?: return
        startTurn(lastUser.text)
    }

    private fun startTurn(prompt: String) {
        turn = viewModelScope.launch {
            val routes = buildRoutes()
            if (routes.isEmpty()) {
                add(
                    ChatItem.Notice(
                        nextId++,
                        "No AI provider connected. Add an API key in Settings → AI Providers.",
                        error = true
                    )
                )
                return@launch
            }

            val system = AgentPrompt.system(
                manifest = manifest(),
                registry = registry
            )
            history.add(ChatMessage(Role.USER, prompt))

            val assistantId = nextId++
            val stepIds = mutableListOf<Long>()
            val buffer = StringBuilder()
            var providerId = ""

            loop.run(
                system = system,
                history = history.toList(),
                routes = routes
            ).collect { event ->
                when (event) {
                    is AgentEvent.Delta -> {
                        buffer.append(event.text)
                        upsertStreaming(assistantId, buffer.toString())
                    }
                    is AgentEvent.ToolStarted -> {
                        val id = nextId++
                        stepIds += id
                        add(ChatItem.Step(id, "→ ${event.label}", ok = true, detail = "step ${event.step}"))
                    }
                    is AgentEvent.ToolFinished -> {
                        stepIds.lastOrNull()?.let { id ->
                            replace(
                                id,
                                ChatItem.Step(
                                    id,
                                    "✓ ${event.result.name.replace('_', ' ')}",
                                    ok = event.result.ok,
                                    detail = event.result.output.take(180) +
                                        " · ${event.result.durationMs}ms"
                                )
                            )
                        }
                        gate.noteExecuted()
                    }
                    is AgentEvent.ToolDenied -> {
                        add(ChatItem.Notice(nextId++, "Blocked: ${event.reason}", error = true))
                    }
                    is AgentEvent.Failed -> {
                        add(ChatItem.Notice(nextId++, event.message, error = true))
                    }
                    is AgentEvent.Done -> {
                        providerId = event.providerId
                        val finalText = buffer.toString().ifBlank { "…" }
                        finishStreaming(assistantId, finalText)
                        history.add(ChatMessage(Role.ASSISTANT, finalText))
                        if (uiState.value.speakReplies) speaker.speak(finalText)
                    }
                }
            }
            if (uiState.value.items.none { it.id == assistantId }) {
                // Nothing streamed at all (hard failure already reported).
                add(ChatItem.Notice(nextId++, "No reply from $providerId.", error = true))
            }
        }
    }

    /** Enables or disables spoken replies. */
    fun setSpeakReplies(on: Boolean) {
        viewModelScope.launch { settings.setVoiceReplies(on) }
    }

    fun respondToGate(callId: String, approved: Boolean) = gate.respond(callId, approved)

    private fun buildRoutes(): List<axis.agent.Route> = gateway.routes(
        enabled = store.enabledProviders.value,
        mode = store.routingMode.value,
        preferredId = store.preferredId.value
    )

    /**
     * The live capability manifest handed to the model. Every flag here is a
     * real check against the platform, so the prompt can never promise an
     * action the device will refuse.
     */
    private fun manifest(): CapabilityManifest = CapabilityManifest(
        accessibility = axis.sense.screen.AxisAccessibilityService.isEnabled(appContext),
        notifications = axis.sense.notify.NotificationAccess.isGranted(appContext),
        overlay = android.provider.Settings.canDrawOverlays(appContext),
        usageStats = hasUsageAccess(),
        writeSettings = android.provider.Settings.System.canWrite(appContext),
        dnd = hasDndAccess(),
        connectedProviders = store.connectedIds.value
    )

    private fun hasDndAccess(): Boolean = runCatching {
        appContext.getSystemService(android.app.NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true
    }.getOrDefault(false)

    private fun hasUsageAccess(): Boolean = runCatching {
        val ops = appContext.getSystemService(android.app.AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            appContext.packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    // ------------------------------------------------------------- helpers

    private fun add(item: ChatItem) {
        items.value = items.value + item
    }

    private fun replace(id: Long, item: ChatItem) {
        items.value = items.value.map { if (it.id == id) item else it }
    }

    private fun upsertStreaming(id: Long, text: String) {
        val existing = items.value.firstOrNull { it.id == id }
        if (existing == null) add(ChatItem.Assistant(id, text, streaming = true))
        else replace(id, ChatItem.Assistant(id, text, streaming = true))
    }

    private fun finishStreaming(id: Long, text: String) {
        val existing = items.value.firstOrNull { it.id == id }
        if (existing == null) add(ChatItem.Assistant(id, text, streaming = false))
        else replace(id, ChatItem.Assistant(id, text, streaming = false))
    }

    override fun onCleared() {
        speaker.stop()
        super.onCleared()
    }
}
