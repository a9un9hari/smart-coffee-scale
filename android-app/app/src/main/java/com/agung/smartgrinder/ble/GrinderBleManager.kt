package com.agung.smartgrinder.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

private const val DEVICE_NAME = "SmartGrinder"

/**
 * BLE central for the grinder. Talks to the GATT server defined in
 * include/ble.h / src/ble.cpp on the firmware side - see BleProtocol.kt for
 * the wire format both sides agree on.
 *
 * Android's BluetoothGatt only allows one pending operation (write/read/
 * descriptor-write) at a time per connection - issuing a second before the
 * first's callback fires silently fails. [enqueue] serializes everything
 * through a simple queue drained by the write/read callbacks.
 *
 * Every public method assumes the caller has already checked/requested the
 * BLUETOOTH_SCAN/BLUETOOTH_CONNECT (or, pre-API31, ACCESS_FINE_LOCATION)
 * runtime permissions - this class does not request permissions itself.
 */
class GrinderBleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null
    private var statusChar: BluetoothGattCharacteristic? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var cupProfileChar: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _status = MutableStateFlow<GrinderStatus?>(null)
    val status: StateFlow<GrinderStatus?> = _status.asStateFlow()

    private val opQueue = ArrayDeque<() -> Unit>()
    private var opInFlight = false
    private var pendingCupProfileResult: ((CupProfile) -> Unit)? = null

    private fun enqueue(op: () -> Unit) = synchronized(opQueue) {
        opQueue.addLast(op)
        if (!opInFlight) dequeueNext()
    }

    private fun dequeueNext() = synchronized(opQueue) {
        val next = opQueue.removeFirstOrNull()
        if (next == null) {
            opInFlight = false
        } else {
            opInFlight = true
            next()
        }
    }

    private fun completeOp() = dequeueNext()

    @SuppressLint("MissingPermission")
    fun connect() {
        val bleScanner = adapter?.bluetoothLeScanner
        if (bleScanner == null) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }
        _connectionState.value = ConnectionState.SCANNING

        val filters = listOf(ScanFilter.Builder().setDeviceName(DEVICE_NAME).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        bleScanner.startScan(filters, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            adapter?.bluetoothLeScanner?.stopScan(this)
            _connectionState.value = ConnectionState.CONNECTING
            gatt = result.device.connectGatt(context, false, gattCallback)
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _status.value = null
                    gatt = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(GrinderBleUuids.SERVICE)
            statusChar = service?.getCharacteristic(GrinderBleUuids.STATUS)
            commandChar = service?.getCharacteristic(GrinderBleUuids.COMMAND)
            cupProfileChar = service?.getCharacteristic(GrinderBleUuids.CUP_PROFILE_QUERY)

            statusChar?.let { sc ->
                g.setCharacteristicNotification(sc, true)
                val cccd = sc.getDescriptor(GrinderBleUuids.CLIENT_CHARACTERISTIC_CONFIG)
                if (cccd != null) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(cccd)
                    }
                }
            }
            _connectionState.value = ConnectionState.CONNECTED
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == GrinderBleUuids.STATUS) {
                decodeStatus(value)?.let { _status.value = it }
            }
        }

        @Deprecated("Deprecated in Java, kept for API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33 && characteristic.uuid == GrinderBleUuids.STATUS) {
                @Suppress("DEPRECATION")
                characteristic.value?.let { decodeStatus(it)?.let { s -> _status.value = s } }
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            completeOp()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            completeOp()
        }

        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            handleCupProfileRead(characteristic.uuid, value)
            completeOp()
        }

        @Deprecated("Deprecated in Java, kept for API < 33")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) {
                @Suppress("DEPRECATION")
                characteristic.value?.let { handleCupProfileRead(characteristic.uuid, it) }
            }
            completeOp()
        }
    }

    private fun handleCupProfileRead(uuid: java.util.UUID, value: ByteArray) {
        if (uuid == GrinderBleUuids.CUP_PROFILE_QUERY) {
            decodeCupProfile(value)?.let { pendingCupProfileResult?.invoke(it) }
            pendingCupProfileResult = null
        }
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(bytes: ByteArray) {
        val g = gatt ?: return
        val ch = commandChar ?: return
        enqueue { writeChar(g, ch, bytes) }
    }

    /** Writes the requested id, then reads that profile's stored data back. */
    @SuppressLint("MissingPermission")
    fun queryCupProfile(id: Int, onResult: (CupProfile) -> Unit) {
        val g = gatt ?: return
        val ch = cupProfileChar ?: return
        enqueue { writeChar(g, ch, byteArrayOf(id.toByte())) }
        enqueue {
            pendingCupProfileResult = onResult
            g.readCharacteristic(ch)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeChar(g: BluetoothGatt, ch: BluetoothGattCharacteristic, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            ch.value = bytes
            @Suppress("DEPRECATION")
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
