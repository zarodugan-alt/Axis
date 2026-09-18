package axis.app.drawer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import axis.kernel.events.AxisEvent
import axis.kernel.events.EventBus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manifest-declared receiver (see AndroidManifest): ACTION_PACKAGE_* is
 * exempt from background-delivery limits, so no polling service is needed
 * to keep the drawer index live.
 */
@AndroidEntryPoint
class PackageChangeReceiver : BroadcastReceiver() {

    @Inject lateinit var repo: AppRepository
    @Inject lateinit var bus: EventBus

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_PACKAGE_ADDED &&
            action != Intent.ACTION_PACKAGE_REMOVED &&
            action != Intent.ACTION_PACKAGE_REPLACED
        ) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                repo.refresh()
                bus.tryEmit(AxisEvent.AppInventoryChanged(action))
            } finally {
                pending.finish()
            }
        }
    }
}
