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

    SystemStatus _status;
    CalibrationData _calibration;

    uint32_t _last_sensor_read_ms = 0;
    uint32_t _last_state_update_ms = 0;
    SystemState _prev_state = STATE_IDLE; // to detect GRINDING -> IDLE (grind completed)

    bool _cup_settling = false;
    uint32_t _cup_settle_start_ms = 0;

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
