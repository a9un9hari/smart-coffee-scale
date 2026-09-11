#include "control.h"
#include <string.h>
#include <math.h>

GrinderController::GrinderController()
    : _scale(PIN_HX711_DOUT, PIN_HX711_CLK) {}

static void resetCupProfiles(CalibrationData &cal) {
    for (uint8_t i = 0; i < CUP_PROFILE_COUNT; i++) {
        memset(cal.cup_profiles[i].name, 0, sizeof(cal.cup_profiles[i].name));
        cal.cup_profiles[i].cup_weight_g = -1.0f; // sentinel: no profile configured, never matches a real reading
        cal.cup_profiles[i].tolerance_g = 0.0f;
    }
    cal.active_cup_profile_id = 0;
}

void GrinderController::begin() {
    _scale.begin();
    _motor.begin();
    _storage.begin();
    _ble.begin();

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
        resetCupProfiles(_calibration);
        _storage.save(_calibration);
    }

    _ble.attachCalibration(&_calibration);

    _status.target_weight_g = _calibration.target_weight_g;
    _status.current_weight_g = 0.0f;
    _status.motor_running = false;

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

void GrinderController::readCupDetect(uint32_t now) {
    if (_status.mode != MODE_GRINDER || _status.state != STATE_IDLE) {
        _cup_settling = false;
        return;
    }

    const CupProfile &profile = _calibration.cup_profiles[_calibration.active_cup_profile_id];
    if (profile.cup_weight_g < 0.0f) {
        return; // no profile configured for this slot
    }

    float diff = fabsf(_status.current_weight_g - profile.cup_weight_g);
    if (diff > profile.tolerance_g) {
        _cup_settling = false;
        return;
    }

    if (!_cup_settling) {
        _cup_settling = true;
        _cup_settle_start_ms = now;
    } else if (now - _cup_settle_start_ms >= CUP_DETECT_SETTLE_MS) {
        _state_machine.onEvent(EVT_CUP_DETECTED);
        _cup_settling = false;
    }
}

void GrinderController::processBleCommands() {
    BleCommand cmd;
    while (_ble.popCommand(cmd)) {
        switch (cmd.opcode) {
            case BLE_OP_SET_TARGET_WEIGHT:
                _status.target_weight_g = cmd.value_a;
                break;

            case BLE_OP_SET_MODE:
                _state_machine.onModeCommand((SystemMode)cmd.mode);
                break;

            case BLE_OP_START:
                _state_machine.onEvent(EVT_BLE_START);
                break;

            case BLE_OP_STOP:
                _state_machine.onEvent(EVT_BLE_STOP);
                break;

            case BLE_OP_EMERGENCY_STOP:
                _state_machine.onEvent(EVT_EMERGENCY_STOP);
                break;

            case BLE_OP_SELECT_CUP_PROFILE:
                if (cmd.id < CUP_PROFILE_COUNT) {
                    _calibration.active_cup_profile_id = cmd.id;
                    _storage.save(_calibration);
                }
                break;

            case BLE_OP_SET_CUP_PROFILE_WEIGHT:
                if (cmd.id < CUP_PROFILE_COUNT) {
                    _calibration.cup_profiles[cmd.id].cup_weight_g = cmd.value_a;
                    _calibration.cup_profiles[cmd.id].tolerance_g = cmd.value_b;
                    _storage.save(_calibration);
                }
                break;

            case BLE_OP_SET_CUP_PROFILE_NAME:
                if (cmd.id < CUP_PROFILE_COUNT) {
                    strncpy(_calibration.cup_profiles[cmd.id].name, cmd.name,
                            sizeof(_calibration.cup_profiles[cmd.id].name) - 1);
                    _calibration.cup_profiles[cmd.id]
                        .name[sizeof(_calibration.cup_profiles[cmd.id].name) - 1] = '\0';
                    _storage.save(_calibration);
                }
                break;

            default:
                break;
        }
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
        // weight in case the app dialed in a new one this run
        _calibration.wear_counter++;
        _calibration.target_weight_g = _status.target_weight_g;
        _storage.save(_calibration);
    }
    _prev_state = _status.state;
}

void GrinderController::update() {
    uint32_t now = millis();

    readSensors(now);
    readCupDetect(now);
    processBleCommands();
    runStateMachine(now);
    _ble.notifyStatus(_status, _calibration.active_cup_profile_id); // internally rate-limited
}
