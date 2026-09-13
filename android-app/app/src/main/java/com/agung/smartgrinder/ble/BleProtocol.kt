package com.agung.smartgrinder.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Mirrors include/ble.h / src/ble.cpp in the firmware exactly - UUIDs,
 * opcodes, and wire struct layouts. Keep these two in sync by hand; there's
 * no shared schema between the Kotlin and C++ sides.
 */
object GrinderBleUuids {
    val SERVICE: UUID = UUID.fromString("c4a10000-1000-4a4a-8a1a-2f5e9b6d0000")
    val STATUS: UUID = UUID.fromString("c4a10000-1000-4a4a-8a1a-2f5e9b6d0001")
    val COMMAND: UUID = UUID.fromString("c4a10000-1000-4a4a-8a1a-2f5e9b6d0002")
    val CUP_PROFILE_QUERY: UUID = UUID.fromString("c4a10000-1000-4a4a-8a1a-2f5e9b6d0003")
    val CALIBRATION_STATUS: UUID = UUID.fromString("c4a10000-1000-4a4a-8a1a-2f5e9b6d0004")
    val OTA_CONFIG: UUID = UUID.fromString("c4a10000-1000-4a4a-8a1a-2f5e9b6d0005")
    val OTA_STATUS: UUID = UUID.fromString("c4a10000-1000-4a4a-8a1a-2f5e9b6d0006")

    // Standard Client Characteristic Configuration Descriptor - used to enable notifications.
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

const val CUP_PROFILE_COUNT = 4

enum class GrinderMode(val wireValue: Int) {
    GRINDER(0), ESPRESSO(1);

    companion object {
        fun fromWire(v: Int) = entries.firstOrNull { it.wireValue == v } ?: GRINDER
    }
}

enum class GrinderState(val wireValue: Int, val label: String) {
    IDLE(0, "Idle"),
    GRINDING(1, "Grinding"),
    ESPRESSO_IDLE(2, "Espresso Ready"),
    PULL_SHOT(3, "Pull Shot"),
    PULLING(4, "Pulling"),
    SHOT_COMPLETE(5, "Shot Complete"),
    ERROR(6, "Error");

    companion object {
        fun fromWire(v: Int) = entries.firstOrNull { it.wireValue == v } ?: ERROR
    }
}

data class GrinderStatus(
    val weightG: Float,
    val targetWeightG: Float,
    val mode: GrinderMode,
    val state: GrinderState,
    val errorCode: Int,
    val activeCupProfileId: Int
)

data class CupProfile(
    val cupWeightG: Float,
    val toleranceG: Float,
    val name: String
) {
    val isConfigured: Boolean get() = cupWeightG >= 0f
}

/** Parses a 12-byte Status characteristic notification/read. Null if malformed. */
fun decodeStatus(bytes: ByteArray): GrinderStatus? {
    if (bytes.size < 12) return null
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val weight = buf.float
    val target = buf.float
    val mode = buf.get().toInt() and 0xFF
    val state = buf.get().toInt() and 0xFF
    val error = buf.get().toInt() and 0xFF
    val activeCup = buf.get().toInt() and 0xFF
    return GrinderStatus(weight, target, GrinderMode.fromWire(mode), GrinderState.fromWire(state), error, activeCup)
}

/** Parses a 20-byte CupProfileQuery read response. Null if malformed. */
fun decodeCupProfile(bytes: ByteArray): CupProfile? {
    if (bytes.size < 20) return null
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val cupWeight = buf.float
    val tolerance = buf.float
    val nameBytes = bytes.copyOfRange(8, 20)
    val nul = nameBytes.indexOf(0)
    val name = String(nameBytes, 0, if (nul >= 0) nul else nameBytes.size, Charsets.UTF_8)
    return CupProfile(cupWeight, tolerance, name)
}

data class CalibrationStatus(
    val pointCount: Int,
    val lastSaveOk: Boolean,
    val lastPointRaw: Float,
    val lastPointWeightG: Float
)

/** Parses a 10-byte CalibrationStatus characteristic notification/read. Null if malformed. */
fun decodeCalibrationStatus(bytes: ByteArray): CalibrationStatus? {
    if (bytes.size < 10) return null
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val pointCount = buf.get().toInt() and 0xFF
    val lastSaveOk = (buf.get().toInt() and 0xFF) != 0
    val lastRaw = buf.float
    val lastWeight = buf.float
    return CalibrationStatus(pointCount, lastSaveOk, lastRaw, lastWeight)
}

enum class OtaState(val wireValue: Int, val label: String) {
    IDLE(0, "Idle"),
    CONNECTING_WIFI(1, "Connecting to WiFi..."),
    UPDATING(2, "Updating firmware..."),
    SUCCESS(3, "Update successful"),
    ERROR_WIFI(4, "Couldn't connect to WiFi"),
    ERROR_UPDATE(5, "Update failed"),
    ERROR_NO_CONFIG(6, "Missing WiFi/URL config");

