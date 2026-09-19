package axis.app.sidecar

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import axis.app.data.ProviderStore
import axis.app.nav.Routes
import axis.sense.notify.NotificationAccess
import axis.sense.screen.AxisAccessibilityService
import axis.ui.components.DotStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SystemRow(
    val id: String,
    val label: String,
    val status: DotStatus,
    val detail: String,
    val fix: SystemFix? = null
)

sealed interface SystemFix {
    /** Navigate to an in-app route (e.g. providers). */
    data class Route(val route: String) : SystemFix

    /** Open a system settings page (package URI appended by the caller). */
    data class SettingsAction(val action: String) : SystemFix
}

/**
 * Side-Car SYSTEMS rows (spec §S4). Every row is a live platform check —
 * accessibility, notifications, overlay, usage stats, DND, write-settings and
 * the provider count. Nothing here reports a state the device has not
 * actually granted.
 */
@Singleton
class PermissionChecks @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providers: ProviderStore
) {
    fun systems(): List<SystemRow> = listOf(
        accessibilityRow(),
        notificationsRow(),
        overlayRow(),
        usageRow(),
        dndRow(),
        writeSettingsRow(),
        providersRow()
    )

    private fun accessibilityRow(): SystemRow {
        val enabled = AxisAccessibilityService.isEnabled(context)
        return SystemRow(
            id = "accessibility",
            label = "Accessibility",
            status = if (enabled) DotStatus.ON else DotStatus.OFF,
            detail = if (enabled) "Foreground app context live"
            else "Off — AXIS cannot see the app in front",
            fix = if (enabled) null
            else SystemFix.SettingsAction(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
    }

    private fun notificationsRow(): SystemRow {
        val granted = NotificationAccess.isGranted(context)
        return SystemRow(
            id = "notifications",
            label = "Notification access",
            status = if (granted) DotStatus.ON else DotStatus.OFF,
            detail = if (granted) "Listener connected"
            else "Off — notifications are not captured",
            fix = if (granted) null
            else SystemFix.SettingsAction("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        )
    }

    private fun overlayRow(): SystemRow {
        val granted = try {
            Settings.canDrawOverlays(context)
        } catch (_: Exception) {
            false
        }
        return SystemRow(
            id = "overlay",
            label = "Appear on top",
            status = if (granted) DotStatus.ON else DotStatus.OFF,
            detail = if (granted) "Granted" else "Needed for the floating Side-Car",
            fix = if (granted) null
            else SystemFix.SettingsAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        )
    }

    private fun usageRow(): SystemRow {
        val granted = usageGranted()
        return SystemRow(
            id = "usage",
            label = "Usage stats",
            status = if (granted) DotStatus.ON else DotStatus.OFF,
            detail = if (granted) "Granted" else "For smarter suggestions",
            fix = if (granted) null
            else SystemFix.SettingsAction(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        )
    }

    private fun dndRow(): SystemRow {
        val granted = try {
            context.getSystemService(android.app.NotificationManager::class.java)
                ?.isNotificationPolicyAccessGranted == true
        } catch (_: Exception) {
            false
        }
        return SystemRow(
            id = "dnd",
            label = "Do Not Disturb control",
            status = if (granted) DotStatus.ON else DotStatus.OFF,
            detail = if (granted) "Granted" else "Needed to toggle DND for routines",
            fix = if (granted) null
            else SystemFix.SettingsAction(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        )
    }

    private fun writeSettingsRow(): SystemRow {
        val granted = try {
            Settings.System.canWrite(context)
        } catch (_: Exception) {
            false
        }
        return SystemRow(
            id = "write",
            label = "Modify system settings",
            status = if (granted) DotStatus.ON else DotStatus.OFF,
            detail = if (granted) "Granted" else "Needed for brightness and rotation",
            fix = if (granted) null
            else SystemFix.SettingsAction(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        )
    }

    private fun providersRow(): SystemRow {
        val connected = providers.connectedIds.value
        val chat = providers.providers.value.count { it.isChat && it.id in connected }
        val speech = providers.providers.value.count { it.isSpeech && it.id in connected }
        val total = providers.providers.value.count { it.isChat }
        val ready = chat > 0
        return SystemRow(
            id = "providers",
            label = "AI Providers",
            status = if (ready) DotStatus.ON else DotStatus.OFF,
            detail = when {
                chat == 0 -> "0/$total connected — basic mode"
                speech == 0 -> "$chat/$total chat · on-device voice"
                else -> "$chat/$total chat · $speech voice"
            },
            fix = if (ready) null else SystemFix.Route(Routes.SETTINGS_PROVIDERS)
        )
    }

    @Suppress("DEPRECATION") // unsafeCheckOpNoThrow is 29+; minSdk is 28
    private fun usageGranted(): Boolean {
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }
}
