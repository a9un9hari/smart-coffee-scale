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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

private const val DEVICE_NAME = "SmartGrinder"
private const val OP_TIMEOUT_MS = 4000L

// Matches the firmware's NimBLEDevice::setMTU(247) - default MTU (23 bytes)
// isn't enough for an OTA WiFi SSID/password/URL write. If the negotiation
// doesn't complete in time for any reason, MTU_FALLBACK_MS still starts
// service discovery so the rest of the app (which fits in 20 bytes either
// way) isn't blocked waiting on it.
private const val REQUESTED_MTU = 247
private const val MTU_FALLBACK_MS = 3000L

// BLE OTA windowed sender (see OtaSend). 16 chunks x 244 bytes ~= 3.9KB
// in flight at most.
private const val OTA_WINDOW_CHUNKS = 16
private const val OTA_STALL_TIMEOUT_MS = 5000L
private const val OTA_CHUNK_RETRIES = 100
private const val OTA_CHUNK_RETRY_DELAY_MS = 5L

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
    private var grindLogChar: BluetoothGattCharacteristic? = null
    private var bleOtaCtrlChar: BluetoothGattCharacteristic? = null
    private var bleOtaDataChar: BluetoothGattCharacteristic? = null
    private var negotiatedMtu = 23
    private var servicesDiscoveryStarted = false

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _status = MutableStateFlow<GrinderStatus?>(null)
    val status: StateFlow<GrinderStatus?> = _status.asStateFlow()

    private val _calibrationStatus = MutableStateFlow<CalibrationStatus?>(null)
    val calibrationStatus: StateFlow<CalibrationStatus?> = _calibrationStatus.asStateFlow()


    // Raw GrindLog notifications (CSV rows / BleGrindLog.END) - a sync can
    // burst a few hundred in a row, hence the generous buffer.
    private val _grindLogLines = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val grindLogLines: SharedFlow<String> = _grindLogLines

    private val _bleOtaStatus = MutableStateFlow<BleOtaStatus?>(null)
    val bleOtaStatus: StateFlow<BleOtaStatus?> = _bleOtaStatus.asStateFlow()

    // App-side count of image bytes whose write has been acknowledged -
    // smoother than the firmware's throttled notifies, and still moves if a
    // notify is dropped.
    private val _bleOtaSentBytes = MutableStateFlow(0L)
    val bleOtaSentBytes: StateFlow<Long> = _bleOtaSentBytes.asStateFlow()

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
                BluetoothProfile.STATE_CONNECTED -> {
                    servicesDiscoveryStarted = false
                    g.requestMtu(REQUESTED_MTU)
                    timeoutHandler.postDelayed({ startServiceDiscoveryOnce(g) }, MTU_FALLBACK_MS)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    negotiatedMtu = 23
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _status.value = null
                    gatt = null
                    resetQueue()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) negotiatedMtu = mtu
            startServiceDiscoveryOnce(g)
        }

        @SuppressLint("MissingPermission")
        private fun startServiceDiscoveryOnce(g: BluetoothGatt) {
            if (servicesDiscoveryStarted) return
            servicesDiscoveryStarted = true
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(GrinderBleUuids.SERVICE)
            statusChar = service?.getCharacteristic(GrinderBleUuids.STATUS)
            commandChar = service?.getCharacteristic(GrinderBleUuids.COMMAND)
            cupProfileChar = service?.getCharacteristic(GrinderBleUuids.CUP_PROFILE_QUERY)
            calibrationStatusChar = service?.getCharacteristic(GrinderBleUuids.CALIBRATION_STATUS)
            grindLogChar = service?.getCharacteristic(GrinderBleUuids.GRIND_LOG) // null on firmware older than the log sync
            bleOtaCtrlChar = service?.getCharacteristic(GrinderBleUuids.BLE_OTA_CTRL) // null on firmware older than BLE OTA
            bleOtaDataChar = service?.getCharacteristic(GrinderBleUuids.BLE_OTA_DATA)

            // All descriptor writes go through the same queue as everything
            // else - issuing them back-to-back without waiting for each
            // callback would silently drop the later ones.
            statusChar?.let { enqueue { enableNotify(g, it) } }
            calibrationStatusChar?.let { enqueue { enableNotify(g, it) } }
            grindLogChar?.let { enqueue { enableNotify(g, it) } }
            bleOtaCtrlChar?.let { enqueue { enableNotify(g, it) } }

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
            GrinderBleUuids.GRIND_LOG -> _grindLogLines.tryEmit(String(value, Charsets.US_ASCII))
            GrinderBleUuids.BLE_OTA_CTRL -> decodeBleOtaStatus(value)?.let {
                _bleOtaStatus.value = it
                onOtaStatus(it)
            }
        }
    }

    fun tare() = sendCommand(BleCommand.tare())
    fun calClear() = sendCommand(BleCommand.calClear())
    fun calAddPoint(knownWeightG: Float) = sendCommand(BleCommand.calAddPoint(knownWeightG))
    fun calSave() = sendCommand(BleCommand.calSave())

    val supportsBleOta: Boolean get() = bleOtaCtrlChar != null && bleOtaDataChar != null

    // ---- BLE OTA windowed sender ----
    // Chunks go out as write-without-response (no per-chunk round trip -
    // the old one-acknowledged-write-per-chunk sender managed ~7 KB/s), in
    // windows of OTA_WINDOW_CHUNKS; the next window is only queued once a
    // status notify shows the firmware has received everything so far. So
    // at most one window is ever in flight, and a lost chunk shows up as a
    // stall within one window rather than an MD5 failure at the very end.
    private class OtaSend(
        val g: BluetoothGatt,
        val ctrl: BluetoothGattCharacteristic,
        val data: BluetoothGattCharacteristic,
        val image: ByteArray,
        val chunkSize: Int,
        val windowBytes: Int
    ) {
        var nextOffset = 0
        var awaitingReceived = 0L // the next window is sent once the firmware reports this many bytes
        var endQueued = false
    }

    private var otaSend: OtaSend? = null
    private val otaHandler = Handler(Looper.getMainLooper())
    private val otaStallCheck = Runnable { onOtaStalled() }

    /**
     * Streams a firmware image: BEGIN, windowed chunks (see OtaSend), then
     * END once the firmware confirms the last byte. Requests a high-priority
     * (short interval) connection for the duration.
     */
    @SuppressLint("MissingPermission")
    fun startBleOta(image: ByteArray, md5Hex: String): Boolean {
        val g = gatt ?: return false
        val ctrl = bleOtaCtrlChar ?: return false
        val data = bleOtaDataChar ?: return false
        val chunkSize = (negotiatedMtu - 3).coerceIn(20, 512)
        val send = OtaSend(g, ctrl, data, image, chunkSize, chunkSize * OTA_WINDOW_CHUNKS)

        _bleOtaSentBytes.value = 0L
        _bleOtaStatus.value = null
        otaSend = send
        g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        // The first window goes out once the firmware's RECEIVING notify
        // (received = 0) confirms BEGIN was accepted - see onOtaStatus().
        enqueue { writeChar(g, ctrl, BleOtaProtocol.begin(image.size, md5Hex, send.windowBytes)) }
        armOtaStallCheck()
        return true
    }

    private fun onOtaStatus(s: BleOtaStatus) {
        val send = otaSend ?: return
        if (s.state == BleOtaState.ERROR || s.state == BleOtaState.SUCCESS) {
            otaSend = null
            otaHandler.removeCallbacks(otaStallCheck)
            return
        }
        if (s.state != BleOtaState.RECEIVING) return
        synchronized(send) {
            if (s.received < send.awaitingReceived) return // a stale/duplicate notify from an earlier window
            if (send.nextOffset < send.image.size) {
                sendOtaWindow(send)
            } else if (!send.endQueued) {
                send.endQueued = true
                // Verify + reboot legitimately take a moment and end with the
                // link dropping - no longer a "stall"; a drop from here on is
                // handled as a likely success (GrinderViewModel).
                otaHandler.removeCallbacks(otaStallCheck)
                enqueue {
                    writeChar(send.g, send.ctrl, BleOtaProtocol.end())
                    send.g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
                }
            }
        }
    }

    private fun sendOtaWindow(send: OtaSend) {
        val windowEnd = minOf(send.nextOffset + send.windowBytes, send.image.size)
        var offset = send.nextOffset
        while (offset < windowEnd) {
            val end = minOf(offset + send.chunkSize, windowEnd)
            val chunk = send.image.copyOfRange(offset, end)
            val sentAfter = end.toLong()
            enqueue {
                _bleOtaSentBytes.value = sentAfter
                writeOtaChunk(send.g, send.data, chunk, attempt = 0)
            }
            offset = end
        }
        send.nextOffset = windowEnd
        send.awaitingReceived = windowEnd.toLong()
        armOtaStallCheck()
    }

    // writeCharacteristic refuses (returns false / BUSY) while the stack's
    // outgoing buffer is full - retry briefly instead of letting the queue's
    // op timeout skip the chunk, which would corrupt the image.
    @SuppressLint("MissingPermission")
    private fun writeOtaChunk(g: BluetoothGatt, ch: BluetoothGattCharacteristic, chunk: ByteArray, attempt: Int) {
        if (writeChar(g, ch, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)) return
        if (attempt < OTA_CHUNK_RETRIES) {
            otaHandler.postDelayed({ writeOtaChunk(g, ch, chunk, attempt + 1) }, OTA_CHUNK_RETRY_DELAY_MS)
        }
    }

    private fun armOtaStallCheck() {
        otaHandler.removeCallbacks(otaStallCheck)
        otaHandler.postDelayed(otaStallCheck, OTA_STALL_TIMEOUT_MS)
    }

    private fun onOtaStalled() {
        if (otaSend == null) return
        val last = _bleOtaStatus.value
        cancelBleOta()
        _bleOtaStatus.value = BleOtaStatus(BleOtaState.ERROR, BLE_OTA_ERR_APP_STALLED, last?.received ?: 0L, last?.total ?: 0L)
    }

    /** Drops every still-queued chunk and tells the firmware to abandon the update. */
    @SuppressLint("MissingPermission")
    fun cancelBleOta() {
        otaSend = null
        otaHandler.removeCallbacks(otaStallCheck)
        val g = gatt ?: return
        val ctrl = bleOtaCtrlChar ?: return
        resetQueue()
        // A chunk write may still be in flight in the GATT stack - a second
        // write issued before its callback would be silently rejected, so
        // give it a moment. (If ABORT is lost anyway, the firmware abandons
        // the transfer on its own data timeout.)
        Handler(Looper.getMainLooper()).postDelayed({
            enqueue { writeChar(g, ctrl, BleOtaProtocol.abort()) }
            g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
        }, 300)
    }

    /** Asks the firmware for every stored grind session newer than this cursor - see BleGrindLog. Returns false if unsupported/not connected. */
    @SuppressLint("MissingPermission")
    fun requestGrindLog(afterBoot: Long, afterUptimeS: Long): Boolean {
        val g = gatt ?: return false
        val ch = grindLogChar ?: return false
        enqueue { writeChar(g, ch, BleGrindLog.request(afterBoot, afterUptimeS)) }
        return true
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

    /** Returns whether the stack accepted the write (it refuses while busy). */
    @SuppressLint("MissingPermission")
    private fun writeChar(
        g: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        bytes: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(ch, bytes, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            ch.value = bytes
            @Suppress("DEPRECATION")
            ch.writeType = writeType
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
