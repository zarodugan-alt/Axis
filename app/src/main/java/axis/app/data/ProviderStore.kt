package axis.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import axis.agent.keys.KeyValidator
import axis.agent.provider.AiProvider
import axis.agent.provider.ApiStyle
import axis.agent.provider.ProviderCatalog
import axis.agent.provider.ProviderCatalog.RoutingMode
import axis.app.security.SecretVault
import axis.kernel.events.AxisEvent
import axis.kernel.events.EventBus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything the UI and the gateway need to know about one provider. */
data class ProviderConfig(
    val provider: AiProvider,
    val enabled: Boolean = false,
    val model: String = "",
    val hasKey: Boolean = false,
    /** Masked preview only — the real key never leaves [ProviderStore.key]. */
    val keyPreview: String? = null,
    /** null = never tested; true/false = last live test result. */
    val keyValid: Boolean? = null,
    val lastCheckedAt: Long = 0L,
    val lastError: String? = null
) {
    val ready: Boolean get() = (hasKey || provider.keyless) && keyValid != false
    val isSpeech: Boolean get() = provider.isSpeech
    val modelOrDefault: String get() = model.ifBlank { provider.defaultModel }
}

private val Context.providerDataStore by preferencesDataStore(name = "axis_providers")

/**
 * Provider configuration + API keys (spec §F5 / §S10 storage).
 *
 * Splitting responsibility: this class owns *configuration* (which providers
 * are on, which model, routing) and *custody* of keys; [SecretVault] does the
 * crypto; [axis.agent.LlmGateway] only ever receives a key for the duration
 * of one request.
 */
