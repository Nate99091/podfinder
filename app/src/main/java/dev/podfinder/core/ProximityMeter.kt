package dev.podfinder.core

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Smooths noisy BLE RSSI readings into a "hot / cold" reading for walking toward a device.
 * Immutable: [update] returns a new meter.
 */
data class ProximityMeter(
    val smoothedRssi: Double? = null,
    val previousSmoothedRssi: Double? = null,
    val lastSeenMillis: Long? = null,
    val sampleCount: Int = 0,
) {
    enum class Warmth(val label: String) {
        RIGHT_HERE("Right here"),
        HOT("Hot"),
        WARM("Warm"),
        COOL("Cool"),
        COLD("Cold"),
        LOST("No signal"),
    }

    enum class Trend { WARMER, COLDER, STEADY, UNKNOWN }

    fun update(rssi: Int, nowMillis: Long): ProximityMeter {
        val isStale = lastSeenMillis == null || nowMillis - lastSeenMillis > STALE_AFTER_MILLIS
        val next = if (isStale || smoothedRssi == null) {
            rssi.toDouble()
        } else {
            SMOOTHING * rssi + (1 - SMOOTHING) * smoothedRssi
        }
        return ProximityMeter(
            smoothedRssi = next,
            previousSmoothedRssi = if (isStale) null else smoothedRssi,
            lastSeenMillis = nowMillis,
            sampleCount = if (isStale) 1 else sampleCount + 1,
        )
    }

    fun warmth(nowMillis: Long): Warmth {
        val rssi = smoothedRssi ?: return Warmth.LOST
        val seen = lastSeenMillis ?: return Warmth.LOST
        if (nowMillis - seen > STALE_AFTER_MILLIS) return Warmth.LOST
        return when {
            rssi >= -50 -> Warmth.RIGHT_HERE
            rssi >= -62 -> Warmth.HOT
            rssi >= -74 -> Warmth.WARM
            rssi >= -86 -> Warmth.COOL
            else -> Warmth.COLD
        }
    }

    fun trend(): Trend {
        val current = smoothedRssi ?: return Trend.UNKNOWN
        val previous = previousSmoothedRssi ?: return Trend.UNKNOWN
        val delta = current - previous
        return when {
            delta >= TREND_THRESHOLD_DB -> Trend.WARMER
            delta <= -TREND_THRESHOLD_DB -> Trend.COLDER
            else -> Trend.STEADY
        }
    }

    /** Very rough log-distance estimate in metres; BLE indoors is easily off by 2-3x. */
    fun approximateMetres(): Double? {
        val rssi = smoothedRssi ?: return null
        val metres = 10.0.pow((MEASURED_POWER_AT_1M - rssi) / (10 * PATH_LOSS_EXPONENT))
        return (metres * 10).roundToInt() / 10.0
    }

    companion object {
        const val STALE_AFTER_MILLIS = 8_000L
        const val SMOOTHING = 0.25
        const val TREND_THRESHOLD_DB = 0.5
        const val MEASURED_POWER_AT_1M = -59.0
        const val PATH_LOSS_EXPONENT = 2.2
    }
}
