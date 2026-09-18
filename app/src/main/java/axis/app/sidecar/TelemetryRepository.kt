package axis.app.sidecar

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device telemetry without any permission: RAM via ActivityManager, battery
 * via the sticky ACTION_BATTERY_CHANGED intent (level + temperature —
 * thermal headroom API is 29+, so temp-from-battery is the API-28 answer).
 */
@Singleton
class TelemetryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Sample(
        val ramPct: Int,
        val batPct: Int,
        val batTempC: Float,
        val charging: Boolean
    )

    fun sample(): Sample {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mem)
        val ramPct = if (mem.totalMem > 0) {
            ((1.0 - mem.availMem.toDouble() / mem.totalMem) * 100).toInt()
        } else 0

        val bat = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val status = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return Sample(
            ramPct = ramPct.coerceIn(0, 100),
            batPct = if (level >= 0) (level * 100 / scale).coerceIn(0, 100) else 0,
            batTempC = temp,
            charging = charging
        )
    }
}
