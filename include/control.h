#ifndef CONTROL_H
#define CONTROL_H

#include <Arduino.h>
#include "config.h"
#include "data_types.h"
#include "hx711.h"
#include "motor.h"
#include "state_machine.h"
#include "ble.h"
#include "storage.h"
#include "ota.h"

// Orchestrates all subsystems: reads the load cell, watches for a known
// dosing cup being placed (auto-start), drains BLE commands from the
// Android app, and lets the state machine drive the motor. main.cpp only
// needs begin() in setup() and update() in loop().
class GrinderController {
public:
    GrinderController();

    void begin();
    void update(); // call every loop() iteration - each subsystem paces itself

    const SystemStatus &getStatus() const { return _status; }

private:
    HX711 _scale;
    MotorControl _motor;
    StateMachine _state_machine;
    BleServer _ble;
    Storage _storage;
    OtaManager _ota;

    SystemStatus _status;
    CalibrationData _calibration;

    uint32_t _last_sensor_read_ms = 0;
    uint32_t _last_state_update_ms = 0;
    SystemState _prev_state = STATE_IDLE; // to detect GRINDING -> IDLE (grind completed)

    bool _cup_settling = false;
    uint32_t _cup_settle_start_ms = 0;

    // Light exponential smoothing on top of HX711::readWeight()'s own 5-sample
    // burst average - cuts the residual ADC jitter the app was showing on
    // every decimal digit. Deliberately light by default (not a big alpha)
    // so it doesn't add meaningful lag to the GRINDING stop-at-target check.
    // Runtime-adjustable via BLE_OP_SET_SMOOTHING_ALPHA (Settings slider in
    // the app) - deliberately NOT persisted to EEPROM, since CalibrationData
    // has a CRC over its whole layout and adding a field would invalidate
    // every already-provisioned board's saved calibration/cup profiles. The
    // app remembers the chosen value itself and resends it on every connect.
    float _filtered_weight_g = 0.0f;
    bool _filter_initialized = false;
    float _smoothing_alpha = WEIGHT_SMOOTHING_ALPHA;

    uint32_t _last_debug_print_ms = 0; // throttles DEBUG_HX711_RAW logging

    // In-progress multi-point calibration session (cleared by BLE_OP_CAL_CLEAR,
    // filled by repeated BLE_OP_CAL_ADD_POINT, consumed by BLE_OP_CAL_SAVE).
    static const uint8_t MAX_CAL_POINTS = 8;
    struct CalPoint { float raw; float weight_g; };
    CalPoint _cal_points[MAX_CAL_POINTS];
    uint8_t _cal_point_count = 0;

    void readSensors(uint32_t now);
    void readCupDetect(uint32_t now);
    void processBleCommands();
    void runStateMachine(uint32_t now);

    long sampleRawAveraged(uint8_t samples);
    void handleTare();
    void handleCalAddPoint(float known_weight_g);
    void handleCalSave();
};

#endif // CONTROL_H
