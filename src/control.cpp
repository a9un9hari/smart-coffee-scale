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
    _ota.begin();
    _ble.attachOta(&_ota);
    _ota.setStatusCallback([this]() { _ble.notifyOtaStatus(); });

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

    float raw_weight = _scale.readWeight();
    // Reflects only the latest read, not "sticky" - a transient HX711 timeout
    // (routine, see docs/DEVELOPMENT-LOG.md's HX711 entry) shouldn't pin this
    // at a non-zero value forever once reads start succeeding again. Not
    // automatically fatal either way - the state machine only escalates via
    // EVT_ERROR_OCCURRED where relevant.
    _status.error_code = (_scale.getStatus() == HX711_OK) ? 0 : 2; // see hx711.h HX711Status for the underlying cause

    if (!_filter_initialized) {
        _filtered_weight_g = raw_weight;
        _filter_initialized = true;
    } else {
        _filtered_weight_g += _smoothing_alpha * (raw_weight - _filtered_weight_g);
    }
    _status.current_weight_g = _filtered_weight_g;

#if DEBUG_HX711_RAW
    if (now - _last_debug_print_ms >= DEBUG_HX711_RAW_INTERVAL_MS) {
        _last_debug_print_ms = now;
        Serial.printf(
            "[HX711] raw=%ld offset=%ld delta=%ld factor=%.6f raw_g=%.2f filtered_g=%.2f status=%d\n",
            _scale.getLastRawAvg(), _scale.getOffset(),
            _scale.getLastRawAvg() - _scale.getOffset(), _scale.getScaleFactor(),
            raw_weight, _filtered_weight_g, (int)_scale.getStatus());
    }
#endif
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

            case BLE_OP_TARE:
                handleTare();
                break;

            case BLE_OP_CAL_CLEAR:
                _cal_point_count = 0;
                _ble.notifyCalibrationStatus(0, false, 0.0f, 0.0f);
                break;

            case BLE_OP_CAL_ADD_POINT:
                handleCalAddPoint(cmd.value_a);
                break;

            case BLE_OP_CAL_SAVE:
                handleCalSave();
                break;

            case BLE_OP_SET_SMOOTHING_ALPHA: {
                float alpha = cmd.value_a;
                if (alpha < 0.05f) alpha = 0.05f;
                if (alpha > 0.9f) alpha = 0.9f;
                _smoothing_alpha = alpha;
                break;
            }

            case BLE_OP_OTA_START:
                Serial.println("[OTA] BLE_OP_OTA_START received");
                // Refuse while the motor could be running - OtaManager's WiFi
                // update is a blocking call once it starts, and would stall
                // MOTOR_MAX_RUNTIME_MS's safety cutoff if the grinder were mid-shot.
                if (_status.state == STATE_IDLE || _status.state == STATE_ESPRESSO_IDLE) {
                    _ota.start();
                } else {
                    Serial.printf("[OTA] start refused - state=%d not idle\n", (int)_status.state);
                }
                break;

            case BLE_OP_OTA_CANCEL:
                Serial.println("[OTA] BLE_OP_OTA_CANCEL received");
                _ota.cancel();
                break;

            default:
                break;
        }
    }
}

void GrinderController::handleTare() {
    _scale.tare();
    _calibration.offset = _scale.getOffset();
    _storage.save(_calibration);
    _filter_initialized = false; // snap the smoothed reading to the new zero instead of easing into it
}

long GrinderController::sampleRawAveraged(uint8_t samples) {
    long sum = 0;
    uint8_t got = 0;
    for (uint8_t i = 0; i < samples; i++) {
        long r = _scale.readRaw();
        if (_scale.getStatus() == HX711_OK) {
            sum += r;
            got++;
        }
    }
    return (got > 0) ? (sum / got) : 0;
}

void GrinderController::handleCalAddPoint(float known_weight_g) {
    float raw = 0.0f;
    if (_cal_point_count < MAX_CAL_POINTS) {
        raw = (float)sampleRawAveraged(10);
        _cal_points[_cal_point_count] = {raw, known_weight_g};
        _cal_point_count++;
    }
    _ble.notifyCalibrationStatus(_cal_point_count, false, raw, known_weight_g);
}

void GrinderController::handleCalSave() {
    if (_cal_point_count < 2) {
        _ble.notifyCalibrationStatus(_cal_point_count, false, 0.0f, 0.0f);
        return;
    }

    // Least-squares line fit: weight_g = slope*raw + intercept, over the
    // captured (raw, weight_g) points. scale_factor = slope; offset is the
    // raw value where the fitted line crosses weight=0 (-intercept/slope) -
    // matches HX711::readWeight()'s (raw - offset) * scale_factor formula.
    double sum_x = 0, sum_y = 0, sum_xy = 0, sum_xx = 0;
    for (uint8_t i = 0; i < _cal_point_count; i++) {
        double x = _cal_points[i].raw;
        double y = _cal_points[i].weight_g;
        sum_x += x;
        sum_y += y;
        sum_xy += x * y;
        sum_xx += x * x;
    }

    double n = _cal_point_count;
    double denom = n * sum_xx - sum_x * sum_x;
    if (fabs(denom) < 1e-9) {
        _ble.notifyCalibrationStatus(_cal_point_count, false, 0.0f, 0.0f); // degenerate: all points at the same raw value
        return;
    }

    double slope = (n * sum_xy - sum_x * sum_y) / denom;
    double intercept = (sum_y - slope * sum_x) / n;
    if (fabs(slope) < 1e-12) {
        _ble.notifyCalibrationStatus(_cal_point_count, false, 0.0f, 0.0f);
        return;
    }

    float new_scale_factor = (float)slope;
    long new_offset = lround(-intercept / slope);

    _scale.setCalibrationFactor(new_scale_factor);
    _scale.setOffset(new_offset);
    _calibration.scale_factor = new_scale_factor;
    _calibration.offset = new_offset;
    _storage.save(_calibration);
    _filter_initialized = false; // snap to the newly-calibrated reading instead of easing into it

    _cal_point_count = 0;
    _ble.notifyCalibrationStatus(0, true, 0.0f, 0.0f);
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
    _ota.update(); // no-op unless an OTA_START was accepted; blocks this loop only mid-flash
    _ble.notifyStatus(_status, _calibration.active_cup_profile_id); // internally rate-limited
    _ble.notifyOtaStatus(); // internally rate-limited
}
