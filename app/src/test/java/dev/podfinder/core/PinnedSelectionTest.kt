package dev.podfinder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedSelectionTest {

    private val pods = hexToBytes("07 19 01 14 20 55 88 F9 51 00 04 20 50 03 CA D5 C9 AC 0F FA 84 78 94 5A 4D DF F5")
    private val airtag = hexToBytes("12 19 10" + " 00".repeat(24))

    private fun registry(vararg entries: Pair<String, ByteArray>): NearbyDevices {
        var n = NearbyDevices()
        entries.forEachIndexed { i, (addr, data) -> n = n.onAdvertisement(addr, data, -50 - i, 0) }
        return n
    }

    @Test
    fun `unpinned when no pin set`() {
        assertEquals(PinnedSelection.Result.Unpinned, PinnedSelection.resolve(registry("AA" to pods), null))
    }

    @Test
    fun `tracks the pinned address`() {
        val nearby = registry("AA" to pods, "BB" to airtag)
        val pin = PinnedSelection.pinFor(nearby.devices.getValue("AA"))

        val result = PinnedSelection.resolve(nearby, pin)

        assertTrue(result is PinnedSelection.Result.Tracking)
        assertEquals("AA", (result as PinnedSelection.Result.Tracking).device.address)
    }

    @Test
    fun `follows the device to a rotated address by unique title`() {
        val first = registry("AA" to pods)
        val pin = PinnedSelection.pinFor(first.devices.getValue("AA"))
        val rotated = registry("ZZ" to pods) // same AirPods, new random address

        val result = PinnedSelection.resolve(rotated, pin)

        assertEquals("ZZ", (result as PinnedSelection.Result.Tracking).device.address)
    }

    @Test
    fun `reports signal lost when nothing matches`() {
        val pin = PinnedSelection.Pin("AA", "AirPods Pro 2")

        assertEquals(PinnedSelection.Result.SignalLost, PinnedSelection.resolve(registry("BB" to airtag), pin))
    }

    @Test
    fun `reports signal lost rather than guessing between two same-title devices`() {
        val nearby = registry("XX" to pods, "YY" to pods)
        val pin = PinnedSelection.Pin("AA", "AirPods Pro 2")

        assertEquals(PinnedSelection.Result.SignalLost, PinnedSelection.resolve(nearby, pin))
    }
}
