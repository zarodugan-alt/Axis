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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val TRANSITION = stringPreferencesKey("transition_style")
        val HAPTICS = booleanPreferencesKey("haptics")
        val HIDDEN = stringSetPreferencesKey("hidden_apps")
        val ICON_SIZE = intPreferencesKey("drawer_icon_size")
        val USER_NAME = stringPreferencesKey("user_name")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val SIDECAR_MODE = stringPreferencesKey("sidecar_mode")
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

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }
}
