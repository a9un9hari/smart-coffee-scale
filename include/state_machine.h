#ifndef STATE_MACHINE_H
#define STATE_MACHINE_H

#include <Arduino.h>
#include "config.h"
#include "data_types.h"
#include "motor.h"

enum StateMachineEvent {
    EVT_BUTTON_START_PRESSED,
    EVT_BUTTON_MODE_PRESSED,
    EVT_ENCODER_CHANGED,
    EVT_WEIGHT_CHANGED,
    EVT_TARGET_REACHED,
    EVT_SHOT_STABLE,
    EVT_ERROR_OCCURRED,
    EVT_EMERGENCY_STOP     // both buttons held 2s - abort to IDLE from anywhere
};

class StateMachine {
public:
    void init(SystemStatus *status);
    void attachMotor(MotorControl *motor) { _motor = motor; }
    void update();                        // call every STATE_MACHINE_UPDATE_MS
    void onEvent(StateMachineEvent event);
    SystemState getCurrentState() const { return _status->state; }

private:
    SystemStatus *_status;
    MotorControl *_motor = nullptr;

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
