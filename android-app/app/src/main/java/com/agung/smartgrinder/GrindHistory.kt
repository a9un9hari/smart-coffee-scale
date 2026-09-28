package com.agung.smartgrinder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One grind session as recorded by the firmware's GrindLog (see
 * include/grind_log.h) - one CSV row per session, synced over BLE - plus
 * what only the app knows: when it was first received, and an optional
 * reference weight from another scale + note entered by hand.
 *
 * (boot, uptimeS) is the firmware's identity for a session: its boot
 * counter plus uptime seconds when the grind started - unique, and the
 * cursor the app hands back to ask for "anything newer".
 */
data class GrindRecord(
    val boot: Long,
    val uptimeS: Long,
    val profile: Int,          // 1-based, as the firmware logs it
    val targetG: Float,
    val learnedBeforeG: Float,
    val mainG: Float?,         // null when the session ended before the post-grind settle (e.g. user stop)
    val learnedAfterG: Float?,
    val pulses: Int,
    val pulseShortG: List<Float>,
    val finalG: Float,
    val result: String,        // ok / gave_up / cup_removed / no_dot / user_stop / interrupted / error
    val receivedAtMs: Long,    // phone clock when first synced - the board has no RTC
    val refWeightG: Float? = null,
    val note: String = ""
) {
    val key: Pair<Long, Long> get() = boot to uptimeS

    companion object {
        /** Parses one firmware CSV row, or null for a header/malformed/truncated line. */
        fun fromCsv(line: String, receivedAtMs: Long): GrindRecord? {
            val f = line.trim().split(',')
            if (f.size != 11) return null
            return try {
                GrindRecord(
                    boot = f[0].toLong(),
                    uptimeS = f[1].toLong(),
                    profile = f[2].toInt(),
                    targetG = f[3].toFloat(),
                    learnedBeforeG = f[4].toFloat(),
                    mainG = f[5].toFloat().takeUnless { it.isNaN() },
                    learnedAfterG = f[6].toFloat().takeUnless { it.isNaN() },
                    pulses = f[7].toInt(),
                    pulseShortG = f[8].split(';').mapNotNull { it.toFloatOrNull() },
                    finalG = f[9].toFloat(),
                    result = f[10],
                    receivedAtMs = receivedAtMs
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}

/**
 * Persists synced grind sessions to grind_history.json under the app's
 * external files dir - same "pullable via adb without root" location as
 * ShotLogger/BrewLogger, but a single JSON array (not append-only lines)
 * since records get edited afterwards (reference weight, note).
 */
class GrindHistoryStore(context: Context) {
    private val file = File(context.getExternalFilesDir(null), "grind_history.json")

    fun load(): List<GrindRecord> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList() // best-effort - a corrupt file shouldn't crash the app
    }

    fun save(records: List<GrindRecord>) {
        try {
            val arr = JSONArray()
            records.forEach { arr.put(toJson(it)) }
            file.writeText(arr.toString(1))
        } catch (e: Exception) {
            // Best-effort, same as the other loggers.
        }
    }

    private fun toJson(r: GrindRecord) = JSONObject().apply {
        put("boot", r.boot)
        put("uptimeS", r.uptimeS)
        put("profile", r.profile)
        put("targetG", r.targetG.toDouble())
        put("learnedBeforeG", r.learnedBeforeG.toDouble())
        put("mainG", r.mainG?.toDouble() ?: JSONObject.NULL)
        put("learnedAfterG", r.learnedAfterG?.toDouble() ?: JSONObject.NULL)
        put("pulses", r.pulses)
        put("pulseShortG", JSONArray(r.pulseShortG.map { it.toDouble() }))
        put("finalG", r.finalG.toDouble())
        put("result", r.result)
        put("receivedAtMs", r.receivedAtMs)
        put("refWeightG", r.refWeightG?.toDouble() ?: JSONObject.NULL)
        put("note", r.note)
    }

    private fun fromJson(o: JSONObject): GrindRecord {
        fun optFloat(key: String): Float? = if (o.isNull(key)) null else o.getDouble(key).toFloat()
        val shorts = o.optJSONArray("pulseShortG")
        return GrindRecord(
            boot = o.getLong("boot"),
            uptimeS = o.getLong("uptimeS"),
            profile = o.getInt("profile"),
            targetG = o.getDouble("targetG").toFloat(),
            learnedBeforeG = o.getDouble("learnedBeforeG").toFloat(),
            mainG = optFloat("mainG"),
            learnedAfterG = optFloat("learnedAfterG"),
            pulses = o.getInt("pulses"),
            pulseShortG = if (shorts == null) emptyList() else (0 until shorts.length()).map { shorts.getDouble(it).toFloat() },
            finalG = o.getDouble("finalG").toFloat(),
            result = o.getString("result"),
            receivedAtMs = o.getLong("receivedAtMs"),
            refWeightG = optFloat("refWeightG"),
            note = o.optString("note", "")
        )
    }
}
