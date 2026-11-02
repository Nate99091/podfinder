package dev.podfinder.core

import java.util.UUID

/**
 * GATT "play sound" protocols that Apple Find My accessories expose to non-owner devices.
 * UUIDs and opcodes come from seemoo-lab/AirGuard (Apache-2.0), AppleFindMy.kt @d515c53, and the
 * IETF DULT accessory protocol draft.
 */
enum class SoundProtocol(
    val serviceUuid: UUID,
    val characteristicUuid: UUID,
    val startCommand: ByteArray,
    val stopCommand: ByteArray?,
    val usesNotifications: Boolean,
) {
    /** Apple Find My network service 0xFD44. */
    FIND_MY(
        serviceUuid = UUID.fromString("0000fd44-0000-1000-8000-00805f9b34fb"),
        characteristicUuid = UUID.fromString("4F860003-943B-49EF-BED4-2F730304427A"),
        startCommand = byteArrayOf(0x01, 0x00, 0x03),
        stopCommand = byteArrayOf(0x01, 0x01, 0x03),
        usesNotifications = true,
    ),

    /** IETF DULT non-owner sound: Sound_Start 0x0300 / Sound_Stop 0x0301, little-endian. */
    DULT(
        serviceUuid = UUID.fromString("15190001-12F4-C226-88ED-2AC5579F2A85"),
        characteristicUuid = UUID.fromString("8E0C0001-1D68-FB92-BF61-48377421680E"),
        startCommand = byteArrayOf(0x00, 0x03),
        stopCommand = byteArrayOf(0x01, 0x03),
        usesNotifications = true,
    ),

    /** Legacy AirTag sound service: single 0xAF trigger; the tag ends the link when done. */
    AIRTAG(
        serviceUuid = UUID.fromString("7DFC9000-7D1C-4951-86AA-8D9728F8D66C"),
        characteristicUuid = UUID.fromString("7DFC9001-7D1C-4951-86AA-8D9728F8D66C"),
        startCommand = byteArrayOf(0xAF.toByte()),
        stopCommand = null,
        usesNotifications = false,
    );

    companion object {
        /** AirGuard's AirPods priority: FINDMY, DULT, AIRTAG. */
        val AIRPODS_PRIORITY: List<SoundProtocol> = listOf(FIND_MY, DULT, AIRTAG)

        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Picks the first protocol whose service and characteristic were both discovered.
         * [discovered] maps service UUID to the characteristic UUIDs it contains.
         */
        fun select(
            discovered: Map<UUID, Set<UUID>>,
            priority: List<SoundProtocol> = AIRPODS_PRIORITY,
        ): SoundProtocol? = priority.firstOrNull { protocol ->
            discovered[protocol.serviceUuid]?.contains(protocol.characteristicUuid) == true
        }
    }
}
