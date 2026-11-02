package dev.podfinder.core

import kotlin.math.roundToLong

/**
 * Turns signal strength into a haptic pulse cadence for the "walk toward them" finder: the stronger
 * the signal, the faster the buzzing, like a metal detector. Pure and immutable.
 */
object HapticCadence {
    /** Below this smoothed RSSI, don't buzz at all — too far to be useful guidance. */
    const val SILENT_BELOW_DBM = -92.0
    const val NEAR_DBM = -48.0
    const val SLOWEST_INTERVAL_MILLIS = 1_500L
    const val FASTEST_INTERVAL_MILLIS = 110L

    /**
     * Milliseconds between pulses for the given smoothed RSSI, or null when it should stay silent
     * (signal lost/stale or too weak to guide). Interpolates linearly between the slowest and
     * fastest cadence across [SILENT_BELOW_DBM]..[NEAR_DBM].
     */
    fun pulseIntervalMillis(smoothedRssi: Double?): Long? {
        val rssi = smoothedRssi ?: return null
        if (rssi < SILENT_BELOW_DBM) return null
        val clamped = rssi.coerceIn(SILENT_BELOW_DBM, NEAR_DBM)
        val fraction = (clamped - SILENT_BELOW_DBM) / (NEAR_DBM - SILENT_BELOW_DBM)
        val interval = SLOWEST_INTERVAL_MILLIS + fraction * (FASTEST_INTERVAL_MILLIS - SLOWEST_INTERVAL_MILLIS)
        return interval.roundToLong()
    }
}
