package dev.podfinder.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.podfinder.core.SoundProtocol
import dev.podfinder.core.SoundSession
import dev.podfinder.core.SoundSession.Action
import dev.podfinder.core.SoundSession.Event
import dev.podfinder.core.toHex

/**
 * Executes one [SoundSession] against a real GATT connection. All state changes run on the main
 * looper, so GATT binder callbacks are marshalled there before reaching the reducer.
 * Callers must hold BLUETOOTH_CONNECT (MainActivity checks before constructing a session).
 */
@SuppressLint("MissingPermission")
class GattSoundPlayer(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val address: String,
    private val onResult: (SoundSession.Result) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var state: SoundSession.State = SoundSession.State.Idle
    private var gatt: BluetoothGatt? = null
    private val timeout = Runnable { dispatch(Event.Timeout) }
    private val playTimer = Runnable { dispatch(Event.PlayTimerElapsed) }

    fun start() {
        handler.postDelayed(timeout, SESSION_TIMEOUT_MILLIS)
        dispatch(Event.Start)
    }

    private fun dispatch(event: Event) {
        val transition = SoundSession.reduce(state, event)
        Log.d(TAG, "$event: $state -> ${transition.state}")
        state = transition.state
        transition.actions.forEach(::execute)
    }

    private fun post(event: Event) {
        handler.post { dispatch(event) }
    }

    private fun execute(action: Action) {
        when (action) {
            Action.Connect -> connect()
            Action.DiscoverServices -> if (gatt?.discoverServices() != true) post(Event.ServicesDiscovered(false, emptyMap()))
            is Action.EnableNotifications -> enableNotifications(action.protocol)
            is Action.Write -> write(action.protocol, action.value)
            is Action.SchedulePlayTimer -> handler.postDelayed(playTimer, action.delayMillis)
            Action.Disconnect -> gatt?.disconnect()
            is Action.Report -> finish(action.result)
        }
    }

    private fun connect() {
        val device: BluetoothDevice = try {
            adapter.getRemoteLeDevice(address, BluetoothDevice.ADDRESS_TYPE_RANDOM)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid address $address", e)
            post(Event.Disconnected(-1))
            return
        }
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) post(Event.Disconnected(-1))
    }

    private fun enableNotifications(protocol: SoundProtocol) {
        val g = gatt ?: return post(Event.DescriptorWritten(false))
        val characteristic = characteristicFor(g, protocol) ?: return post(Event.DescriptorWritten(false))
        g.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(SoundProtocol.CCCD_UUID)
        if (cccd == null) {
            // No CCCD: nothing to enable, continue straight to the start write.
            Log.w(TAG, "No CCCD on ${characteristic.uuid}; writing directly")
            return post(Event.DescriptorWritten(true))
        }
        val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        val status = g.writeDescriptor(cccd, value)
        if (status != BluetoothGatt.GATT_SUCCESS) post(Event.DescriptorWritten(false))
    }

    private fun write(protocol: SoundProtocol, value: ByteArray) {
        val g = gatt ?: return post(Event.CharacteristicWritten(false))
        val characteristic = characteristicFor(g, protocol) ?: return post(Event.CharacteristicWritten(false))
        Log.i(TAG, "Writing ${value.toHex()} to ${protocol.name}")
        val status = g.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        if (status != BluetoothGatt.GATT_SUCCESS) post(Event.CharacteristicWritten(false))
    }

    private fun characteristicFor(g: BluetoothGatt, protocol: SoundProtocol): BluetoothGattCharacteristic? =
        g.getService(protocol.serviceUuid)?.getCharacteristic(protocol.characteristicUuid)

    private fun finish(result: SoundSession.Result) {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(playTimer)
        // Give a requested disconnect a moment to go out before releasing the client.
        handler.postDelayed({ gatt?.close(); gatt = null }, CLOSE_DELAY_MILLIS)
        onResult(result)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when {
                status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED -> post(Event.Connected)
                newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS ->
                    post(Event.Disconnected(status))
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val services = g.services.associate { service ->
                service.uuid to service.characteristics.map { it.uuid }.toSet()
            }
            Log.d(TAG, "Services: ${services.keys}")
            post(Event.ServicesDiscovered(status == BluetoothGatt.GATT_SUCCESS, services))
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            post(Event.DescriptorWritten(status == BluetoothGatt.GATT_SUCCESS))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            post(Event.CharacteristicWritten(status == BluetoothGatt.GATT_SUCCESS))
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            Log.i(TAG, "Indication from ${characteristic.uuid}: ${value.toHex()}")
        }
    }

    private companion object {
        const val TAG = "PodFinder.Sound"
        const val SESSION_TIMEOUT_MILLIS = 20_000L
        const val CLOSE_DELAY_MILLIS = 500L
    }
}
