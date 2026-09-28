package com.agung.smartgrinder

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agung.smartgrinder.ble.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One (elapsed seconds since pull started, weight in grams) sample for the shot graph. */
data class ShotSample(val tSeconds: Float, val weightG: Float)

/** One (elapsed seconds since brew started, poured water in grams) sample for the brew graph. */
data class BrewLogSample(val tSeconds: Float, val weightG: Float)

/** One auto-tagged pour segment ("Bloom", "Pour N") from BrewScreen's flow-rate edge detection. */
data class BrewLogSegment(val label: String, val startSec: Float, val endSec: Float, val weightG: Float)

/** Everything BrewScreen knows about one completed manual brew, handed up for logging on Stop. */
data class BrewLogData(
    val methodLabel: String,
    val coffeeWeightG: Float,
    val ratio: Float,
    val waterTargetG: Float,
    val finalWaterG: Float,
    val durationSec: Float,
    val samples: List<BrewLogSample>,
    val segments: List<BrewLogSegment>
)

class GrinderViewModel(application: Application) : AndroidViewModel(application) {

    private val ble = GrinderBleManager(application)
    private val prefs = AppPreferences(application)
    private val shotLogger = ShotLogger(application)
    private val brewLogger = BrewLogger(application)

    fun logBrew(data: BrewLogData) = brewLogger.logBrew(data)

    // Grind sessions synced from the firmware's on-flash GrindLog, newest
    // first. Synced automatically on every connect (only rows newer than
    // what's already stored), and pushed live by the firmware as each
    // grind finishes while connected.
    private val grindHistoryStore = GrindHistoryStore(application)
    private val _grindHistory = MutableStateFlow(sortedHistory(grindHistoryStore.load()))
    val grindHistory: StateFlow<List<GrindRecord>> = _grindHistory.asStateFlow()

    private val _grindLogSyncing = MutableStateFlow(false)
    val grindLogSyncing: StateFlow<Boolean> = _grindLogSyncing.asStateFlow()

    private fun sortedHistory(records: List<GrindRecord>) =
        records.sortedWith(compareByDescending<GrindRecord> { it.boot }.thenByDescending { it.uptimeS })

    /**
     * Incremental (default, used on connect): only sessions newer than the
     * newest one stored. Full: everything still on the grinder - backfills
     * any row missed earlier (e.g. one an older app version failed to
     * parse); already-stored rows are skipped, keeping their manual fields.
     */
    fun syncGrindLog(full: Boolean = false) {
        val newest = if (full) null else _grindHistory.value.firstOrNull()
        if (ble.requestGrindLog(newest?.boot ?: 0L, newest?.uptimeS ?: 0L)) {
            _grindLogSyncing.value = true
        }
    }

    private fun onGrindLogLine(line: String) {
        if (line == BleGrindLog.END) {
            _grindLogSyncing.value = false
            return
        }
        val record = GrindRecord.fromCsv(line, System.currentTimeMillis()) ?: return
        val current = _grindHistory.value
        if (current.any { it.key == record.key }) return // already have it - keep its manual fields
        val updated = sortedHistory(current + record)
        _grindHistory.value = updated
        grindHistoryStore.save(updated)
    }

    /** Saves a reference-scale weight (net dose) and/or note for one session; null clears the weight. */
    fun setGrindReference(boot: Long, uptimeS: Long, refWeightG: Float?, note: String) {
        val updated = _grindHistory.value.map {
            if (it.boot == boot && it.uptimeS == uptimeS) it.copy(refWeightG = refWeightG, note = note) else it
        }
        _grindHistory.value = updated
        grindHistoryStore.save(updated)
    }

