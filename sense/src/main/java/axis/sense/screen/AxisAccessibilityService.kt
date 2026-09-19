package axis.sense.screen

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicReference
import axis.kernel.di.locate
import axis.kernel.events.AxisEvent
import axis.kernel.events.EventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Foreground-app tracker (spec §F9). AXIS uses accessibility for *context*,
 * not for drive-by automation: knowing which app is in front powers routine
 * triggers and "what am I looking at" chat answers.
 *
 * Node-tree scraping is intentionally shallow — a hash of the visible text
 * plus the window title — so the agent can say "the screen changed" without
 * AXIS hoarding screen contents.
 */
class AxisAccessibilityService : AccessibilityService() {

    private val bus: EventBus? get() = locate()

    override fun onServiceConnected() {
        super.onServiceConnected()
        granted.value = true
        bus?.tryEmit(AxisEvent.ServiceHealth("accessibility", true))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = e.packageName?.toString() ?: return
                if (pkg == packageName) return
                if (foregroundPackage.value != pkg) {
                    foregroundPackage.value = pkg
                    bus?.tryEmit(AxisEvent.ForegroundAppChanged(pkg))
                }
                val title = runCatching { e.text?.joinToString(" ")?.take(120) }.getOrNull()
                windowTitle.value = title
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val pkg = e.packageName?.toString() ?: return
                val hash = (e.text?.joinToString("|")?.hashCode() ?: 0) * 31 + pkg.hashCode()
                if (hash != lastTreeHash.getAndSet(hash) && lastTreeHash.get() != 0) {
                    bus?.tryEmit(AxisEvent.ScreenContentChanged(pkg, hash))
                }
            }
            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        granted.value = false
        bus?.tryEmit(AxisEvent.ServiceHealth("accessibility", false))
        super.onDestroy()
    }

    companion object {
        /** True while the service is bound (set by the service itself). */
        val granted = MutableStateFlow(false)

        /** Current foreground package, null when unknown. */
        val foregroundPackage = MutableStateFlow<String?>(null)

        /** Last window title we saw (often the app name or screen heading). */
        val windowTitle = MutableStateFlow<String?>(null)

        private val lastTreeHash = AtomicReference(0)

        /** Reads the sticky "enabled" secure setting (survives process death). */
        fun isEnabled(context: Context): Boolean = try {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            flat.split(':').any { it.contains(context.packageName) }
        } catch (_: Exception) {
            false
        }

        /** Our component id as the system expects it in the settings list. */
        fun componentId(context: Context): String =
            context.packageName + "/" + AxisAccessibilityService::class.java.name

        fun logEvent(event: String) = Timber.v("a11y: $event")
    }
}
