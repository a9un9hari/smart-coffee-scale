package com.agung.smartgrinder

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agung.smartgrinder.ble.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One (elapsed seconds since pull started, weight in grams) sample for the shot graph. */
data class ShotSample(val tSeconds: Float, val weightG: Float)

class GrinderViewModel(application: Application) : AndroidViewModel(application) {

    private val ble = GrinderBleManager(application)
    private val prefs = AppPreferences(application)

    private val _darkTheme = MutableStateFlow(prefs.darkTheme)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        prefs.darkTheme = enabled
        _darkTheme.value = enabled
    }

    val connectionState: StateFlow<ConnectionState> = ble.connectionState
    val status: StateFlow<GrinderStatus?> = ble.status
    val calibrationStatus: StateFlow<CalibrationStatus?> = ble.calibrationStatus

    // Espresso shot graph: recorded from PULL_SHOT through SHOT_COMPLETE, reset
    // when a new pull starts. Sourced entirely from the existing Status
    // notification stream (~6-7Hz) - no separate firmware protocol needed.
    private val _shotSamples = MutableStateFlow<List<ShotSample>>(emptyList())
    val shotSamples: StateFlow<List<ShotSample>> = _shotSamples.asStateFlow()
    private var shotStartElapsedMs = 0L
    private var lastState: GrinderState? = null

    init {
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
