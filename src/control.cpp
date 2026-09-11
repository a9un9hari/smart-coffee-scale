#include "control.h"

GrinderController::GrinderController()
    : _scale(PIN_HX711_DOUT, PIN_HX711_CLK) {}

void GrinderController::begin() {
    _scale.begin();
    _buttons.begin();
    _encoder.begin();
    _encoder.setRange(10, 30); // grind target: 10-30g
    _motor.begin();
    _display.begin();
    _storage.begin();

    if (_storage.restore(_calibration)) {
        _scale.setCalibrationFactor(_calibration.scale_factor);
        _scale.setOffset(_calibration.offset);
    } else {
        // first boot / corrupt flash: fall back to a physical tare + factory scale.
        // -0.001547 g/count: empirically corrected 2026-09-11 by comparing a
        // known 33.4g weight against this firmware's own live reading (an
        // initial raw-delta estimate of -0.000691 undershot by ~2.24x).
        _scale.setCalibrationFactor(-0.001547f);
        _scale.tare();
        _calibration.offset = _scale.getOffset();
        _calibration.scale_factor = -0.001547f;
        _calibration.target_weight_g = 18.0f;
        _calibration.wear_counter = 0;
        _storage.save(_calibration);
    }

    _status.target_weight_g = _calibration.target_weight_g;
    _status.current_weight_g = 0.0f;
    _status.motor_running = false;
    _encoder.setValue((int)_status.target_weight_g);

    _state_machine.init(&_status); // also sets mode=GRINDER, state=IDLE, error_code=0
    _state_machine.attachMotor(&_motor);
    _prev_state = _status.state;
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

void GrinderController::readEncoder() {
    // Quadrature needs polling every loop iteration, not throttled to an
    // interval like the other subsystems, or clicks get missed.
    _encoder.update();

    int8_t delta = _encoder.getDelta();
    if (delta == 0) {
        return;
    }

    SystemState state = _state_machine.getCurrentState();
    if (state == STATE_SELECT_WEIGHT || state == STATE_UPDATE_WEIGHT) {
        _status.target_weight_g = (float)_encoder.getValue();
        _state_machine.onEvent(EVT_ENCODER_CHANGED);
    }
}

void GrinderController::runStateMachine(uint32_t now) {
    if (now - _last_state_update_ms < STATE_MACHINE_UPDATE_MS) {
        return;
    }
    _last_state_update_ms = now;

    _state_machine.update();
    _motor.update();

    if (_prev_state == STATE_GRINDING && _status.state == STATE_IDLE && _status.error_code == 0) {
        // grind finished cleanly - bump wear counter and persist the target
        // weight in case the user dialed in a new one this run
        _calibration.wear_counter++;
        _calibration.target_weight_g = _status.target_weight_g;
        _storage.save(_calibration);
    }
    _prev_state = _status.state;
}

void GrinderController::update() {
    uint32_t now = millis();

    readSensors(now);
    readButtons(now);
    readEncoder();
    runStateMachine(now);
    _display.update(_status); // internally rate-limited to ~30Hz
}
