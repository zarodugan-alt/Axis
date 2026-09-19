package axis.act.action

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Result of a system action, with an honest failure message. */
data class ActionResult(
    val ok: Boolean,
    val detail: String,
    val needsPermission: String? = null
)

/**
 * The write side of `:act` (spec §F11–F13): system toggles the agent, the
 * routines and the home board can trigger.
 *
 * Every action returns an [ActionResult] instead of throwing, and every
 * permission-gated action says which permission is missing rather than
 * silently doing nothing. Nothing here is asynchronous except the torch,
 * which must wait for the camera callback.
 */
class SystemActions(private val context: Context) {

    // ------------------------------------------------------------ audio

    fun setRingerMode(mode: Int): ActionResult {
        return try {
            val am = context.getSystemService(AudioManager::class.java)
                ?: return ActionResult(false, "audio service unavailable")
            am.ringerMode = mode.coerceIn(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_NORMAL)
            val label = when (am.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "normal"
            }
            ActionResult(true, "ringer → $label")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: "ringer change failed")
        }
    }

    fun setMediaVolume(percent: Int): ActionResult {
        return try {
            val am = context.getSystemService(AudioManager::class.java)
                ?: return ActionResult(false, "audio service unavailable")
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (max * percent.coerceIn(0, 100) / 100f).toInt().coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            ActionResult(true, "media volume → ${target * 100 / max}%")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: "volume change failed")
        }
    }

    // --------------------------------------------------------------- dnd

    fun setDnd(on: Boolean): ActionResult {
        val nm = context.getSystemService(NotificationManager::class.java)
            ?: return ActionResult(false, "notification service unavailable")
        if (!nm.isNotificationPolicyAccessGranted) {
            return ActionResult(
                false,
                "Do Not Disturb access not granted",
                needsPermission = "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"
            )
        }
        return try {
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            ActionResult(true, "dnd → ${if (on) "on" else "off"}")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: "dnd change failed")
        }
    }

    // ------------------------------------------------------ display pages

    fun setBrightness(percent: Int): ActionResult {
        if (!Settings.System.canWrite(context)) {
            return ActionResult(
                false,
                "AXIS needs WRITE_SETTINGS to change brightness",
                needsPermission = Settings.ACTION_MANAGE_WRITE_SETTINGS
            )
        }
        return try {
            val value = (255 * percent.coerceIn(5, 100) / 100f).toInt().coerceIn(1, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
            ActionResult(true, "brightness → ${percent.coerceIn(5, 100)}%")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: "brightness change failed")
        }
    }

    fun setRotationLocked(locked: Boolean): ActionResult {
        if (!Settings.System.canWrite(context)) {
            return ActionResult(
                false,
                "AXIS needs WRITE_SETTINGS to lock rotation",
                needsPermission = Settings.ACTION_MANAGE_WRITE_SETTINGS
            )
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
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) {
                ActionResult(false, "no flash unit on this device")
            } else suspendCancellableCoroutine { cont ->
                val callback = object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId == id) {
                            cm.unregisterTorchCallback(this)
                            if (cont.isActive) {
                                cont.resume(ActionResult(true, "torch → ${if (on) "on" else "off"}"))
                            }
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
        val normalised = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(normalised))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ActionResult(true, "opened $normalised")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: "could not open link")
        }
    }

    fun openAppInfo(packageName: String): ActionResult = try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        ActionResult(true, "opened app info")
    } catch (t: Throwable) {
        ActionResult(false, t.message ?: "could not open app info")
    }

    fun openHomeSettings(): ActionResult = try {
        context.startActivity(
            Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        ActionResult(true, "opened home settings")
    } catch (t: Throwable) {
        ActionResult(false, t.message ?: "could not open home settings")
    }

    fun enableComponent(component: ComponentName, enabled: Boolean) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                component,
                if (enabled) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
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
}
