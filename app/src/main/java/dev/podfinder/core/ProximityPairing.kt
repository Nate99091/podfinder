package dev.podfinder.core

/**
 * Decoder for the Apple proximity-pairing advertisement (Continuity type 0x07, 25 bytes) that
 * AirPods broadcast while out of the case or with the lid open.
 *
 * Layout (offsets into the message data, after type/length), as documented by OpenPods, CAPod and
 * furiousMAC/continuity:
 *  0      prefix, 0x01 for the plaintext status format
 *  1..2   device model, big-endian (e.g. 0x0E20 = AirPods Pro)
 *  3      status; high-nibble bit 1 clear means left/right fields are swapped
 *  4      battery nibbles (0..10 = 0..100 %, 15 = not reported)
 *  5      high nibble: charging flags (bit0/bit1 pods, bit2 case); low nibble: case battery
 *  6      lid-open counter
 *  7      colour
 *  9..24  encrypted payload (needs the pods' encryption key; not decoded here)
 */
object ProximityPairing {
    const val MESSAGE_LENGTH = 25
    private const val PLAINTEXT_PREFIX = 0x01
    private const val NIBBLE_NOT_REPORTED = 15
    private const val MAX_BATTERY_NIBBLE = 10

    data class Pod(val batteryPercent: Int?, val isCharging: Boolean, val isInEar: Boolean)

    data class Status(
        val modelId: Int,
        val modelName: String,
        val left: Pod,
        val right: Pod,
        val case: Pod,
        val color: Int,
    )

    fun decode(message: AppleContinuity.Message): Status? {
        if (message.type != AppleContinuity.TYPE_PROXIMITY_PAIRING) return null
        if (message.length != MESSAGE_LENGTH) return null
        if (message[0] != PLAINTEXT_PREFIX) return null

        val modelId = (message[1] shl 8) or message[2]
        val status = message[3]
        val battery = message[4]
        val chargeAndCase = message[5]

        val isFlipped = (status and 0x20) == 0
        val high = battery shr 4
        val low = battery and 0x0F
        val chargeFlags = chargeAndCase shr 4
        val inEarFlags = status and 0x0F

        val left = Pod(
            batteryPercent = nibbleToPercent(if (isFlipped) high else low),
            isCharging = chargeFlags and (if (isFlipped) 0b10 else 0b01) != 0,
            isInEar = inEarFlags and (if (isFlipped) 0b1000 else 0b0010) != 0,
        )
        val right = Pod(
            batteryPercent = nibbleToPercent(if (isFlipped) low else high),
            isCharging = chargeFlags and (if (isFlipped) 0b01 else 0b10) != 0,
            isInEar = inEarFlags and (if (isFlipped) 0b0010 else 0b1000) != 0,
        )
        val case = Pod(
            batteryPercent = nibbleToPercent(chargeAndCase and 0x0F),
            isCharging = chargeFlags and 0b100 != 0,
            isInEar = false,
        )
        return Status(modelId, AirPodsModels.nameFor(modelId), left, right, case, message[7])
    }

    private fun nibbleToPercent(nibble: Int): Int? = when {
        nibble == NIBBLE_NOT_REPORTED -> null
        nibble > MAX_BATTERY_NIBBLE -> null
        else -> nibble * 10
    }
}
