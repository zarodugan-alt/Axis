package axis.app.sidecar

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import axis.app.nav.Routes
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
 * Side-Car SYSTEMS rows (spec §S4). Overlay / usage-stats / providers are
 * real checks today; accessibility + notification rows honestly report
 * "not built yet" until the P2 services land (never fake greens).
 */
@Singleton
class PermissionChecks @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun systems(): List<SystemRow> = listOf(
        SystemRow(
            id = "accessibility",
            label = "Accessibility",
            status = DotStatus.OFF,
            detail = "Arrives in Phase 2"
        ),
        SystemRow(
            id = "notifications",
            label = "Notifications",
            status = DotStatus.OFF,
            detail = "Arrives in Phase 2"
        ),
        overlayRow(),
        usageRow(),
        SystemRow(
            id = "providers",
            label = "AI Providers",
            status = DotStatus.OFF,
            detail = "0/4 · not set up",
            fix = SystemFix.Route(Routes.SETTINGS_PROVIDERS)
        )
    )

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
            detail = if (granted) "Granted" else "Needed for floating HUD",
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

    // P2 implements: accessibilityEnabled() via ENABLED_ACCESSIBILITY_SERVICES,
    // notificationsEnabled() via ENABLED_NOTIFICATION_LISTENERS, plus the
    // 30s watchdog that flips rows to DEGRADED with heads-up + Fix action.
}
