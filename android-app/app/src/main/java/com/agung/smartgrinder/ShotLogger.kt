package com.agung.smartgrinder

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends one JSON line per completed espresso shot to a file under the
 * app's external files dir - readable later via `adb pull` without root
 * and without the phone needing to be plugged into a laptop at the moment
 * the shot happens. ESP32 stays untouched (its flash is already ~76% used;
 * this is purely an Android-side record of what the app already receives
 * over BLE).
 */
class ShotLogger(context: Context) {
    private val file: File = File(context.getExternalFilesDir(null), "shot_logs.jsonl")

    /** Returns the timestamp used for this entry, so callers (e.g. a matching screenshot) can name themselves to line up with it. */
    fun logShot(targetWeightG: Float, finalWeightG: Float, samples: List<ShotSample>): Long {
        val timestamp = System.currentTimeMillis()
        val timeLabel = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
        val durationSec = samples.lastOrNull()?.tSeconds ?: 0f
        // Locale.US explicitly everywhere below - JSON needs a dot decimal
        // separator, but the default locale (e.g. Indonesian) formats floats
        // with a comma, which would silently corrupt every number into an
        // extra (invalid) field separator.
        val samplesJson = samples.joinToString(",") {
            String.format(Locale.US, "{\"t\":%.2f,\"w\":%.2f}", it.tSeconds, it.weightG)
        }
        val line = String.format(
            Locale.US,
            "{\"timestamp\":%d,\"time\":\"%s\",\"targetWeightG\":%.1f,\"finalWeightG\":%.1f,\"durationSec\":%.1f,\"samples\":[%s]}",
            timestamp, timeLabel, targetWeightG, finalWeightG, durationSec, samplesJson
        )

        try {
            file.appendText(line + "\n")
        } catch (e: Exception) {
            // Best-effort - a failed log write should never crash the app.
        }
        return timestamp
    }
}
