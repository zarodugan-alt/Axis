package axis.sense

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One sample of everything the home board's telemetry strip shows.
 *
 * Every field is a real platform read; when a read is unavailable the field
 * falls back to a neutral value rather than a plausible-looking fake.
 */
data class DeviceSnapshot(
    val batteryPct: Int = 0,
    val charging: Boolean = false,
    val batteryTempC: Float = 0f,
    val batteryVoltageMv: Int = 0,
    val network: String = "offline",
    val wifi: Boolean = false,
    val cellular: Boolean = false,
    val vpn: Boolean = false,
    val ramUsedPct: Int = 0,
    val ramUsedMb: Int = 0,
    val ramTotalMb: Int = 0,
    val storageUsedPct: Int = 0,
    val storageFreeGb: Float = 0f,
    val cpuLoad: Float = 0f,
    val cpuCores: Int = 1,
    val thermalC: Float = 0f,
    val uptimeMs: Long = 0L,
    val screenOn: Boolean = false,
    val torchOn: Boolean = false,
    val dndFilter: Int = 0,
    val rotationLocked: Boolean = false,
    val brightness: Int = 0
) {
    val dndOn: Boolean get() = dndFilter != 0
}

/**
 * Reads device state for the board (spec §F1, §S2) and the Side-Car systems
 * panel. Cheap enough to call on a 2-second tick: every read is a system
 * service getter or a small /proc or StatFs call.
 */
class DeviceStateRepository(private val context: Context) {

    private val cameraManager: CameraManager? =
        context.getSystemService(CameraManager::class.java)

    fun sample(): DeviceSnapshot {
        val battery = readBattery()
        val network = readNetwork()
        val mem = readMemory()
        val store = readStorage()
        return DeviceSnapshot(
            batteryPct = battery.pct,
            charging = battery.charging,
            batteryTempC = battery.tempC,
            batteryVoltageMv = battery.voltageMv,
            network = network.first,
            wifi = network.second,
            cellular = network.third,
            vpn = network.fourth,
            ramUsedPct = mem.usedPct,
            ramUsedMb = mem.usedMb,
            ramTotalMb = mem.totalMb,
            storageUsedPct = store.usedPct,
            storageFreeGb = store.freeGb,
            cpuLoad = readLoad(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            thermalC = readThermal(),
            uptimeMs = SystemClock.elapsedRealtime(),
            screenOn = readScreenOn(),
            torchOn = readTorch(),
            dndFilter = readDndFilter(),
            rotationLocked = readRotationLocked(),
            brightness = readBrightness()
        )
    }

    // ------------------------------------------------------------- battery

    private data class Battery(val pct: Int, val charging: Boolean, val tempC: Float, val voltageMv: Int)

    private fun readBattery(): Battery {
        val intent: Intent? = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        } ?: return Battery(0, false, 0f, 0)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        return Battery(pct, charging, temp, voltage)
    }

    // ------------------------------------------------------------- network

    /** @return network label, wifi, cellular, vpn. */
    private fun readNetwork(): Quadruple {
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
                ?: return Quadruple("offline", false, false, false)
            val active = cm.activeNetwork ?: return Quadruple("offline", false, false, false)
            val caps = cm.getNetworkCapabilities(active)
                ?: return Quadruple("offline", false, false, false)
            val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            val label = when {
                vpn -> "vpn"
                wifi -> "wifi"
                ethernet -> "ethernet"
                cellular -> when {
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> "mobile"
                    else -> "mobile"
                }
                else -> "online"
            }
            Quadruple(label, wifi, cellular, vpn)
        } catch (_: Exception) {
            Quadruple("offline", false, false, false)
        }
    }

    private data class Quadruple(val first: String, val second: Boolean, val third: Boolean, val fourth: Boolean)

    // -------------------------------------------------------------- memory

    private data class Mem(val usedPct: Int, val usedMb: Int, val totalMb: Int)

    private fun readMemory(): Mem {
        return try {
            val am = context.getSystemService(ActivityManager::class.java) ?: return Mem(0, 0, 0)
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val total = info.totalMem
            val avail = info.availMem
            val used = (total - avail).coerceAtLeast(0)
            val pct = if (total <= 0) 0 else (used * 100 / total).toInt()
            Mem(pct, (used / (1024 * 1024)).toInt(), (total / (1024 * 1024)).toInt())
        } catch (_: Exception) {
            Mem(0, 0, 0)
        }
    }

    // ------------------------------------------------------------- storage

    private data class Store(val usedPct: Int, val freeGb: Float)

    private fun readStorage(): Store {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            val usedPct = if (total <= 0) 0 else ((total - free) * 100 / total).toInt()
            Store(usedPct, free / (1024f * 1024f * 1024f))
        } catch (_: Exception) {
            Store(0, 0f)
        }
    }

    /** 1-minute load average normalised by core count. Reads /proc/loadavg. */
    private fun readLoad(): Float {
        return try {
            val raw = File("/proc/loadavg").readText().trim().split(" ").firstOrNull() ?: return 0f
            val load = raw.toFloatOrNull() ?: return 0f
            (load / Runtime.getRuntime().availableProcessors()).coerceIn(0f, 1f)
        } catch (_: Exception) {
            0f
        }
    }

    private fun readThermal(): Float {
        // No public thermal API below API 29; approximate from the battery
        // sensor, which is the signal the OS itself throttles on.
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
        return (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    }

    private fun readScreenOn(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) pm.isInteractive else pm.isScreenOn
    }

    // --------------------------------------------------------------- torch

    // Torch state is *tracked*, not polled: camera2 has no public
    // getTorchMode(), so the repository subscribes to the platform callback
    // once and mirrors it into an atomic flag.
    private val torchState = AtomicBoolean(false)

    @Volatile
    private var torchTracking = false

    private fun ensureTorchTracking() {
        if (torchTracking) return
        val cm = cameraManager ?: return
        runCatching {
            cm.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        torchState.set(enabled)
                    }

                    override fun onTorchModeUnavailable(cameraId: String) {
                        torchState.set(false)
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper())
            )
            torchTracking = true
        }
    }

    fun readTorch(): Boolean {
        ensureTorchTracking()
        return torchState.get()
    }

    fun hasFlash(): Boolean = try {
        cameraManager?.cameraIdList?.any { camId ->
            cameraManager?.getCameraCharacteristics(camId)
                ?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } == true
    } catch (_: Exception) {
        false
    }

    // ----------------------------------------------------------- settings

    private fun readDndFilter(): Int = try {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.currentInterruptionFilter ?: 0
    } catch (_: Exception) {
        0
    }

    fun hasNotificationPolicyAccess(): Boolean = try {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.isNotificationPolicyAccessGranted == true
    } catch (_: Exception) {
        false
    }

    private fun readRotationLocked(): Boolean = try {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            1
        ) == 0
    } catch (_: Exception) {
        false
    }

    private fun readBrightness(): Int = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    } catch (_: Exception) {
        0
    }

    fun canWriteSettings(): Boolean = try {
        Settings.System.canWrite(context)
    } catch (_: Exception) {
        false
    }

    fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun installedAppCount(): Int = try {
        context.packageManager.getInstalledApplications(0).size
    } catch (_: Exception) {
        0
    }
}
