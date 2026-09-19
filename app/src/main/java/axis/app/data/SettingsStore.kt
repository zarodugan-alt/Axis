package axis.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

enum class TransitionStyle { CUBE, DEPTH }

private val Context.settingsDataStore by preferencesDataStore(name = "axis_settings")

/**
 * Typed DataStore façade (spec §8.2 subset for P1). P2+ keys (asr_mode,
 * tts_voice, routing_mode, protected_apps, gates, retention_days, …) land
 * here as their phases do. All flows are distinctUntilChanged.
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bus: axis.kernel.events.EventBus
) {
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    private object Keys {
        val TRANSITION = stringPreferencesKey("transition_style")
        val HAPTICS = booleanPreferencesKey("haptics")
        val HIDDEN = stringSetPreferencesKey("hidden_apps")
        val ICON_SIZE = intPreferencesKey("drawer_icon_size")
        val USER_NAME = stringPreferencesKey("user_name")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val SIDECAR_MODE = stringPreferencesKey("sidecar_mode")

        // --- P2/P3/P4 keys -------------------------------------------------
        val PROTECTED = stringSetPreferencesKey("protected_apps")
        val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        val CONFIRM_FROM = stringPreferencesKey("safety_confirm_from")
        val RATE_LIMIT = intPreferencesKey("safety_rate_limit")
        val PRIORITY_APPS = stringSetPreferencesKey("triage_priority_apps")
        val TRIAGE_KEYWORDS = stringSetPreferencesKey("triage_keywords")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val QUIET_ON = booleanPreferencesKey("quiet_enabled")
        val WEBHOOK_PORT = intPreferencesKey("webhook_port")
        val VOICE_REPLIES = booleanPreferencesKey("voice_replies")
        val ASR_MODE = stringPreferencesKey("asr_mode")
    }

    val transitionStyle: Flow<TransitionStyle> =
        context.settingsDataStore.data
            .map { p -> runCatching { TransitionStyle.valueOf(p[Keys.TRANSITION] ?: "CUBE") }
                .getOrDefault(TransitionStyle.CUBE) }
            .distinctUntilChanged()

    val hapticsEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.HAPTICS] ?: true }
            .distinctUntilChanged()

    val hiddenApps: Flow<Set<String>> =
        context.settingsDataStore.data.map { it[Keys.HIDDEN].orEmpty() }
            .distinctUntilChanged()

    /** Drawer icon size in dp (Appearance slider, 40–64). */
    val drawerIconSize: Flow<Int> =
        context.settingsDataStore.data.map { (it[Keys.ICON_SIZE] ?: 48).coerceIn(40, 64) }
            .distinctUntilChanged()

    val userName: Flow<String?> =
        context.settingsDataStore.data.map { it[Keys.USER_NAME] }
            .distinctUntilChanged()

    val onboardingDone: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.ONBOARDING] ?: false }
            .distinctUntilChanged()

    /** Quick-mode chip state: "off" | "focus" | "sleep" | "drive" (P4 wires behavior). */
    val sideCarMode: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.SIDECAR_MODE] ?: "off" }
            .distinctUntilChanged()

    suspend fun setTransitionStyle(value: TransitionStyle) =
        edit { it[Keys.TRANSITION] = value.name }

    suspend fun setHaptics(enabled: Boolean) =
        edit { it[Keys.HAPTICS] = enabled }

    suspend fun setHidden(packageName: String, hidden: Boolean) =
        edit {
            val cur = it[Keys.HIDDEN].orEmpty().toMutableSet()
            if (hidden) cur.add(packageName) else cur.remove(packageName)
            it[Keys.HIDDEN] = cur
        }

    suspend fun setDrawerIconSize(dp: Int) =
        edit { it[Keys.ICON_SIZE] = dp.coerceIn(40, 64) }

    suspend fun setUserName(name: String?) =
        edit { if (name == null) it.remove(Keys.USER_NAME) else it[Keys.USER_NAME] = name }

    suspend fun setOnboardingDone(done: Boolean) =
        edit { it[Keys.ONBOARDING] = done }

    suspend fun setSideCarMode(mode: String) =
        edit { it[Keys.SIDECAR_MODE] = mode }

    // ------------------------------------------------- P2/P3/P4 settings

    val protectedApps: Flow<Set<String>> =
        context.settingsDataStore.data.map { it[Keys.PROTECTED].orEmpty() }
            .distinctUntilChanged()

    val killSwitch: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.KILL_SWITCH] ?: false }
            .distinctUntilChanged()

    val confirmFrom: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.CONFIRM_FROM] ?: "MEDIUM" }
            .distinctUntilChanged()

    val rateLimit: Flow<Int> =
        context.settingsDataStore.data.map { (it[Keys.RATE_LIMIT] ?: 12).coerceIn(1, 60) }
            .distinctUntilChanged()

    val priorityApps: Flow<Set<String>> =
        context.settingsDataStore.data.map { it[Keys.PRIORITY_APPS].orEmpty() }
            .distinctUntilChanged()

    val triageKeywords: Flow<Set<String>> =
        context.settingsDataStore.data.map { it[Keys.TRIAGE_KEYWORDS].orEmpty() }
            .distinctUntilChanged()

    val quietHoursEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.QUIET_ON] ?: false }
            .distinctUntilChanged()

    /** Quiet window as (startMinute, endMinute). */
    val quietWindow: Flow<Pair<Int, Int>> =
        context.settingsDataStore.data
            .map { (it[Keys.QUIET_START] ?: (22 * 60)) to (it[Keys.QUIET_END] ?: (7 * 60)) }
            .distinctUntilChanged()

    /** "auto" (device mic) or "cloud" — consumed by the voice screen. */
    val asrMode: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.ASR_MODE] ?: "auto" }
            .distinctUntilChanged()

    val voiceReplies: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.VOICE_REPLIES] ?: false }
            .distinctUntilChanged()

    val webhookPort: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.WEBHOOK_PORT] ?: 0 }
            .distinctUntilChanged()

    suspend fun setProtected(packageName: String, protected: Boolean) =
        edit {
            val cur = it[Keys.PROTECTED].orEmpty().toMutableSet()
            if (protected) cur.add(packageName) else cur.remove(packageName)
            it[Keys.PROTECTED] = cur
        }

    suspend fun setKillSwitch(engaged: Boolean) {
        edit { it[Keys.KILL_SWITCH] = engaged }
        bus.tryEmit(axis.kernel.events.AxisEvent.KillSwitchToggled(engaged))
    }

    suspend fun setConfirmFrom(level: String) =
        edit { it[Keys.CONFIRM_FROM] = level }

    suspend fun setRateLimit(perMinute: Int) =
        edit { it[Keys.RATE_LIMIT] = perMinute.coerceIn(1, 60) }

    suspend fun setPriorityApp(packageName: String, priority: Boolean) =
        edit {
            val cur = it[Keys.PRIORITY_APPS].orEmpty().toMutableSet()
            if (priority) cur.add(packageName) else cur.remove(packageName)
            it[Keys.PRIORITY_APPS] = cur
        }

    suspend fun setTriageKeyword(keyword: String, on: Boolean) =
        edit {
            val cur = it[Keys.TRIAGE_KEYWORDS].orEmpty().toMutableSet()
            val k = keyword.trim().lowercase()
            if (k.isEmpty()) return@edit
            if (on) cur.add(k) else cur.remove(k)
            it[Keys.TRIAGE_KEYWORDS] = cur
        }

    suspend fun setQuietHours(enabled: Boolean, startMinute: Int, endMinute: Int) =
        edit {
            it[Keys.QUIET_ON] = enabled
            it[Keys.QUIET_START] = startMinute.coerceIn(0, 1439)
            it[Keys.QUIET_END] = endMinute.coerceIn(0, 1439)
        }

    suspend fun setAsrMode(mode: String) = edit { it[Keys.ASR_MODE] = mode }

    suspend fun setVoiceReplies(on: Boolean) = edit { it[Keys.VOICE_REPLIES] = on }

    suspend fun setWebhookPort(port: Int) = edit { it[Keys.WEBHOOK_PORT] = port.coerceIn(0, 65535) }

    /** Cached kill-switch value for synchronous readers (engine, gate). */
    val killSwitchState: kotlinx.coroutines.flow.StateFlow<Boolean> = killSwitch
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    /** Live safety policy, rebuilt whenever any component key changes. */
    val policyState: kotlinx.coroutines.flow.StateFlow<axis.safety.SafetyPolicy> =
        combine(protectedApps, killSwitch, confirmFrom, rateLimit) { protected, kill, level, limit ->
            axis.safety.SafetyPolicy(
                protectedPackages = protected,
                killSwitch = kill,
                confirmFrom = runCatching {
                    axis.kernel.agent.RiskLevel.valueOf(level)
                }.getOrDefault(axis.kernel.agent.RiskLevel.MEDIUM),
                rateLimitPerMinute = limit
            )
        }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, axis.safety.SafetyPolicy())

    /** Snapshot for the safety layer (reads current values once). */
    suspend fun safetySnapshot(): axis.safety.SafetyPolicy = axis.safety.SafetyPolicy(
        protectedPackages = protectedApps.first(),
        killSwitch = killSwitch.first(),
        confirmFrom = runCatching {
            axis.kernel.agent.RiskLevel.valueOf(confirmFrom.first())
        }.getOrDefault(axis.kernel.agent.RiskLevel.MEDIUM),
        rateLimitPerMinute = rateLimit.first()
    )

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }
}
