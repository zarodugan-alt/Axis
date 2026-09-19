package axis.app

import android.app.Application
import axis.act.routine.RoutineEngine
import axis.act.routine.RoutineRepository
import axis.agent.usage.UsageLedger
import axis.app.data.ProviderStore
import axis.app.data.SettingsStore
import axis.app.data.UsageRepository
import axis.app.drawer.AppRepository
import axis.app.system.AxisNotifications
import axis.kernel.di.ServiceLocator
import axis.kernel.events.EventBus
import axis.sense.notify.NotificationInbox
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class AxisApplication : Application() {

    @Inject lateinit var usageRepository: UsageRepository
    @Inject lateinit var ledger: UsageLedger
    @Inject lateinit var providerStore: ProviderStore
    @Inject lateinit var bus: EventBus
    @Inject lateinit var inbox: NotificationInbox
    @Inject lateinit var routineEngine: RoutineEngine
    @Inject lateinit var routineRepository: RoutineRepository
    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var notifications: AxisNotifications

    /** App-lifetime scope for warmups that must not block the first frame. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        notifications.ensureChannels()

        // Framework-instantiated components (listener + accessibility
        // services) resolve their collaborators through the locator, because
        // they cannot take constructor injection.
        ServiceLocator.register(EventBus::class.java, bus)
        ServiceLocator.register(NotificationInbox::class.java, inbox)
        ServiceLocator.register(RoutineEngine::class.java, routineEngine)

        appScope.launch {
            runCatching { ledger.seed(usageRepository.load()) }
                .onFailure { Timber.w(it, "usage seed failed") }
            runCatching { routineRepository.seedDefaultsOnce() }
                .onFailure { Timber.w(it, "routine seed failed") }
            // Touch the app index + provider store so flows are warm before
            // the first frame asks for them.
            runCatching { appRepository.refresh() }
                .onFailure { Timber.w(it, "app index warm failed") }
            runCatching {
                // Routines start automatically when any routine is enabled.
                val enabled = routineRepository.load().count { it.enabled }
                if (enabled > 0) routineEngine.start()
            }.onFailure { Timber.w(it, "routine engine warm failed") }
            runCatching { settings.killSwitchState.value }
        }
    }
}