    private val _darkTheme = MutableStateFlow(prefs.darkTheme)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        prefs.darkTheme = enabled
        _darkTheme.value = enabled
    }

    private val _smoothingAlpha = MutableStateFlow(prefs.smoothingAlpha)
    val smoothingAlpha: StateFlow<Float> = _smoothingAlpha.asStateFlow()

    fun setSmoothingAlpha(alpha: Float) {
        prefs.smoothingAlpha = alpha
        _smoothingAlpha.value = alpha
        ble.sendCommand(BleCommand.setSmoothingAlpha(alpha))
    }

    // Firmware persists this itself (so phone-less grinding survives a
    // power cycle) - the app just sends changes and reads it back from
    // GrinderStatus.weightSource, never resends it on connect.
    fun setWeightSource(source: WeightSource) {
        ble.sendCommand(BleCommand.setWeightSource(source))
    }

    private val _timemoreAutoConnect = MutableStateFlow(prefs.timemoreAutoConnect)
    val timemoreAutoConnect: StateFlow<Boolean> = _timemoreAutoConnect.asStateFlow()

    fun setTimemoreAutoConnect(enabled: Boolean) {
        prefs.timemoreAutoConnect = enabled
        _timemoreAutoConnect.value = enabled
        ble.sendCommand(BleCommand.setTimemoreAutoConnect(enabled))
    }

    val connectionState: StateFlow<ConnectionState> = ble.connectionState
    val status: StateFlow<GrinderStatus?> = ble.status
    val calibrationStatus: StateFlow<CalibrationStatus?> = ble.calibrationStatus
    val otaStatus: StateFlow<OtaStatus?> = ble.otaStatus

    // Espresso shot graph: recorded from PULL_SHOT through SHOT_COMPLETE, reset
    // when a new pull starts. Sourced entirely from the existing Status
    // notification stream (~6-7Hz) - no separate firmware protocol needed.
    private val _shotSamples = MutableStateFlow<List<ShotSample>>(emptyList())
    val shotSamples: StateFlow<List<ShotSample>> = _shotSamples.asStateFlow()
    private var shotStartElapsedMs = 0L
    private var lastState: GrinderState? = null

    // Fired right after a completed shot is written to the log, carrying
    // the same timestamp used as its record's key - MainActivity uses this
    // to capture a matching screenshot (PixelCopy needs an Activity/Window,
    // not available from a ViewModel), named to line up with the log entry.
    private val _shotCompletedEvents = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val shotCompletedEvents: SharedFlow<Long> = _shotCompletedEvents

    init {
        // Firmware doesn't persist the smoothing alpha - reapply the user's
        // saved preference every time a connection is (re-)established.
        viewModelScope.launch {
            ble.connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    ble.sendCommand(BleCommand.setSmoothingAlpha(_smoothingAlpha.value))
                    ble.sendCommand(BleCommand.setTimemoreAutoConnect(_timemoreAutoConnect.value))
                    syncGrindLog()
                } else {
                    _grindLogSyncing.value = false
                }
            }
        }

        viewModelScope.launch {
            ble.grindLogLines.collect { onGrindLogLine(it) }
        }

        viewModelScope.launch {
            ble.status.collect { s ->
                val state = s?.state ?: return@collect
                val wasPulling = lastState == GrinderState.PULL_SHOT || lastState == GrinderState.PULLING
                if (state == GrinderState.PULL_SHOT && !wasPulling) {
                    shotStartElapsedMs = SystemClock.elapsedRealtime()
                    _shotSamples.value = emptyList()
                }
                if (state == GrinderState.PULL_SHOT || state == GrinderState.PULLING) {
                    val t = (SystemClock.elapsedRealtime() - shotStartElapsedMs) / 1000f
                    _shotSamples.value = _shotSamples.value + ShotSample(t, s.weightG)
                }
                if (state == GrinderState.SHOT_COMPLETE && lastState != GrinderState.SHOT_COMPLETE) {
                    val timestamp = shotLogger.logShot(s.targetWeightG, s.weightG, _shotSamples.value)
                    _shotCompletedEvents.tryEmit(timestamp)
                }
                lastState = state
            }
        }
    }

    // Local editable copies of the 4 cup profile slots, filled in via loadCupProfiles().
    private val _cupProfiles = MutableStateFlow<List<CupProfile>>(
        List(CUP_PROFILE_COUNT) { CupProfile(-1f, 0f, "") }
    )
    val cupProfiles: StateFlow<List<CupProfile>> = _cupProfiles.asStateFlow()

    fun connect() = ble.connect()
    fun disconnect() = ble.disconnect()

    /** Tries to reconnect to the last device we paired with; falls back to a fresh scan if there isn't one. */
    fun connectToSavedDevice() {
        if (!ble.connectToSavedDevice()) ble.connect()
    }

    fun setTargetWeight(grams: Float) = ble.sendCommand(BleCommand.setTargetWeight(grams))
    fun setMode(mode: GrinderMode) = ble.sendCommand(BleCommand.setMode(mode))
    fun start() = ble.sendCommand(BleCommand.start())
    fun stop() = ble.sendCommand(BleCommand.stop())
    fun emergencyStop() = ble.sendCommand(BleCommand.emergencyStop())

    fun selectCupProfile(id: Int) = ble.sendCommand(BleCommand.selectCupProfile(id))

    fun tare() = ble.tare()
    fun calClear() = ble.calClear()
    fun calAddPoint(knownWeightG: Float) = ble.calAddPoint(knownWeightG)
    fun calSave() = ble.calSave()

    // Sent as one shot right before the start command, instead of on every
    // keystroke - fewer BLE writes, and no risk of the firmware ending up
    // with a stale/partial value if an intermediate keystroke write is lost
    // (GrinderBleManager doesn't check write status, see its onCharacteristicWrite).
    fun otaStart(ssid: String, password: String, url: String) {
        ble.setOtaSsid(ssid)
        ble.setOtaPassword(password)
        ble.setOtaUrl(url)
        ble.otaStart()
    }
    fun otaCancel() = ble.otaCancel()

    fun saveCupProfile(id: Int, name: String, cupWeightG: Float, toleranceG: Float) {
        ble.sendCommand(BleCommand.setCupProfileName(id, name))
        ble.sendCommand(BleCommand.setCupProfileWeight(id, cupWeightG, toleranceG))
        loadCupProfile(id) // refresh local copy from the device once it's applied
    }

    /** Pulls all 4 stored profiles from the device - call after connecting. */
    fun loadAllCupProfiles() {
        for (id in 0 until CUP_PROFILE_COUNT) {
            loadCupProfile(id)
        }
    }

    private fun loadCupProfile(id: Int) {
        viewModelScope.launch {
            ble.queryCupProfile(id) { profile ->
                val updated = _cupProfiles.value.toMutableList()
                if (id < updated.size) {
                    updated[id] = profile
                    _cupProfiles.value = updated
                }
            }
        }
    }
}
