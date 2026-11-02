package dev.podfinder.core

/**
 * Apple "Continuity" manufacturer-data framing.
 *
 * Android hands us the manufacturer-specific payload for company ID 0x004C with the company ID
 * already stripped. The payload is a sequence of TLV messages: `type (1) | length (1) | data`.
 */
object AppleContinuity {
    const val APPLE_COMPANY_ID = 0x004C
    const val TYPE_PROXIMITY_PAIRING = 0x07
    const val TYPE_OFFLINE_FINDING = 0x12

    class Message(val type: Int, data: ByteArray) {
        private val bytes = data.copyOf()

        val data: ByteArray get() = bytes.copyOf()
        val length: Int get() = bytes.size

        operator fun get(index: Int): Int = bytes[index].toInt() and 0xFF

        override fun equals(other: Any?): Boolean =
            other is Message && other.type == type && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * type + bytes.contentHashCode()

        override fun toString(): String = "Message(type=0x%02X, data=%s)".format(type, bytes.toHex())
    }

    /** Parses every complete TLV message; a truncated trailing message is dropped. */
    fun parse(payload: ByteArray): List<Message> {
        val messages = mutableListOf<Message>()
        var offset = 0
        while (offset + 2 <= payload.size) {
            val type = payload[offset].toInt() and 0xFF
            val length = payload[offset + 1].toInt() and 0xFF
            val start = offset + 2
            val end = start + length
            if (end > payload.size) break
            messages += Message(type, payload.copyOfRange(start, end))
            offset = end
        }
        return messages
    }
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

fun hexToBytes(hex: String): ByteArray {
    val clean = hex.filterNot { it.isWhitespace() }
    require(clean.length % 2 == 0) { "Hex string must have an even number of digits" }
    return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
