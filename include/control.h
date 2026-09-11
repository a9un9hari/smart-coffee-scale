#ifndef CONTROL_H
#define CONTROL_H

#include <Arduino.h>
#include "config.h"
#include "data_types.h"
#include "hx711.h"
#include "buttons.h"
#include "motor.h"
#include "state_machine.h"
#include "encoder.h"
#include "display.h"
#include "storage.h"

// Orchestrates all subsystems: reads the load cell, debounces buttons,
// drives the state machine, and lets the state machine drive the motor.
// main.cpp only needs begin() in setup() and update() in loop().
class GrinderController {
public:
    GrinderController();

    void begin();
    void update(); // call every loop() iteration - each subsystem paces itself

    const SystemStatus &getStatus() const { return _status; }

private:
    HX711 _scale;
    Buttons _buttons;
    Encoder _encoder;
    MotorControl _motor;
    StateMachine _state_machine;
    Display _display;
    Storage _storage;

    SystemStatus _status;
    CalibrationData _calibration;

    uint32_t _last_sensor_read_ms = 0;
    uint32_t _last_button_update_ms = 0;
    uint32_t _last_state_update_ms = 0;
    SystemState _prev_state = STATE_IDLE; // to detect GRINDING -> IDLE (grind completed)

    void readSensors(uint32_t now);
    void readButtons(uint32_t now);
    void readEncoder();
    void runStateMachine(uint32_t now);
};

#endif // CONTROL_H
