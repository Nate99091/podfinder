package dev.podfinder.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import dev.podfinder.core.AppleContinuity

/** Low-latency BLE scan for Apple proximity-pairing (0x07) and Find My (0x12) adverts. */
class BleScanner(
    private val adapter: BluetoothAdapter,
    private val onAdvert: (address: String, appleData: ByteArray, rssi: Int) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var isScanning = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = deliver(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::deliver)

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode")
            isScanning = false
            onError("Bluetooth scan failed (code $errorCode)")
        }
    }

    private fun deliver(result: ScanResult) {
        val data = result.scanRecord?.getManufacturerSpecificData(AppleContinuity.APPLE_COMPANY_ID) ?: return
        onAdvert(result.device.address, data, result.rssi)
    }

    // Caller (MainActivity) only invokes after BLUETOOTH_SCAN is granted.
    @SuppressLint("MissingPermission")
    fun start() {
        if (isScanning) return
        val scanner = adapter.bluetoothLeScanner ?: return onError("Bluetooth LE scanner unavailable")
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        scanner.startScan(FILTERS, settings, callback)
        isScanning = true
    }

    // Caller (MainActivity) only invokes after BLUETOOTH_SCAN is granted.
    @SuppressLint("MissingPermission")
    fun stop() {
        if (!isScanning) return
        adapter.bluetoothLeScanner?.stopScan(callback)
        isScanning = false
    }

    private companion object {
        const val TAG = "PodFinder.Scan"

        val FILTERS: List<ScanFilter> = listOf(
            appleFilter(byteArrayOf(AppleContinuity.TYPE_PROXIMITY_PAIRING.toByte(), 0x19)),
            appleFilter(byteArrayOf(AppleContinuity.TYPE_OFFLINE_FINDING.toByte())),
        )

        fun appleFilter(prefix: ByteArray): ScanFilter = ScanFilter.Builder()
            .setManufacturerData(
                AppleContinuity.APPLE_COMPANY_ID,
                prefix,
                ByteArray(prefix.size) { 0xFF.toByte() },
            )
            .build()
    }
}