    companion object {
        fun fromWire(v: Int) = entries.firstOrNull { it.wireValue == v } ?: ERROR_UPDATE
    }
}

data class OtaStatus(val state: OtaState, val progressPercent: Int, val lastErrorCode: Int)

/** Parses a 3-byte OtaStatus characteristic notification/read. Null if malformed. */
fun decodeOtaStatus(bytes: ByteArray): OtaStatus? {
    if (bytes.size < 3) return null
    val state = bytes[0].toInt() and 0xFF
    val progress = bytes[1].toInt() and 0xFF
    val errorCode = bytes[2].toInt() // signed - matches firmware's int8_t HTTPUpdate/HTTPClient error codes
    return OtaStatus(OtaState.fromWire(state), progress, errorCode)
}

/**
 * Mirrors HTTPUpdate.h / HTTPClient.h's own error codes (include/ota.h on the
 * firmware side casts httpUpdate.getLastError() straight into an int8_t) -
 * turns the bare number into something the person tapping "Start update"
 * can actually act on instead of just "Update failed".
 */
fun otaErrorHint(code: Int): String? = when (code) {
    -1 -> "connection refused - check the URL host/port"
    -2 -> "failed sending request headers"
    -3 -> "failed sending request"
    -4 -> "not connected to server"
    -5 -> "connection lost mid-download"
    -6, -7 -> "server didn't respond like an HTTP server"
    -8 -> "device out of RAM"
    -9 -> "bad content encoding"
    -10 -> "flash write failed"
    -11 -> "timed out waiting for server"
    -100 -> "firmware file too big for the free partition space"
    -101 -> "server didn't report a file size"
    -102 -> "404 - firmware file not found at that URL"
    -103 -> "403 - server forbade the request"
    -104 -> "unexpected HTTP status from server"
    -105 -> "MD5 checksum mismatch - corrupted download"
    -106 -> "not a valid firmware image"
    -107 -> "firmware built for the wrong flash chip/size"
    -108 -> "no free OTA partition on the device"
    else -> null
}

/** Builds Command characteristic write payloads - every one is <=20 bytes, no MTU negotiation needed. */
object BleCommand {
    private const val OP_SET_TARGET_WEIGHT: Int = 1
    private const val OP_SET_MODE: Int = 2
    private const val OP_START: Int = 3
    private const val OP_STOP: Int = 4
    private const val OP_EMERGENCY_STOP: Int = 5
    private const val OP_SELECT_CUP_PROFILE: Int = 6
    private const val OP_SET_CUP_PROFILE_WEIGHT: Int = 7
    private const val OP_SET_CUP_PROFILE_NAME: Int = 8
    private const val OP_TARE: Int = 9
    private const val OP_CAL_CLEAR: Int = 10
    private const val OP_CAL_ADD_POINT: Int = 11
    private const val OP_CAL_SAVE: Int = 12
    private const val OP_SET_SMOOTHING_ALPHA: Int = 13
    private const val OP_OTA_START: Int = 14
    private const val OP_OTA_CANCEL: Int = 15

