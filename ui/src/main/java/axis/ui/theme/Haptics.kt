package axis.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi

/**
 * Haptics map (spec §2.7). Callers pass the master-toggle state ([enabled]);
 * when false everything is a no-op. `View.performHapticFeedback` needs no
 * permission; the [Vibrator] pulses need VIBRATE (declared in :app).
 *
 * All VibrationEffect APIs used here are API 26+; minSdk is 28, so no
 * version guards are needed — but lint's NewApi gate verifies that in CI.
 */
object AxisHaptics {

    fun press(view: View, enabled: Boolean = true) {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun pageSettle(view: View, enabled: Boolean = true) {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun longPress(view: View, enabled: Boolean = true) {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun tick(view: View, enabled: Boolean = true) {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun stepVerified(context: Context, enabled: Boolean = true) {
        vibrate(context, enabled, longArrayOf(0, 15), intArrayOf(0, 80))
    }

    fun taskComplete(context: Context, enabled: Boolean = true) {
        vibrate(context, enabled, longArrayOf(0, 20, 60, 20), intArrayOf(0, 120, 0, 120))
    }

    fun error(context: Context, view: View? = null, enabled: Boolean = true) {
        if (!enabled) return
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        vibrate(context, true, longArrayOf(0, 120), intArrayOf(0, 200))
    }

    fun killSwitch(context: Context, enabled: Boolean = true) {
        vibrate(context, enabled, longArrayOf(0, 200), intArrayOf(0, 255))
    }

    private fun vibrate(context: Context, enabled: Boolean, timings: LongArray, amplitudes: IntArray) {
        if (!enabled) return
        try {
            val vibrator = context.getSystemService(Vibrator::class.java) ?: return
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } catch (_: SecurityException) {
            // VIBRATE permission missing — degrade silently, never crash.
        }
    }

    /** True when the device can play [VibrationEffect] waveforms. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun supportsWaveforms(context: Context): Boolean =
        context.getSystemService(Vibrator::class.java)?.hasVibrator() == true
}
