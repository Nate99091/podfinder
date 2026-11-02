package dev.podfinder.core

/**
 * Immutable registry of Apple audio / Find My advertisers currently in range, keyed by BLE address.
 * AirPods rotate their random address roughly every 15 minutes, so stale entries are expired.
 */
data class NearbyDevices(val devices: Map<String, Device> = emptyMap()) {

    sealed interface Kind {
        /** Proximity-pairing advert: buds out of the case or lid open, near-owner mode. */
        data class AirPodsNearby(val status: ProximityPairing.Status) : Kind

        /** Find My offline-finding advert. Only separated AirPods accept the play-sound command. */
        data class FindMy(val status: OfflineFinding.Status) : Kind
    }

    data class Device(val address: String, val kind: Kind, val meter: ProximityMeter) {
        val title: String
            get() = when (kind) {
                is Kind.AirPodsNearby -> kind.status.modelName
                is Kind.FindMy -> when (kind.status.deviceType) {
                    OfflineFinding.DeviceType.AIRPODS -> "AirPods (Find My)"
                    OfflineFinding.DeviceType.AIRTAG -> "AirTag"
                    OfflineFinding.DeviceType.FIND_MY_ACCESSORY -> "Find My accessory"
                    OfflineFinding.DeviceType.APPLE_DEVICE -> "Apple device (Find My)"
                }
            }

        val canPlaySound: Boolean
            get() = kind is Kind.FindMy && kind.status.isSeparated &&
                kind.status.deviceType != OfflineFinding.DeviceType.APPLE_DEVICE
    }

    fun onAdvertisement(
        address: String,
        appleManufacturerData: ByteArray,
        rssi: Int,
        nowMillis: Long,
    ): NearbyDevices {
        val kind = classify(appleManufacturerData) ?: return this
        val meter = (devices[address]?.meter ?: ProximityMeter()).update(rssi, nowMillis)
        return copy(devices = devices + (address to Device(address, kind, meter)))
    }

    fun expire(nowMillis: Long): NearbyDevices = copy(
        devices = devices.filterValues { device ->
            val seen = device.meter.lastSeenMillis ?: return@filterValues false
            nowMillis - seen <= EXPIRE_AFTER_MILLIS
        },
    )

    /** Strongest signal first, so the closest device sits on top. */
    fun sorted(): List<Device> = devices.values.sortedByDescending { it.meter.smoothedRssi ?: Double.NEGATIVE_INFINITY }

    companion object {
        const val EXPIRE_AFTER_MILLIS = 30_000L

        fun classify(appleManufacturerData: ByteArray): Kind? {
            for (message in AppleContinuity.parse(appleManufacturerData)) {
                ProximityPairing.decode(message)?.let { return Kind.AirPodsNearby(it) }
                OfflineFinding.decode(message)?.let { status ->
                    if (status.deviceType != OfflineFinding.DeviceType.APPLE_DEVICE) return Kind.FindMy(status)
                }
            }
            return null
        }
    }
}
