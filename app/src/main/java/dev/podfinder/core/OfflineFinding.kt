package dev.podfinder.core

/**
 * Decoder for the Find My "offline finding" advertisement (Continuity type 0x12).
 *
 * Status byte (data[0]), per seemoo-lab/AirGuard (DeviceManager.calculateDeviceType and
 * AppleFindMy.getConnectionState/getBatteryState):
 *  bits 4-5  device type: 0 Apple device, 1 AirTag, 2 Find My accessory, 3 AirPods
 *  bits 6-7  battery: 0 full, 1 medium, 2 low, 3 very low
 *
 * The 25-byte long form is broadcast when the accessory is separated from its owner; the short
 * form is broadcast while the owner's device is nearby. Only the separated form accepts the
 * non-owner "play sound" command.
 */
object OfflineFinding {
    const val SEPARATED_MESSAGE_LENGTH = 25

    enum class DeviceType { APPLE_DEVICE, AIRTAG, FIND_MY_ACCESSORY, AIRPODS }
    enum class Battery { FULL, MEDIUM, LOW, VERY_LOW }

    data class Status(val deviceType: DeviceType, val battery: Battery, val isSeparated: Boolean)

    fun decode(message: AppleContinuity.Message): Status? {
        if (message.type != AppleContinuity.TYPE_OFFLINE_FINDING) return null
        if (message.length < 1) return null
        val status = message[0]
        return Status(
            deviceType = DeviceType.entries[(status shr 4) and 0x03],
            battery = Battery.entries[(status shr 6) and 0x03],
            isSeparated = message.length == SEPARATED_MESSAGE_LENGTH,
        )
    }
}
