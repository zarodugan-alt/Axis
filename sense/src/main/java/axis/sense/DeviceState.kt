package axis.sense

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import java.io.File

/**
 * A single sample of everything the home HUD shows. Cheap to build (no
 * binder round-trips beyond what the platform caches) and immutable, so the
 * UI can diff snapshots.
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
 * Reads device state (spec §F9 "SENSE"). Read-only and permission-light:
 * everything here works with zero runtime permissions on API 28+ — battery,
 * memory, storage, load average, network capabilities and torch state are
 * all publicly readable.
 */
class DeviceStateRepository(private val context: Context) {

    private val cameraManager: CameraManager?
        get() = context.getSystemService(CameraManager::class.java)

    fun sample(): DeviceSnapshot {
        val battery = readBattery()
        val net = readNetwork()
        val mem = readMemory()
        val storage = readStorage()
        return DeviceSnapshot(
            batteryPct = battery.pct,
            charging = battery.charging,
            batteryTempC = battery.tempC,
            batteryVoltageMv = battery.voltageMv,
            network = net.label,
            wifi = net.wifi,
            cellular = net.cellular,
            vpn = net.vpn,
            ramUsedPct = mem.usedPct,
            ramUsedMb = mem.usedMb,
            ramTotalMb = mem.totalMb,
            storageUsedPct = storage.usedPct,
            storageFreeGb = storage.freeGb,
            cpuLoad = readLoad(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            thermalC = battery.tempC,
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
        return try {
            val intent: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            if (intent == null) return Battery(0, false, 0f, 0)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (level < 0 || scale <= 0) 0 else level * 100 / scale
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val tempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            val mv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            Battery(pct, charging, tempC, mv)
        } catch (_: Exception) {
            Battery(0, false, 0f, 0)
        }
    }

    // ------------------------------------------------------------- network

    private data class Net(val label: String, val wifi: Boolean, val cellular: Boolean, val vpn: Boolean)

    private fun readNetwork(): Net {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return Net("offline", false, false, false)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return Net("offline", false, false, false)
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val cell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val label = when {
            vpn -> "vpn"
            wifi -> "wi-fi"
            cell -> "lte"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "eth"
            else -> "link"
        }
        val up = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return Net(if (up) label else "no-internet", wifi, cell, vpn)
    }

    // -------------------------------------------------------------- memory

    private data class Mem(val usedPct: Int, val usedMb: Int, val totalMb: Int)

    private fun readMemory(): Mem {
        val am = context.getSystemService(ActivityManager::class.java) ?: return Mem(0, 0, 0)
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val total = info.totalMem / (1024 * 1024)
        val avail = info.availMem / (1024 * 1024)
        val used = (total - avail).coerceAtLeast(0)
        val pct = if (total <= 0) 0 else (used * 100 / total).toInt()
        return Mem(pct, used.toInt(), total.toInt())
    }

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

    private fun readScreenOn(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) pm.isInteractive else @Suppress("DEPRECATION") pm.isScreenOn
    }

    // --------------------------------------------------------------- torch

    // Torch state is *tracked*, not polled: camera2 has no public
    // getTorchMode(), so the repository subscribes to the platform callback
    // once and mirrors it into an atomic flag.
    private val torchState = java.util.concurrent.atomic.AtomicBoolean(false)

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
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm?.currentInterruptionFilter ?: 0
    } catch (_: Exception) {
        0
    }

    fun hasNotificationPolicyAccess(): Boolean = try {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm?.isNotificationPolicyAccessGranted == true
    } catch (_: Exception) {
        false
    }

    /** Only meaningful with WRITE_SETTINGS (checked by the caller). */
    fun canWriteSettings(): Boolean = try {
        android.provider.Settings.System.canWrite(context)
    } catch (_: Exception) {
        false
    }

    private fun readRotationLocked(): Boolean = try {
        android.provider.Settings.System.getInt(
            context.contentResolver,
            android.provider.Settings.System.ACCELEROMETER_ROTATION,
            1
        ) == 0
    } catch (_: Exception) {
        false
    }

    private fun readBrightness(): Int = try {
        android.provider.Settings.System.getInt(
            context.contentResolver,
            android.provider.Settings.System.SCREEN_BRIGHTNESS,
            128
        )
    } catch (_: Exception) {
        128
    }

    /** Installed launcher count — used by the about/system panel. */
    fun installedAppCount(): Int = try {
        @Suppress("DEPRECATION")
        context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo?.packageName }
            .size
    } catch (_: Exception) {
        0
    }

    fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
