package axis.act.action

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

/** Outcome of one system action, with the truth about why it failed. */
data class ActionResult(
    val ok: Boolean,
    val detail: String,
    /** Set when the user must grant something for this to ever work. */
    val needsPermission: String? = null
)

/**
 * The actual device mutations behind routines and agent tools (spec §F12).
 * Every method reports honestly what happened — no optimistic "done" when
 * the platform refused; callers surface [ActionResult.detail] verbatim.
 */
class SystemActions(private val context: Context) {

    private val audio: AudioManager?
        get() = context.getSystemService(AudioManager::class.java)

    private val notifications: NotificationManager?
        get() = context.getSystemService(NotificationManager::class.java)

    // ------------------------------------------------------------- audio

    /** mode: 0 = silent, 1 = vibrate, 2 = normal (AudioManager ringer modes). */
    fun setRingerMode(mode: Int): ActionResult {
        val am = audio ?: return ActionResult(false, "audio service unavailable")
        return try {
            @Suppress("DEPRECATION")
            am.ringerMode = mode.coerceIn(0, 2)
            val label = when (mode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "normal"
            }
            ActionResult(true, "ringer → $label")
        } catch (t: Throwable) {
            if (t is SecurityException) {
                ActionResult(false, "DND access not granted", "notification policy access")
            } else {
                ActionResult(false, t.message ?: "ringer change failed")
            }
        }
    }

    fun setMediaVolume(percent: Int): ActionResult {
        val am = audio ?: return ActionResult(false, "audio service unavailable")
        return try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * percent / 100).coerceIn(0, max), 0)
            ActionResult(true, "media volume → $percent%")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: "volume change failed")
        }
    }

    // --------------------------------------------------------------- dnd

    fun setDnd(on: Boolean): ActionResult {
        val nm = notifications ?: return ActionResult(false, "notification service unavailable")
        if (!nm.isNotificationPolicyAccessGranted) {
            return ActionResult(
                false,
                "Grant DND access in Settings → Notification access",
                "do not disturb access"
            )
        }
        return try {
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            ActionResult(true, "do not disturb → ${if (on) "on" else "off"}")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: "DND change failed")
        }
    }

    // ------------------------------------------------------- display

    fun setBrightness(percent: Int): ActionResult {
        if (!Settings.System.canWrite(context)) {
            return ActionResult(
                false,
                "Grant \"Modify system settings\" to control brightness",
                "write settings"
            )
        }
        return try {
            val value = (255 * percent.coerceIn(5, 100) / 100)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
            ActionResult(true, "brightness → $percent%")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: "brightness change failed")
        }
    }

    fun setRotationLocked(locked: Boolean): ActionResult {
        if (!Settings.System.canWrite(context)) {
            return ActionResult(false, "Grant \"Modify system settings\" to lock rotation", "write settings")
        }
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (locked) 0 else 1
            )
            ActionResult(true, "rotation → ${if (locked) "locked" else "auto"}")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: "rotation change failed")
        }
    }

    // --------------------------------------------------------- flashlight

    suspend fun setTorch(on: Boolean): ActionResult = try {
        val cm = context.getSystemService(CameraManager::class.java)
        if (cm == null) {
            ActionResult(false, "camera service unavailable")
        } else {
        val id = cm.cameraIdList.firstOrNull { cam ->
            cm.getCameraCharacteristics(cam)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) {
            ActionResult(false, "no flash unit on this device")
        } else suspendCancellableCoroutine { cont ->
            val callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == id) {
                        cm.unregisterTorchCallback(this)
                        if (cont.isActive) cont.resume(ActionResult(true, "torch → ${if (on) "on" else "off"}"))
                    }
                }

                override fun onTorchModeUnavailable(cameraId: String) {
                    cm.unregisterTorchCallback(this)
                    if (cont.isActive) cont.resume(ActionResult(false, "torch busy (camera in use)"))
                }
            }
            cm.registerTorchCallback(callback, null)
            try {
                cm.setTorchMode(id, on)
            } catch (t: Throwable) {
                cm.unregisterTorchCallback(callback)
                if (cont.isActive) cont.resume(ActionResult(false, t.message ?: "torch failed"))
            }
        }
        }
    } catch (t: Throwable) {
        ActionResult(false, t.message ?: "torch failed")
    }

    // ------------------------------------------------------------ intents

    fun launchApp(packageName: String): ActionResult {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ActionResult(false, "no launchable activity in $packageName")
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionResult(true, "launched $packageName")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: "launch failed")
        }
    }

    fun openUrl(url: String): ActionResult {
        val normalized = if (url.startsWith("http")) url else "https://$url"
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ActionResult(true, "opened $normalized")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: "open failed")
        }
    }

    /** Named system pages routines and the agent may jump to. */
    fun openSettingsPage(page: String): ActionResult {
        val action = when (page.lowercase()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "dnd", "do not disturb" -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notification", "notification access" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "overlay", "appear on top" -> Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            "usage", "usage access" -> Settings.ACTION_USAGE_ACCESS_SETTINGS
            "settings" -> Settings.ACTION_SETTINGS
            else -> return ActionResult(false, "unknown settings page \"$page\"")
        }
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionResult(true, "opened $page settings")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: "settings page failed")
        }
    }

    /** Opens a specific app's detail page in system settings. */
    fun openAppInfo(packageName: String): ActionResult = try {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        ActionResult(true, "opened app info for $packageName")
    } catch (t: Throwable) {
        ActionResult(false, t.message ?: "app info failed")
    }

    /** Request to become the default launcher again (used by onboarding). */
    fun openHomeSettings(): ActionResult = try {
        context.startActivity(
            Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        ActionResult(true, "opened home settings")
    } catch (t: Throwable) {
        ActionResult(false, t.message ?: "home settings failed")
    }

    fun enableComponent(component: ComponentName, enabled: Boolean) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                component,
                if (enabled) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }.onFailure { Timber.w(it, "component toggle failed") }
    }
}
