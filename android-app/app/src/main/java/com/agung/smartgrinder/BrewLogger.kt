package com.agung.smartgrinder

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends one JSON line per completed manual brew to brew_logs.jsonl under
 * the app's external files dir - same pattern as ShotLogger, pullable via
 * adb without root and without the phone needing to be plugged into a
 * laptop when the brew happens.
 */
class BrewLogger(context: Context) {
    private val file: File = File(context.getExternalFilesDir(null), "brew_logs.jsonl")

    fun logBrew(data: BrewLogData) {
        val timestamp = System.currentTimeMillis()
        val timeLabel = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))

        // Locale.US explicitly everywhere below - the default locale (e.g.
        // Indonesian) formats floats with a comma, which would corrupt the
        // JSON's field separators (bit us once already with ShotLogger).
        val samplesJson = data.samples.joinToString(",") {
            String.format(Locale.US, "{\"t\":%.2f,\"w\":%.2f}", it.tSeconds, it.weightG)
        }
        val segmentsJson = data.segments.joinToString(",") {
            String.format(
                Locale.US,
                "{\"label\":\"%s\",\"startSec\":%.2f,\"endSec\":%.2f,\"weightG\":%.2f}",
                it.label, it.startSec, it.endSec, it.weightG
            )
        }
        val line = String.format(
            Locale.US,
            "{\"timestamp\":%d,\"time\":\"%s\",\"method\":\"%s\",\"coffeeWeightG\":%.1f,\"ratio\":%.1f,\"waterTargetG\":%.1f,\"finalWaterG\":%.1f,\"durationSec\":%.1f,\"samples\":[%s],\"segments\":[%s]}",
            timestamp, timeLabel, data.methodLabel, data.coffeeWeightG, data.ratio,
            data.waterTargetG, data.finalWaterG, data.durationSec, samplesJson, segmentsJson
        )

        try {
            file.appendText(line + "\n")
        } catch (e: Exception) {
            // Best-effort - a failed log write should never crash the app.
        }
    }
}
