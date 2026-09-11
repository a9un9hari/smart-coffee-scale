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
}
