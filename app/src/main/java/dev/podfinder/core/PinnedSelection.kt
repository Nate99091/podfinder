package dev.podfinder.core

/**
 * Resolves the single device the user pinned to track.
 *
 * AirPods rotate their BLE address roughly every 15 minutes, so a pin can't rely on the address
 * alone. We pin by address first; if that address has rotated away, we fall back to the one
 * remaining device with the same title. If that is ambiguous (two devices share a title) we report
 * the pin as lost rather than track the wrong one.
 */
object PinnedSelection {

    data class Pin(val address: String, val title: String)

    sealed interface Result {
        data class Tracking(val device: NearbyDevices.Device) : Result
        /** Pinned, but nothing matching is currently in range. */
        data object SignalLost : Result
        data object Unpinned : Result
    }

    fun resolve(nearby: NearbyDevices, pin: Pin?): Result {
        if (pin == null) return Result.Unpinned
        nearby.devices[pin.address]?.let { return Result.Tracking(it) }
        val sameTitle = nearby.sorted().filter { it.title == pin.title }
        return when (val only = sameTitle.singleOrNull()) {
            null -> Result.SignalLost
            else -> Result.Tracking(only)
        }
    }

    fun pinFor(device: NearbyDevices.Device): Pin = Pin(device.address, device.title)
}
