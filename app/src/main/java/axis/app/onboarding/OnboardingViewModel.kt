package axis.app.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import axis.app.data.ProviderStore
import axis.app.data.SettingsStore
import axis.sense.notify.NotificationAccess
import axis.sense.screen.AxisAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingState(
    val isDefaultLauncher: Boolean = false,
    val notificationAccess: Boolean = false,
    val accessibility: Boolean = false,
    val micGranted: Boolean = false,
    val providersConnected: Int = 0
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    store: ProviderStore
) : ViewModel() {

    private val refresh = MutableStateFlow(0)

    val state: StateFlow<OnboardingState> =
        kotlinx.coroutines.flow.combine(store.connectedIds, refresh) { ids, _ ->
            OnboardingState(
                isDefaultLauncher = isDefaultLauncher(),
                notificationAccess = NotificationAccess.isGranted(context),
                accessibility = AxisAccessibilityService.isEnabled(context),
                micGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED,
                providersConnected = ids.size
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OnboardingState())

    fun refresh() {
        refresh.value += 1
    }

    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun openHomeSettings() {
        // Requires a user pick — the launcher role cannot be self-granted.
        refresh()
        val intents = listOf(
            Intent("android.settings.HOME_SETTINGS"),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        launchFirst(intents)
    }

    fun openNotificationAccess() {
        refresh()
        launchFirst(
            listOf(
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
                Intent(Settings.ACTION_SETTINGS)
            )
        )
    }

    fun openAccessibilitySettings() {
        refresh()
        launchFirst(
            listOf(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
        )
    }

    private fun launchFirst(intents: List<Intent>) {
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                runCatching { context.startActivity(intent) }
                return
            }
        }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            onDone()
        }
    }
}
