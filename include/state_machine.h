#ifndef STATE_MACHINE_H
#define STATE_MACHINE_H

#include <Arduino.h>
#include "config.h"
#include "data_types.h"
#include "motor.h"

enum StateMachineEvent {
    EVT_CUP_DETECTED,      // GRINDER mode: weight settled matching the active cup profile
    EVT_BLE_START,          // app "Start" - grinder: manual start w/o cup match; espresso: start pull
    EVT_BLE_STOP,            // app "Stop" - abort current grind/pull back to idle
    EVT_WEIGHT_CHANGED,       // espresso: weight moved off zero, pull started
    EVT_TARGET_REACHED,       // grinder: dose complete
    EVT_SHOT_STABLE,          // espresso: weight held steady, shot done
    EVT_ERROR_OCCURRED,
    EVT_EMERGENCY_STOP        // app e-stop command - abort to IDLE from anywhere
};

class StateMachine {
public:
    void init(SystemStatus *status);
    void attachMotor(MotorControl *motor) { _motor = motor; }
    void update();                        // call every STATE_MACHINE_UPDATE_MS
    void onEvent(StateMachineEvent event);
    void onModeCommand(SystemMode requested_mode); // app mode toggle, only honored from an idle-ish state
    SystemState getCurrentState() const { return _status->state; }

private:
    SystemStatus *_status;
    MotorControl *_motor = nullptr;

    // Weight at the moment a grind session started (cup auto-detect or
    // manual BLE start) - GRINDING completion is measured relative to
    // this, not raw current_weight_g, so it doesn't matter how much the
    // dosing cup itself weighs.
    float _session_start_weight_g = 0.0f;

    // PULLING-state tracking: weight considered "stable" once it hasn't
    // moved for ESPRESSO_STABLE_MS - that's when a shot is done.
    float _pulling_last_weight_g;
    uint32_t _pulling_stable_since_ms;
    bool _pulling_tracking;

    void transitionTo(SystemState new_state);
    void handleGrinderEvent(StateMachineEvent event);
    void handleEspressoEvent(StateMachineEvent event);
};

#endif // STATE_MACHINE_H
