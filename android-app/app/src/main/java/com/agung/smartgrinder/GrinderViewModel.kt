package com.agung.smartgrinder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agung.smartgrinder.ble.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GrinderViewModel(application: Application) : AndroidViewModel(application) {

    private val ble = GrinderBleManager(application)

    val connectionState: StateFlow<ConnectionState> = ble.connectionState
    val status: StateFlow<GrinderStatus?> = ble.status

    // Local editable copies of the 4 cup profile slots, filled in via loadCupProfiles().
    private val _cupProfiles = MutableStateFlow<List<CupProfile>>(
        List(CUP_PROFILE_COUNT) { CupProfile(-1f, 0f, "") }
    )
    val cupProfiles: StateFlow<List<CupProfile>> = _cupProfiles.asStateFlow()

    fun connect() = ble.connect()
    fun disconnect() = ble.disconnect()

    fun setTargetWeight(grams: Float) = ble.sendCommand(BleCommand.setTargetWeight(grams))
    fun setMode(mode: GrinderMode) = ble.sendCommand(BleCommand.setMode(mode))
    fun start() = ble.sendCommand(BleCommand.start())
    fun stop() = ble.sendCommand(BleCommand.stop())
    fun emergencyStop() = ble.sendCommand(BleCommand.emergencyStop())

    fun selectCupProfile(id: Int) = ble.sendCommand(BleCommand.selectCupProfile(id))

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
