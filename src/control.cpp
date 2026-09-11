#include "control.h"

GrinderController::GrinderController()
    : _scale(PIN_HX711_DOUT, PIN_HX711_CLK) {}

void GrinderController::begin() {
    _scale.begin();
    _scale.setCalibrationFactor(0.01f); // placeholder: grams per raw count, calibrate later
    _scale.tare();

    _buttons.begin();
    _motor.begin();
    // TODO: load _calibration from EEPROM (overrides tare/calibration factor above)

    _status.target_weight_g = 18.0f;
    _status.current_weight_g = 0.0f;
    _status.motor_running = false;

    _state_machine.init(&_status); // also sets mode=GRINDER, state=IDLE, error_code=0
    _state_machine.attachMotor(&_motor);
}

void GrinderController::readSensors(uint32_t now) {
    if (now - _last_sensor_read_ms < HX711_READ_INTERVAL_MS) {
        return;
    }
    _last_sensor_read_ms = now;

    _status.current_weight_g = _scale.readWeight();
    if (_scale.getStatus() != HX711_OK) {
        // load cell error is not automatically fatal (transient timeouts happen) -
        // the state machine only escalates it via EVT_ERROR_OCCURRED where relevant
        _status.error_code = 2; // see hx711.h HX711Status for the underlying cause
    }
}

void GrinderController::readButtons(uint32_t now) {
    if (now - _last_button_update_ms < BUTTON_UPDATE_MS) {
        return;
    }
    _last_button_update_ms = now;

    _buttons.update();

    if (_buttons.wasPressed(START_BUTTON)) {
        _state_machine.onEvent(EVT_BUTTON_START_PRESSED);
    }
    if (_buttons.wasPressed(MODE_BUTTON)) {
        _state_machine.onEvent(EVT_BUTTON_MODE_PRESSED);
    }

    // emergency stop: both buttons held together for 2s, from any state
    static uint32_t both_held_since_ms = 0;
    if (_buttons.isPressed(START_BUTTON) && _buttons.isPressed(MODE_BUTTON)) {
        if (both_held_since_ms == 0) {
            both_held_since_ms = now;
        } else if (now - both_held_since_ms >= 2000) {
            _state_machine.onEvent(EVT_EMERGENCY_STOP);
            both_held_since_ms = 0;
        }
    } else {
        both_held_since_ms = 0;
    }
}

void GrinderController::runStateMachine(uint32_t now) {
    if (now - _last_state_update_ms < STATE_MACHINE_UPDATE_MS) {
        return;
    }
    _last_state_update_ms = now;

    _state_machine.update();
    _motor.update();
}

void GrinderController::update() {
    uint32_t now = millis();

    readSensors(now);
    readButtons(now);
    runStateMachine(now);
}
