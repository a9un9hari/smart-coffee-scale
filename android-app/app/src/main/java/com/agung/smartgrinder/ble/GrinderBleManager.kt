package com.agung.smartgrinder.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.agung.smartgrinder.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

private const val DEVICE_NAME = "SmartGrinder"
private const val OP_TIMEOUT_MS = 4000L

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
    private val prefs = AppPreferences(context)

    private var gatt: BluetoothGatt? = null
    private var statusChar: BluetoothGattCharacteristic? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var cupProfileChar: BluetoothGattCharacteristic? = null
    private var calibrationStatusChar: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _status = MutableStateFlow<GrinderStatus?>(null)
    val status: StateFlow<GrinderStatus?> = _status.asStateFlow()

    private val _calibrationStatus = MutableStateFlow<CalibrationStatus?>(null)
    val calibrationStatus: StateFlow<CalibrationStatus?> = _calibrationStatus.asStateFlow()

    private val opQueue = ArrayDeque<() -> Unit>()
    private var opInFlight = false
    private var currentOpToken = 0
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var pendingCupProfileResult: ((CupProfile) -> Unit)? = null

    // A single dropped GATT callback (known to happen on real devices/stacks)
    // must never permanently jam the queue - every command after it would
    // silently do nothing forever. If a callback doesn't arrive within this
    // window, give up on that op and move on.
    private fun enqueue(op: () -> Unit) = synchronized(opQueue) {
        opQueue.addLast(op)
        if (!opInFlight) startNext()
    }

    private fun startNext() {
        var next: (() -> Unit)? = null
        synchronized(opQueue) {
            next = opQueue.removeFirstOrNull()
            if (next == null) {
                opInFlight = false
            } else {
                opInFlight = true
                currentOpToken++
            }
        }
        val op = next ?: return
        val token = currentOpToken
        op()
        timeoutHandler.postDelayed({ onOpTimeout(token) }, OP_TIMEOUT_MS)
    }

    private fun onOpTimeout(token: Int) {
        val stillCurrent = synchronized(opQueue) { opInFlight && token == currentOpToken }
        if (stillCurrent) startNext() // give up waiting, move the queue along
    }

    private fun completeOp() {
        val wasCurrent = synchronized(opQueue) { opInFlight }
        if (!wasCurrent) return
        timeoutHandler.removeCallbacksAndMessages(null)
        startNext()
    }

    private fun resetQueue() = synchronized(opQueue) {
        opQueue.clear()
        opInFlight = false
        currentOpToken++ // invalidates any in-flight timeout from the old connection
        timeoutHandler.removeCallbacksAndMessages(null)
    }

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

    /**
     * Connects straight to the last device we successfully paired with, skipping
     * the active scan - used to reconnect automatically when the app opens.
     * autoConnect=true means Android keeps waiting/retrying in the background
     * until the grinder is actually in range and advertising, rather than
     * failing immediately. Returns false (nothing to do) if we've never
     * connected to a device before, so the caller can fall back to [connect].
     */
    @SuppressLint("MissingPermission")
    fun connectToSavedDevice(): Boolean {
        val a = adapter ?: return false
        val savedAddress = prefs.lastDeviceAddress ?: return false
        val device = try {
            a.getRemoteDevice(savedAddress)
        } catch (e: IllegalArgumentException) {
            return false
        }
        _connectionState.value = ConnectionState.CONNECTING
        gatt = device.connectGatt(context, true, gattCallback)
        return true
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
                    resetQueue()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(GrinderBleUuids.SERVICE)
            statusChar = service?.getCharacteristic(GrinderBleUuids.STATUS)
            commandChar = service?.getCharacteristic(GrinderBleUuids.COMMAND)
            cupProfileChar = service?.getCharacteristic(GrinderBleUuids.CUP_PROFILE_QUERY)
            calibrationStatusChar = service?.getCharacteristic(GrinderBleUuids.CALIBRATION_STATUS)

            // Both descriptor writes go through the same queue as everything
            // else - issuing them back-to-back without waiting for each
            // callback would silently drop the second one.
            statusChar?.let { enqueue { enableNotify(g, it) } }
            calibrationStatusChar?.let { enqueue { enableNotify(g, it) } }

            prefs.lastDeviceAddress = g.device.address
            _connectionState.value = ConnectionState.CONNECTED
        }

        @SuppressLint("MissingPermission")
        private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(GrinderBleUuids.CLIENT_CHARACTERISTIC_CONFIG)
            if (cccd == null) {
                completeOp() // nothing to write, still need to release the queue
                return
            }
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleNotification(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in Java, kept for API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                @Suppress("DEPRECATION")
                characteristic.value?.let { handleNotification(characteristic.uuid, it) }
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

    private fun handleNotification(uuid: java.util.UUID, value: ByteArray) {
        when (uuid) {
            GrinderBleUuids.STATUS -> decodeStatus(value)?.let { _status.value = it }
            GrinderBleUuids.CALIBRATION_STATUS -> decodeCalibrationStatus(value)?.let { _calibrationStatus.value = it }
        }
    }

    fun tare() = sendCommand(BleCommand.tare())
    fun calClear() = sendCommand(BleCommand.calClear())
    fun calAddPoint(knownWeightG: Float) = sendCommand(BleCommand.calAddPoint(knownWeightG))
    fun calSave() = sendCommand(BleCommand.calSave())

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
        resetQueue()
    }
}
