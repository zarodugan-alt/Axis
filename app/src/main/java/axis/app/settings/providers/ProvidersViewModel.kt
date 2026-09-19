package axis.app.settings.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import axis.agent.LlmGateway
import axis.agent.keys.KeyValidator
import axis.agent.provider.AiProvider
import axis.agent.provider.ProviderCatalog
import axis.agent.provider.ProviderCatalog.RoutingMode
import axis.app.data.ProviderConfig
import axis.app.data.ProviderStore
import axis.app.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of the most recent "Test key" action, per provider. */
data class TestState(
    val providerId: String,
    val running: Boolean = false,
    val ok: Boolean? = null,
    val message: String = ""
)

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val store: ProviderStore,
    private val gateway: LlmGateway,
    private val settings: SettingsStore
) : ViewModel() {

    val chatProviders: StateFlow<List<ProviderConfig>> = store.configs
        .map { list -> list.filter { it.provider.isChat } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val speechProviders: StateFlow<List<ProviderConfig>> = store.configs
        .map { list -> list.filter { it.isSpeech } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routingMode: StateFlow<RoutingMode> = store.routingMode
    val preferredId: StateFlow<String?> = store.preferredId
    val speechProviderId: StateFlow<String?> = store.speechProviderId
    val haptics: StateFlow<Boolean> = settings.hapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val testState = MutableStateFlow<TestState?>(null)

    fun config(id: String): StateFlow<ProviderConfig?> = store.configs
        .map { list -> list.firstOrNull { it.provider.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveKey(id: String, key: String) {
        viewModelScope.launch {
            store.saveKey(id, key)
            val check = provider(id)?.let { KeyValidator.validate(it, key) }
            testState.value = TestState(
                providerId = id,
                ok = null,
                message = check?.message ?: "Saved"
            )
        }
    }

    fun clearKey(id: String) {
        viewModelScope.launch {
            store.clearKey(id)
            testState.value = TestState(id, message = "Key removed")
        }
    }

    fun setModel(id: String, model: String) {
        viewModelScope.launch { store.setModel(id, model) }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { store.setEnabled(id, enabled) }
    }

    fun setRoutingMode(mode: RoutingMode) {
        viewModelScope.launch { store.setRoutingMode(mode) }
    }

    fun setPreferred(id: String?) {
        viewModelScope.launch { store.setPreferred(id) }
    }

    fun setSpeechProvider(id: String?) {
        viewModelScope.launch { store.setSpeechProvider(id) }
    }

    /** Live check: sends a real 8-token request with the stored key. */
    fun testKey(id: String) {
        val provider = provider(id) ?: return
        val model = configValue(id)?.modelOrDefault ?: provider.defaultModel
        viewModelScope.launch {
            testState.value = TestState(id, running = true, message = "Contacting ${provider.label}…")
            val key = store.key(id)
            if (key.isNullOrBlank() && !provider.keyless) {
                testState.value = TestState(id, ok = false, message = "No key stored")
                return@launch
            }
            val result = gateway.verifyKey(provider, model, key.orEmpty())
            result.fold(
                onSuccess = { message ->
                    store.markChecked(id, ok = true)
                    testState.value = TestState(id, ok = true, message = message)
                },
                onFailure = { error ->
                    val msg = error.message ?: "Failed"
                    store.markChecked(id, ok = false, error = msg)
                    testState.value = TestState(id, ok = false, message = msg)
                }
            )
        }
    }

    fun saveCustom(label: String, baseUrl: String, model: String, key: String) {
        viewModelScope.launch {
            store.saveCustom(label, baseUrl, model, key.ifBlank { null })
            testState.value = TestState(ProviderCatalog.custom.id, message = "Custom endpoint saved")
        }
    }

    fun clearCustom() {
        viewModelScope.launch {
            store.clearCustom()
            testState.value = TestState(ProviderCatalog.custom.id, message = "Custom endpoint removed")
        }
    }

    fun customProvider(): AiProvider = store.customTemplate()

    private suspend fun provider(id: String): AiProvider? =
        store.providers.value.firstOrNull { it.id == id }

    private fun configValue(id: String): ProviderConfig? =
        store.configs.value.firstOrNull { it.provider.id == id }

    /** Count of chat providers enabled with usable credentials. */
    val connectedCount: StateFlow<Int> = store.connectedIds
        .map { ids -> ids.count { id -> ProviderCatalog.byId(id)?.isChat != false } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