@Singleton
class ProviderStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: SecretVault,
    private val bus: EventBus
) {
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    private object K {
        val ROUTING = stringPreferencesKey("routing_mode")
        val ENABLED = stringPreferencesKey("enabled_ids")
        val PREFERRED = stringPreferencesKey("preferred_id")
        val SPEECH = stringPreferencesKey("speech_provider")
        val CUSTOM_LABEL = stringPreferencesKey("custom_label")
        val CUSTOM_BASE = stringPreferencesKey("custom_base")
        val CUSTOM_ENABLED = booleanPreferencesKey("custom_enabled")

        fun key(id: String) = stringPreferencesKey("key_$id")
        fun preview(id: String) = stringPreferencesKey("keyprev_$id")
        fun model(id: String) = stringPreferencesKey("model_$id")
        fun valid(id: String) = stringPreferencesKey("valid_$id")
        fun checked(id: String) = longPreferencesKey("checked_$id")
        fun error(id: String) = stringPreferencesKey("error_$id")
        fun enabled(id: String) = booleanPreferencesKey("on_$id")
    }

    /** Live provider instances (builtins + the user's custom endpoint). */
    val providers: StateFlow<List<AiProvider>> = context.providerDataStore.data
        .map { customProvider(it)?.let { c -> ProviderCatalog.builtins + c } ?: ProviderCatalog.builtins }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, ProviderCatalog.builtins)

    val routingMode: StateFlow<RoutingMode> = context.providerDataStore.data
        .map { prefs ->
            runCatching { RoutingMode.valueOf(prefs[K.ROUTING] ?: RoutingMode.AUTO.name) }
                .getOrDefault(RoutingMode.AUTO)
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, RoutingMode.AUTO)

    val preferredId: StateFlow<String?> = context.providerDataStore.data
        .map { it[K.PREFERRED]?.takeIf { id -> id.isNotBlank() } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    val speechProviderId: StateFlow<String?> = context.providerDataStore.data
        .map { it[K.SPEECH]?.takeIf { id -> id.isNotBlank() } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Full config list, ordered: chat providers in [ProviderCatalog] order. */
    val configs: StateFlow<List<ProviderConfig>> =
        combine(context.providerDataStore.data, providers) { prefs, list ->
            list.map { p ->
                ProviderConfig(
                    provider = p,
                    enabled = prefs[K.enabled(p.id)] ?: false,
                    model = prefs[K.model(p.id)].orEmpty(),
                    hasKey = !prefs[K.key(p.id)].isNullOrBlank(),
                    keyPreview = prefs[K.preview(p.id)],
                    keyValid = prefs[K.valid(p.id)]?.let { it == "ok" },
                    lastCheckedAt = prefs[K.checked(p.id)] ?: 0L,
                    lastError = prefs[K.error(p.id)]
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Ids of providers that are enabled *and* have usable credentials. */
    val connectedIds: StateFlow<Set<String>> = configs
        .map { list ->
            list.filter { it.enabled && (it.hasKey || it.provider.keyless) }
                .map { it.provider.id }
                .toSet()
        }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Providers the gateway may route to, in the user's preferred order. */
    val enabledProviders: StateFlow<List<AiProvider>> = combine(
        configs,
        routingMode,
        preferredId
    ) { list, mode, preferred ->
        val ready = list.filter { it.enabled && (it.hasKey || it.provider.keyless) }
            .map { it.provider }
        val ordered = if (preferred != null) {
            ready.sortedByDescending { it.id == preferred }
        } else ready
        ProviderCatalog.order(ordered, mode)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun config(id: String): Flow<ProviderConfig?> = configs.map { list ->
        list.firstOrNull { it.provider.id == id }
    }

    /** Plaintext key for one request. Never cache the result. */
    suspend fun key(id: String): String? {
        val blob = context.providerDataStore.data.first()[K.key(id)] ?: return null
        return vault.decrypt(blob)
    }

    suspend fun saveKey(id: String, rawKey: String) {
        val key = rawKey.trim()
        val preview = KeyValidator.preview(key)
        context.providerDataStore.edit {
            it[K.key(id)] = vault.encrypt(key)
            it[K.preview(id)] = preview
            it[K.valid(id)] = ""            // needs a fresh live test
            it[K.error(id)] = ""
        }
        bus.tryEmit(AxisEvent.ProviderChanged(id, "key_saved", preview))
    }

    suspend fun clearKey(id: String) {
        context.providerDataStore.edit {
            it.remove(K.key(id))
            it.remove(K.preview(id))
            it.remove(K.valid(id))
            it.remove(K.error(id))
            it[K.enabled(id)] = false
        }
        bus.tryEmit(AxisEvent.ProviderChanged(id, "key_cleared", ""))
    }

    suspend fun markChecked(id: String, ok: Boolean, error: String? = null) {
        context.providerDataStore.edit {
            it[K.valid(id)] = if (ok) "ok" else "bad"
            it[K.checked(id)] = System.currentTimeMillis()
            it[K.error(id)] = error.orEmpty()
            if (ok) it[K.enabled(id)] = true
        }
        bus.tryEmit(AxisEvent.ProviderChanged(id, if (ok) "verified" else "failed", error.orEmpty()))
    }

    suspend fun setModel(id: String, model: String) {
        context.providerDataStore.edit { it[K.model(id)] = model.trim() }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        context.providerDataStore.edit { it[K.enabled(id)] = enabled }
        bus.tryEmit(AxisEvent.ProviderChanged(id, if (enabled) "enabled" else "disabled", ""))
    }

    suspend fun setRoutingMode(mode: RoutingMode) {
        context.providerDataStore.edit { it[K.ROUTING] = mode.name }
    }

    suspend fun setPreferred(id: String?) {
        context.providerDataStore.edit {
            if (id == null) it.remove(K.PREFERRED) else it[K.PREFERRED] = id
        }
    }

    suspend fun setSpeechProvider(id: String?) {
        context.providerDataStore.edit {
            if (id == null) it.remove(K.SPEECH) else it[K.SPEECH] = id
        }
    }

    // ------------------------------------------------------- custom endpoint

    private fun customProvider(
        prefs: androidx.datastore.preferences.core.Preferences
    ): AiProvider? {
        val base = prefs[K.CUSTOM_BASE]?.takeIf { it.isNotBlank() } ?: return null
        return ProviderCatalog.custom.copy(
            label = prefs[K.CUSTOM_LABEL]?.takeIf { it.isNotBlank() } ?: "Custom endpoint",
            baseUrl = base
        )
    }

    suspend fun hasCustom(): Boolean =
        !context.providerDataStore.data.first()[K.CUSTOM_BASE].isNullOrBlank()

    suspend fun saveCustom(label: String, baseUrl: String, model: String, apiKey: String?) {
        context.providerDataStore.edit {
            it[K.CUSTOM_LABEL] = label.trim().ifBlank { "Custom endpoint" }
            it[K.CUSTOM_BASE] = baseUrl.trim()
            it[K.model(ProviderCatalog.custom.id)] = model.trim()
            it[K.CUSTOM_ENABLED] = true
        }
        if (!apiKey.isNullOrBlank()) saveKey(ProviderCatalog.custom.id, apiKey)
        bus.tryEmit(AxisEvent.ProviderChanged(ProviderCatalog.custom.id, "custom_saved", baseUrl))
    }

    suspend fun clearCustom() {
        context.providerDataStore.edit {
            it.remove(K.CUSTOM_BASE)
            it.remove(K.CUSTOM_LABEL)
            it.remove(K.key(ProviderCatalog.custom.id))
            it.remove(K.preview(ProviderCatalog.custom.id))
            it[K.enabled(ProviderCatalog.custom.id)] = false
        }
        bus.tryEmit(AxisEvent.ProviderChanged(ProviderCatalog.custom.id, "custom_removed", ""))
    }

    /** Provider ids currently usable — feeds [axis.kernel.caps.CapabilityManifest]. */
    suspend fun connectedProviderIds(): Set<String> = connectedIds.value

    /** Custom OpenAI-compatible provider skeleton for the "add" form. */
    fun customTemplate(): AiProvider = ProviderCatalog.custom

    fun styles(): List<ApiStyle> = listOf(ApiStyle.OPENAI)
}
