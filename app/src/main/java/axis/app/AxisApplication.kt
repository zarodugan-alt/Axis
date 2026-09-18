package axis.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class AxisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // P2: init :kernel (event bus warmup), CoreService start, watchdog.
        // P3: Room warmup, audit retention purge.
    }
}
