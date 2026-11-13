package app.poolt

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/** Short explicit pulses, with system haptic as a fallback. */
class HapticEngine(private val context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        }

    fun tap(view: View, enabled: Boolean) {
        if (!enabled) return
        val vibrated = runCatching {
            val actuator = vibrator
            if (actuator != null && actuator.hasVibrator()) {
                actuator.vibrate(VibrationEffect.createOneShot(22L, 135))
                true
            } else false
        }.getOrDefault(false)
        if (!vibrated) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}