    fun setTargetWeight(grams: Float): ByteArray =
        ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            .put(OP_SET_TARGET_WEIGHT.toByte()).putFloat(grams).array()

    fun setMode(mode: GrinderMode): ByteArray =
        byteArrayOf(OP_SET_MODE.toByte(), mode.wireValue.toByte())

    fun start(): ByteArray = byteArrayOf(OP_START.toByte())
    fun stop(): ByteArray = byteArrayOf(OP_STOP.toByte())
    fun emergencyStop(): ByteArray = byteArrayOf(OP_EMERGENCY_STOP.toByte())

    fun selectCupProfile(id: Int): ByteArray = byteArrayOf(OP_SELECT_CUP_PROFILE.toByte(), id.toByte())

    fun setCupProfileWeight(id: Int, cupWeightG: Float, toleranceG: Float): ByteArray =
        ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .put(OP_SET_CUP_PROFILE_WEIGHT.toByte())
            .put(id.toByte())
            .putFloat(cupWeightG)
            .putFloat(toleranceG)
            .array()

    fun setCupProfileName(id: Int, name: String): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8).copyOf(11) // truncate/pad to firmware's 11-char limit
        return ByteBuffer.allocate(2 + nameBytes.size)
            .put(OP_SET_CUP_PROFILE_NAME.toByte())
            .put(id.toByte())
            .put(nameBytes)
            .array()
    }

    fun tare(): ByteArray = byteArrayOf(OP_TARE.toByte())
    fun calClear(): ByteArray = byteArrayOf(OP_CAL_CLEAR.toByte())
    fun calSave(): ByteArray = byteArrayOf(OP_CAL_SAVE.toByte())

    fun calAddPoint(knownWeightG: Float): ByteArray =
        ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            .put(OP_CAL_ADD_POINT.toByte()).putFloat(knownWeightG).array()

    /** Not persisted on the firmware side - resend after every connect (GrinderViewModel does this). */
    fun setSmoothingAlpha(alpha: Float): ByteArray =
        ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            .put(OP_SET_SMOOTHING_ALPHA.toByte()).putFloat(alpha).array()

    /** SSID/password/URL must already be written via BleOtaConfig before this does anything. */
    fun otaStart(): ByteArray = byteArrayOf(OP_OTA_START.toByte())

    /** Only stops an in-progress WiFi connect attempt - can't interrupt an update already flashing. */
    fun otaCancel(): ByteArray = byteArrayOf(OP_OTA_CANCEL.toByte())
}

/**
 * Builds OtaConfig characteristic writes: a field-id byte + raw UTF-8 string
 * bytes (not null-terminated on the wire - firmware's OtaManager setters take
 * an explicit length). Needs the bumped MTU (see GrinderBleManager) since
 * these can run past the default 20-byte payload limit.
 */
object BleOtaConfig {
    private const val FIELD_SSID: Int = 0
    private const val FIELD_PASSWORD: Int = 1
    private const val FIELD_URL: Int = 2

    // One less than OtaManager's fixed buffers (include/ota.h) to leave room
    // for the firmware's own null terminator.
    fun ssid(value: String): ByteArray = field(FIELD_SSID, value, 32)
    fun password(value: String): ByteArray = field(FIELD_PASSWORD, value, 64)
    fun url(value: String): ByteArray = field(FIELD_URL, value, 128)

    private fun field(fieldId: Int, value: String, maxLen: Int): ByteArray {
        val raw = value.toByteArray(Charsets.UTF_8)
        val truncated = if (raw.size > maxLen) raw.copyOf(maxLen) else raw
        return byteArrayOf(fieldId.toByte()) + truncated
    }
}
