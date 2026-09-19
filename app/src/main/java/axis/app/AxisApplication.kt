package axis.app

import android.app.Application
import axis.agent.usage.UsageLedger
import axis.app.data.ProviderStore
import axis.app.data.UsageRepository
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

    /** App-lifetime scope for warmups that must not block the first frame. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Warm the usage ring from disk so the dashboard is populated on open,
        // and touch the provider store so its flows are live before the first
        // capability manifest is compiled.
        appScope.launch {
            runCatching { ledger.seed(usageRepository.load()) }
                .onFailure { Timber.w(it, "usage seed failed") }
            runCatching { providerStore.connectedProviderIds() }
                .onFailure { Timber.w(it, "provider warmup failed") }
        }
    }
}
