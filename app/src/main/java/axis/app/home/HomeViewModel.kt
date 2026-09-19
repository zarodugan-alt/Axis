package axis.app.home

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import axis.act.action.SystemActions
import axis.act.routine.RoutineEngine
import axis.app.data.ProviderStore
import axis.app.data.SettingsStore
import axis.app.data.UsageRepository
import axis.app.drawer.AppRepository
import axis.kernel.model.AppEntry
import axis.kernel.search.FuzzySearch
import axis.sense.DeviceStateRepository
import axis.sense.DeviceSnapshot
import axis.sense.notify.NotificationAccess
import axis.sense.notify.NotificationInbox
import axis.sense.screen.AxisAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the circuit board renders. One 2-second ticker drives the
 * telemetry; everything else is a real flow (notifications, providers,
 * routines, kill switch) so the board never shows stale state.
 */
data class HomeState(
    val snapshot: DeviceSnapshot = DeviceSnapshot(),
    val loadHistory: List<Float> = emptyList(),
    val providersConnected: Int = 0,
    val speechReady: Boolean = false,
    val unread: Int = 0,
    val routinesEnabled: Int = 0,
    val lastRoutine: String? = null,
    val killSwitch: Boolean = false,
    val notificationAccess: Boolean = false,
    val screenAccess: Boolean = false,
    val storageWrites: Boolean = false,
    val dndAccess: Boolean = false,
    val flashAvailable: Boolean = false,
    val ready: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: AppRepository,
    private val device: DeviceStateRepository,
    private val inbox: NotificationInbox,
    private val routines: RoutineEngine,
    private val usage: UsageRepository,
    private val providerStore: ProviderStore,
    private val settings: SettingsStore,
    private val systemActions: SystemActions,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val query = MutableStateFlow("")

    /** Live app results while typing (top 6); empty when idle. */
    val searchResults: StateFlow<List<AppEntry>> =
        combine(repo.visibleApps, query) { list, q ->
            if (q.isBlank()) emptyList() else FuzzySearch.filter(q, list) { it.label }.take(6)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val greeting: StateFlow<String> = combine(settings.userName, minuteTicker) { name, _ ->
        greetingFor(currentHour(), name)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        greetingFor(currentHour(), null)
    )

    val userName: StateFlow<String?> = settings.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Requests in the last 24h — shown on the VAULT/USAGE module. */
    val requestsToday: StateFlow<Int> = usage.records
        .map { list -> list.count { it.ts > System.currentTimeMillis() - 86_400_000 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val loadSamples = mutableListOf<Float>()

    val state: StateFlow<HomeState> = combine(
        ticker,
        inbox.unreadCount,
        providerStore.connectedIds,
        routines.routines,
        settings.killSwitchState
    ) { _, unread, connected, routineList, kill ->
        val snapshot = device.sample()
        synchronized(loadSamples) {
            loadSamples.add(snapshot.cpuLoad)
            while (loadSamples.size > 40) loadSamples.removeAt(0)
        }
        HomeState(
            snapshot = snapshot,
            loadHistory = synchronized(loadSamples) { loadSamples.toList() },
            providersConnected = connected.count { id -> providerStore.providers.value.any { it.id == id && it.isChat } },
            speechReady = connected.any { id -> providerStore.providers.value.any { it.id == id && it.isSpeech } },
            unread = unread,
            routinesEnabled = routineList.count { it.enabled },
            lastRoutine = routineList.filter { it.lastRunAt > 0 }
                .maxByOrNull { it.lastRunAt }?.name,
            killSwitch = kill,
            notificationAccess = NotificationAccess.isGranted(context),
            screenAccess = AxisAccessibilityService.isEnabled(context),
            storageWrites = device.canWriteSettings(),
            dndAccess = device.hasNotificationPolicyAccess(),
            flashAvailable = device.hasFlash(),
            ready = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    // ------------------------------------------------------------- actions

    fun setQuery(q: String) {
        query.value = q
    }

    fun iconFor(packageName: String): Drawable? = repo.iconFor(packageName)

    fun launch(packageName: String) {
        viewModelScope.launch { repo.launch(packageName) }
    }

    fun webSearch(q: String) = repo.webSearch(q)

    fun openWallpaperPicker() = repo.openWallpaperPicker()

    fun openAppInfo(packageName: String) = repo.openAppInfo(packageName)

    fun toggleTorch(on: Boolean) {
        viewModelScope.launch {
            val result = systemActions.setTorch(on)
            lastAction.value = result.detail
        }
    }

    fun toggleDnd(on: Boolean) {
        viewModelScope.launch { lastAction.value = systemActions.setDnd(on).detail }
    }

    fun toggleRotation(locked: Boolean) {
        viewModelScope.launch { lastAction.value = systemActions.setRotationLocked(locked).detail }
    }

    fun nudgeBrightness(delta: Int) {
        viewModelScope.launch {
            val current = device.sample().brightness * 100 / 255
            lastAction.value = systemActions.setBrightness(current + delta).detail
        }
    }

    fun openSettingsPage(page: String) {
        viewModelScope.launch { lastAction.value = systemActions.openSettingsPage(page).detail }
    }

    fun clearNotifications() = inbox.clear()

    fun setKillSwitch(engaged: Boolean) {
        viewModelScope.launch { settings.setKillSwitch(engaged) }
    }

    fun runRoutine(id: String) = routines.runNow(id)

    fun setUserName(name: String) {
        viewModelScope.launch { settings.setUserName(name.take(24)) }
    }

    /** Last action feedback shown as a HUD toast line. */
    val lastAction = MutableStateFlow<String?>(null)

    fun clearActionMessage() {
        lastAction.value = null
    }

    companion object {
        /** Telemetry tick — fast enough to feel live, slow enough to be free. */
        private val ticker: Flow<Unit> = flow {
            while (true) {
                emit(Unit)
                delay(2_000)
            }
        }

        /** Re-emits once a minute so the greeting tracks the clock. */
        private val minuteTicker: Flow<Unit> = flow {
            while (true) {
                emit(Unit)
                delay(60_000)
            }
        }

        fun currentHour(nowMillis: Long = System.currentTimeMillis()): Int =
            Calendar.getInstance().apply { timeInMillis = nowMillis }
                .get(Calendar.HOUR_OF_DAY)

        fun greetingFor(hour: Int, name: String?): String {
            val part = when (hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Still up"
            }
            val who = name?.takeIf { it.isNotBlank() } ?: return part
            return "$part, $who"
        }
    }
}
