package dev.podfinder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProximityMeterTest {

    @Test
    fun `first reading sets the level directly`() {
        val meter = ProximityMeter().update(rssi = -55, nowMillis = 0)

        assertEquals(-55.0, meter.smoothedRssi!!, 0.0)
        assertEquals(ProximityMeter.Warmth.HOT, meter.warmth(nowMillis = 0))
        assertEquals(ProximityMeter.Trend.UNKNOWN, meter.trend())
    }

    @Test
    fun `smooths a single outlier`() {
        val meter = ProximityMeter()
            .update(-80, 0)
            .update(-80, 500)
            .update(-40, 1_000)

        assertEquals(-70.0, meter.smoothedRssi!!, 0.001)
        assertEquals(ProximityMeter.Warmth.WARM, meter.warmth(1_000))
    }

    @Test
    fun `reports warmer when walking toward the device`() {
        val meter = ProximityMeter().update(-85, 0).update(-75, 500)

        assertEquals(ProximityMeter.Trend.WARMER, meter.trend())
    }

    @Test
    fun `reports colder when walking away`() {
        val meter = ProximityMeter().update(-60, 0).update(-75, 500)

        assertEquals(ProximityMeter.Trend.COLDER, meter.trend())
    }

    @Test
    fun `stale signal reads as lost and restarts smoothing`() {
        val meter = ProximityMeter().update(-50, 0)
        val later = ProximityMeter.STALE_AFTER_MILLIS + 1

        assertEquals(ProximityMeter.Warmth.LOST, meter.warmth(later))
        val restarted = meter.update(-90, later)
        assertEquals(-90.0, restarted.smoothedRssi!!, 0.0)
        assertNull(restarted.previousSmoothedRssi)
        assertEquals(ProximityMeter.Warmth.COLD, restarted.warmth(later))
    }

    @Test
    fun `approximate distance is about one metre at measured power`() {
        val meter = ProximityMeter().update(-59, 0)

        assertEquals(1.0, meter.approximateMetres()!!, 0.0)
    }

    @Test
    fun `update does not mutate the original meter`() {
        val original = ProximityMeter().update(-60, 0)
        original.update(-40, 100)

        assertEquals(-60.0, original.smoothedRssi!!, 0.0)
    }
}
