package dev.podfinder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are real captures published in CAPod's test suite (AirPodsPro2Test, DualApplePodsTest). */
class AdvertDecodingTest {

    private fun proximity(hex: String): ProximityPairing.Status {
        val message = AppleContinuity.parse(hexToBytes(hex)).single()
        return requireNotNull(ProximityPairing.decode(message))
    }

    @Test
    fun `decodes AirPods Pro 2 in case, all charging`() {
        val status = proximity("07 19 01 14 20 55 88 F9 51 00 04 20 50 03 CA D5 C9 AC 0F FA 84 78 94 5A 4D DF F5")

        assertEquals(0x1420, status.modelId)
        assertEquals("AirPods Pro 2", status.modelName)
        assertEquals(ProximityPairing.Pod(80, isCharging = true, isInEar = false), status.left)
        assertEquals(ProximityPairing.Pod(80, isCharging = true, isInEar = false), status.right)
        assertEquals(ProximityPairing.Pod(90, isCharging = true, isInEar = false), status.case)
    }

    @Test
    fun `decodes AirPods Pro 2 in ears with unreported case battery`() {
        val status = proximity("07 19 01 14 20 2B 9A 8F 01 00 04 0F 26 1A C4 2B FA 2F B9 B6 08 CD 60 CB DF 75 AB")

        assertEquals(ProximityPairing.Pod(100, isCharging = false, isInEar = true), status.left)
        assertEquals(ProximityPairing.Pod(90, isCharging = false, isInEar = true), status.right)
        assertEquals(ProximityPairing.Pod(null, isCharging = false, isInEar = false), status.case)
    }

    @Test
    fun `decodes first-gen AirPods Pro model`() {
        val status = proximity("07 19 01 0E 20 54 AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4")

        assertEquals("AirPods Pro", status.modelName)
        assertEquals(50, status.case.batteryPercent)
    }

    @Test
    fun `rejects proximity message with unsupported prefix`() {
        val message = AppleContinuity.parse(
            hexToBytes("07 19 07 14 20 55 88 F9 51 00 04 20 50 03 CA D5 C9 AC 0F FA 84 78 94 5A 4D DF F5"),
        ).single()

        assertNull(ProximityPairing.decode(message))
    }

    @Test
    fun `parses multiple TLV messages and drops truncated tail`() {
        val messages = AppleContinuity.parse(hexToBytes("10 02 0B 1C 12 02 34 00 07 19 01"))

        assertEquals(listOf(0x10, 0x12), messages.map { it.type })
        assertEquals(2, messages[1].length)
    }

    @Test
    fun `decodes separated Find My AirPods advert`() {
        // Long-form (0x19) offline-finding message, status byte 0x30 = AirPods, battery full.
        val payload = hexToBytes("12 19 30" + " 00".repeat(24))
        val status = OfflineFinding.decode(AppleContinuity.parse(payload).single())!!

        assertEquals(OfflineFinding.DeviceType.AIRPODS, status.deviceType)
        assertEquals(OfflineFinding.Battery.FULL, status.battery)
        assertTrue(status.isSeparated)
    }

    @Test
    fun `short-form Find My advert means owner is nearby`() {
        val status = OfflineFinding.decode(AppleContinuity.parse(hexToBytes("12 02 D4 00")).single())!!

        assertEquals(OfflineFinding.DeviceType.AIRTAG, status.deviceType)
        assertEquals(OfflineFinding.Battery.VERY_LOW, status.battery)
        assertFalse(status.isSeparated)
    }

    @Test
    fun `nearby registry only offers play sound for separated Find My accessories`() {
        val separated = hexToBytes("12 19 30" + " 00".repeat(24))
        val nearbyOwner = hexToBytes("12 02 30 00")
        val pods = hexToBytes("07 19 01 14 20 55 88 F9 51 00 04 20 50 03 CA D5 C9 AC 0F FA 84 78 94 5A 4D DF F5")
        val iphone = hexToBytes("12 02 00 00")

        val registry = NearbyDevices()
            .onAdvertisement("AA", separated, rssi = -60, nowMillis = 0)
            .onAdvertisement("BB", nearbyOwner, rssi = -70, nowMillis = 0)
            .onAdvertisement("CC", pods, rssi = -50, nowMillis = 0)
            .onAdvertisement("DD", iphone, rssi = -40, nowMillis = 0)

        assertEquals(listOf("CC", "AA", "BB"), registry.sorted().map { it.address })
        assertEquals(listOf(false, true, false), registry.sorted().map { it.canPlaySound })
        assertEquals("AirPods Pro 2", registry.devices.getValue("CC").title)
    }

    @Test
    fun `registry expires devices not seen recently`() {
        val pods = hexToBytes("07 19 01 14 20 55 88 F9 51 00 04 20 50 03 CA D5 C9 AC 0F FA 84 78 94 5A 4D DF F5")
        val registry = NearbyDevices().onAdvertisement("CC", pods, rssi = -50, nowMillis = 0)

        assertEquals(1, registry.expire(NearbyDevices.EXPIRE_AFTER_MILLIS).devices.size)
        assertEquals(0, registry.expire(NearbyDevices.EXPIRE_AFTER_MILLIS + 1).devices.size)
    }
}
