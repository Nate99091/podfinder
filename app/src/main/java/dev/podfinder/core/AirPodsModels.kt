package dev.podfinder.core

/** Proximity-pairing model IDs, as catalogued by CAPod (d4rken-org/capod, devices/airpods). */
object AirPodsModels {
    private val NAMES = mapOf(
        0x0220 to "AirPods (1st gen)",
        0x0F20 to "AirPods (2nd gen)",
        0x1320 to "AirPods (3rd gen)",
        0x1920 to "AirPods 4",
        0x1B20 to "AirPods 4 (ANC)",
        0x0E20 to "AirPods Pro",
        0x1420 to "AirPods Pro 2",
        0x2420 to "AirPods Pro 2 (USB-C)",
        0x2720 to "AirPods Pro 3",
        0x0A20 to "AirPods Max",
        0x1F20 to "AirPods Max (USB-C)",
        0x2D20 to "AirPods Max 2",
    )

    fun nameFor(modelId: Int): String = NAMES[modelId] ?: "Apple audio device 0x%04X".format(modelId)

    fun isKnownAirPods(modelId: Int): Boolean = modelId in NAMES
}
