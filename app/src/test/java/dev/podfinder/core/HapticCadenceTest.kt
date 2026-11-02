package dev.podfinder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticCadenceTest {

    @Test
    fun `silent when no signal`() {
        assertNull(HapticCadence.pulseIntervalMillis(null))
    }

    @Test
    fun `silent when too far`() {
        assertNull(HapticCadence.pulseIntervalMillis(-95.0))
    }

    @Test
    fun `fastest cadence when right next to it`() {
        assertEquals(HapticCadence.FASTEST_INTERVAL_MILLIS, HapticCadence.pulseIntervalMillis(-40.0))
    }

    @Test
    fun `slowest cadence at the far edge of range`() {
        assertEquals(HapticCadence.SLOWEST_INTERVAL_MILLIS, HapticCadence.pulseIntervalMillis(-92.0))
    }

    @Test
    fun `closer always buzzes faster than farther`() {
        val near = HapticCadence.pulseIntervalMillis(-55.0)!!
        val far = HapticCadence.pulseIntervalMillis(-80.0)!!

        assertTrue("near ($near) should be a shorter interval than far ($far)", near < far)
    }
}
