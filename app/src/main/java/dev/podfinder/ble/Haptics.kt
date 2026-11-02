package dev.podfinder.ble

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.podfinder.core.HapticCadence

/**
 * Metal-detector style haptics: pulses at a cadence driven by signal strength. Call [update] with
 * the latest smoothed RSSI; closer buzzes faster, out-of-range goes silent. Call [stop] to end.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
        else -> @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }
    private val handler = Handler(Looper.getMainLooper())
    private var intervalMillis: Long? = null
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            val interval = intervalMillis ?: return
            pulse()
            handler.postDelayed(this, interval)
        }
    }

    fun update(smoothedRssi: Double?) {
        val next = HapticCadence.pulseIntervalMillis(smoothedRssi)
        val wasSilent = intervalMillis == null
        intervalMillis = next
        if (next == null) {
            handler.removeCallbacks(tick)
            return
        }
        if (wasSilent && running) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    fun start() {
        if (running) return
        running = true
        if (intervalMillis != null) handler.post(tick)
    }

    fun stop() {
        running = false
        intervalMillis = null
        handler.removeCallbacks(tick)
    }

    private fun pulse() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(PULSE_DURATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(PULSE_DURATION_MILLIS)
        }
    }

    private companion object {
        const val PULSE_DURATION_MILLIS = 40L
    }
}
