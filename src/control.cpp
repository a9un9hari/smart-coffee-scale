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
    _timemore.begin(); // starts scanning immediately - fine if no Timemore Dot is around, update() just keeps retrying
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

    if (!_overshoot_storage.restore(_overshoot)) {
        for (uint8_t i = 0; i < CUP_PROFILE_COUNT; i++) {
            _overshoot.learned_overshoot_g[i] = 0.0f; // no learning yet - stop exactly at target, same as before this feature
        }
        _overshoot_storage.save(_overshoot);
    }

    if (_prefs_storage.restore(_prefs) &&
        (_prefs.weight_source == WEIGHT_SOURCE_HX711 || _prefs.weight_source == WEIGHT_SOURCE_TIMEMORE)) {
        _weight_source = (WeightSource)_prefs.weight_source;
    } else {
        _prefs.weight_source = WEIGHT_SOURCE_TIMEMORE; // first boot with this record - Timemore Dot is the primary scale
        _prefs_storage.save(_prefs);
        _weight_source = WEIGHT_SOURCE_TIMEMORE;
    }
    Serial.printf("[PREFS] weight_source=%d\n", (int)_weight_source);

    _status.target_weight_g = _calibration.target_weight_g;
    _status.current_weight_g = 0.0f;
    _status.motor_running = false;

    _grind_log.begin();
    _grind_log.setLiveSink([this](const char *line) { _ble.notifyGrindLogLine(line); });
    _display.begin(); // optional module - no-op everywhere if it isn't wired up

    _state_machine.init(&_status); // also sets mode=GRINDER, state=IDLE, error_code=0
    _state_machine.attachMotor(&_motor);
    _prev_state = _status.state;
}

void GrinderController::readSensors(uint32_t now) {
    _timemore.update(); // paces its own scan/reconnect - safe to call every loop, not gated by HX711_READ_INTERVAL_MS

    if (now - _last_sensor_read_ms < HX711_READ_INTERVAL_MS) {
        return;
    }
    _last_sensor_read_ms = now;

    // HX711 is always sampled regardless of the active source - keeps tare/
    // calibration state live so switching sources mid-session doesn't hand
    // back a stale reading, and it's the only source local Tare/Calibrate
    // BLE ops touch.
    float hx711_weight = _scale.readWeight();
    _hx711_detected = (_scale.getStatus() != HX711_DISCONNECTED);

    float raw_weight;
    if (_weight_source == WEIGHT_SOURCE_TIMEMORE) {
        raw_weight = _timemore.getWeightG();
        // 3 = selected weight source (Timemore Dot) not connected - distinct
        // from HX711's own error codes (1/2) so the app can tell them apart.
        // Not "sticky" either, same reasoning as the HX711 case below - once
        // reconnected this clears on its own.
        _status.error_code = _timemore.isConnected() ? 0 : 3;
    } else {
        raw_weight = hx711_weight;
        // Reflects only the latest read, not "sticky" - a transient HX711 timeout
        // (routine, see docs/DEVELOPMENT-LOG.md's HX711 entry) shouldn't pin this
        // at a non-zero value forever once reads start succeeding again. Not
        // automatically fatal either way - the state machine only escalates via
        // EVT_ERROR_OCCURRED where relevant.
        _status.error_code = (_scale.getStatus() == HX711_OK) ? 0 : 2; // see hx711.h HX711Status for the underlying cause
    }

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

    if (!_cup_settling || fabsf(_status.current_weight_g - _cup_settle_ref_g) > CUP_DETECT_STABLE_BAND_G) {
        // (re)start the hold - weight still moving, see CUP_DETECT_STABLE_BAND_G
        _cup_settling = true;
        _cup_settle_start_ms = now;
        _cup_settle_ref_g = _status.current_weight_g;
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
                Serial.printf("[CTRL] BLE_OP_SET_TARGET_WEIGHT requested=%.2f (was %.2f)\n", cmd.value_a, _status.target_weight_g);
                _status.target_weight_g = cmd.value_a;
                break;

            case BLE_OP_SET_MODE:
                Serial.printf("[MODE] SET_MODE requested=%d current_mode=%d current_state=%d\n",
                    (int)cmd.mode, (int)_status.mode, (int)_status.state);
                _state_machine.onModeCommand((SystemMode)cmd.mode);
                Serial.printf("[MODE] after: mode=%d state=%d\n", (int)_status.mode, (int)_status.state);
                break;

            case BLE_OP_START:
                Serial.printf("[CTRL] BLE_OP_START mode=%d state=%d\n", (int)_status.mode, (int)_status.state);
                _state_machine.onEvent(EVT_BLE_START);
                break;

            case BLE_OP_STOP:
                if (_grind_log.isSessionOpen()) {
                    _grind_log.finish(_status.current_weight_g - _state_machine.getSessionStartWeightG(), "user_stop");
                }
                _grind_stopped_by_user = true;
                _overshoot_eval_pending = false; // cancels a pending learn/top-up check too
                _state_machine.onEvent(EVT_BLE_STOP);
                break;

            case BLE_OP_EMERGENCY_STOP:
                if (_grind_log.isSessionOpen()) {
                    _grind_log.finish(_status.current_weight_g - _state_machine.getSessionStartWeightG(), "user_stop");
                }
                _grind_stopped_by_user = true;
                _overshoot_eval_pending = false;
                _state_machine.onEvent(EVT_EMERGENCY_STOP);
                break;

            case BLE_OP_SET_WEIGHT_SOURCE:
                Serial.printf("[CTRL] BLE_OP_SET_WEIGHT_SOURCE requested=%d\n", (int)cmd.mode);
                if (cmd.mode == WEIGHT_SOURCE_HX711 || cmd.mode == WEIGHT_SOURCE_TIMEMORE) {
                    _weight_source = (WeightSource)cmd.mode;
                    _filter_initialized = false; // snap to the new source's reading instead of blending across scales
                    if (_prefs.weight_source != cmd.mode) { // only touch flash on a real change
                        _prefs.weight_source = cmd.mode;
                        _prefs_storage.save(_prefs);
                    }
                }
                break;

            case BLE_OP_SET_TIMEMORE_AUTOCONNECT:
                Serial.printf("[CTRL] BLE_OP_SET_TIMEMORE_AUTOCONNECT requested=%d\n", (int)cmd.id);
                _timemore.setEnabled(cmd.id != 0);
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
    if (_weight_source == WEIGHT_SOURCE_TIMEMORE) {
        _timemore.tare(); // scale zeroes itself over BLE - nothing local to persist
    } else {
        _scale.tare();
        _calibration.offset = _scale.getOffset();
        _storage.save(_calibration);
    }
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

    _state_machine.setStopOffsetG(_overshoot.learned_overshoot_g[_calibration.active_cup_profile_id]);

    if (_prev_state != STATE_GRINDING && _status.state == STATE_GRINDING && !_state_machine.isTopUpPulseActive()) {
        _topup_pulses_done = 0;
        _grind_stopped_by_user = false;
        if (_grind_log.isSessionOpen()) {
            // previous session's settle/top-up never got to finish (new
            // cup placed or Start tapped during SETTLING)
            _grind_log.finish(_status.current_weight_g - _overshoot_eval_session_start_weight_g, "interrupted");
        }
        _grind_log.startSession(_calibration.active_cup_profile_id, _status.target_weight_g,
                                _overshoot.learned_overshoot_g[_calibration.active_cup_profile_id]);
        Serial.printf("[OVERSHOOT] grind starting: profile=%d target=%.2fg using learned_offset=%.2fg\n",
                      _calibration.active_cup_profile_id, _status.target_weight_g,
                      _overshoot.learned_overshoot_g[_calibration.active_cup_profile_id]);
    }

    _state_machine.update();
    _motor.update();

    if (_prev_state == STATE_GRINDING && _status.state == STATE_IDLE && _status.error_code == 0 &&
        _state_machine.lastGrindWasTopUpPulse()) {
        // a top-up pulse just ended - settle again, then re-check (no
        // learning, no wear/target bookkeeping: same session as before)
        if (!_grind_stopped_by_user) {
            _overshoot_eval_pending = true;
            _overshoot_eval_after_pulse = true;
            _overshoot_eval_start_ms = now;
        }
    } else if (_prev_state == STATE_GRINDING && _status.state == STATE_IDLE && _status.error_code == 0) {
        // grind finished cleanly - bump wear counter and persist the target
        // weight in case the app dialed in a new one this run
        _calibration.wear_counter++;
        _calibration.target_weight_g = _status.target_weight_g;
        _storage.save(_calibration);

        // Don't sample current_weight_g yet - it's essentially just the
        // stop-threshold value that triggered this very transition, not
        // the true final delivered weight. Capture what's already fixed
        // now (won't change even if another grind starts before the
        // settle window elapses) and sample the weight itself later.
        _overshoot_eval_start_ms = now;
        _overshoot_eval_session_start_weight_g = _state_machine.getSessionStartWeightG();
        _overshoot_eval_target_weight_g = _status.target_weight_g;
        _overshoot_eval_profile_id = _calibration.active_cup_profile_id;
        _overshoot_eval_after_pulse = false;
        _overshoot_eval_pending = !_grind_stopped_by_user; // a deliberate stop is neither learned from nor topped up
    }

    if (_overshoot_eval_pending && now - _overshoot_eval_start_ms >= OVERSHOOT_SETTLE_MS) {
        _overshoot_eval_pending = false;
        if (_status.state == STATE_IDLE) { // still idle - no new grind started meanwhile, safe to sample
            if (!_overshoot_eval_after_pulse) {
                updateOvershootLearning(_overshoot_eval_session_start_weight_g,
                                         _overshoot_eval_target_weight_g,
                                         _overshoot_eval_profile_id);
                _grind_log.setMainResult(_status.current_weight_g - _overshoot_eval_session_start_weight_g,
                                         _overshoot.learned_overshoot_g[_overshoot_eval_profile_id]);
            }
            maybeStartTopUp(_overshoot_eval_session_start_weight_g, _overshoot_eval_target_weight_g);
        }
    }

    if (_status.state == STATE_ERROR && _grind_log.isSessionOpen()) {
        _grind_log.finish(_status.current_weight_g - _state_machine.getSessionStartWeightG(), "error");
    }

    _prev_state = _status.state;
}

// Called ~OVERSHOOT_SETTLE_MS after a grind session ends in STATE_IDLE
// with no error, once current_weight_g has had time to settle to the true
// final delivered weight - updates the given cup profile's learned
// overshoot correction (EMA of delivered-minus-target grams) so the next
// grind on this profile stops that many grams earlier. Skipped if the
// grind looks like it was manually aborted (BLE_OP_STOP) rather than
// completed near its target - learning from a partial/aborted grind would
// corrupt the correction.
void GrinderController::updateOvershootLearning(float session_start_weight_g, float target_weight_g, uint8_t profile_id) {
    float delivered_g = _status.current_weight_g - session_start_weight_g;
    if (delivered_g < target_weight_g * 0.8f) {
        Serial.printf("[OVERSHOOT] skipped (looks aborted): delivered=%.2fg target=%.2fg\n",
                      delivered_g, target_weight_g);
        return; // looks aborted early, not a natural target-reached completion - don't learn from it
    }

    // actual_overshoot_g is the RESIDUAL error after already-applied
    // correction (this grind stopped at target-learned, then drifted this
    // much further) - it is NOT a fresh, uncorrected overshoot sample.
    // Confirmed on the bench 2026-09-25: `learned += alpha*(actual-learned)`
    // (treating it like an EMA of independent raw samples) has a fixed
    // point at learned == true_overshoot/2, not true_overshoot - it
    // converges to only ever cancelling HALF the real overshoot, because
    // it re-subtracts the already-applied correction a second time. The
    // fix is a plain integral update: nudge learned directly by the
    // residual itself, which has its fixed point at the residual reaching
    // zero (learned == true_overshoot) - verified against real grind
    // data, see project_relay_module memory equivalent / DEVELOPMENT-LOG.
    float actual_overshoot_g = delivered_g - target_weight_g;
    float &learned = _overshoot.learned_overshoot_g[profile_id];
    float before = learned;
    learned += OVERSHOOT_EMA_ALPHA * actual_overshoot_g;
    if (learned < OVERSHOOT_CLAMP_MIN_G) learned = OVERSHOOT_CLAMP_MIN_G;
    if (learned > OVERSHOOT_CLAMP_MAX_G) learned = OVERSHOOT_CLAMP_MAX_G;

    Serial.printf("[OVERSHOOT] profile=%d delivered=%.2fg target=%.2fg actual_overshoot=%.2fg learned %.2fg -> %.2fg\n",
                  profile_id, delivered_g, target_weight_g, actual_overshoot_g, before, learned);

    _overshoot_storage.save(_overshoot);
}

// Called at each post-grind settle point (after the main grind, then after
// every pulse). Starts one more short motor pulse if the settled dose is
// still meaningfully short - see TOPUP_* in config.h.
// Also closes the session's GrindLog row whenever no further pulse follows.
void GrinderController::maybeStartTopUp(float session_start_weight_g, float target_weight_g) {
    float delivered_g = _status.current_weight_g - session_start_weight_g;
    float shortfall_g = target_weight_g - delivered_g;

    if (!TOPUP_ENABLED || shortfall_g <= TOPUP_THRESHOLD_G) {
        if (_topup_pulses_done > 0) {
            Serial.printf("[TOPUP] done after %d pulse(s): delivered=%.2fg target=%.2fg\n",
                          _topup_pulses_done, delivered_g, target_weight_g);
        }
        _grind_log.finish(delivered_g, "ok");
        return;
    }
    if (delivered_g < target_weight_g * 0.8f) {
        Serial.printf("[TOPUP] skipped: delivered=%.2fg looks like cup removed/aborted\n", delivered_g);
        _grind_log.finish(delivered_g, "cup_removed");
        return;
    }
    if (_weight_source == WEIGHT_SOURCE_TIMEMORE && !_timemore.isConnected()) {
        Serial.println("[TOPUP] skipped: Timemore disconnected, weight reading can't be trusted");
        _grind_log.finish(delivered_g, "no_dot");
        return;
    }
    if (_topup_pulses_done >= TOPUP_MAX_PULSES) {
        Serial.printf("[TOPUP] gave up after %d pulses: still %.2fg short\n", _topup_pulses_done, shortfall_g);
        _grind_log.finish(delivered_g, "gave_up");
        return;
    }

    if (_state_machine.startTopUpPulse(TOPUP_PULSE_MS)) {
        _topup_pulses_done++;
        _grind_log.addPulse(shortfall_g);
        Serial.printf("[TOPUP] pulse %d/%d: delivered=%.2fg target=%.2fg short=%.2fg\n",
                      _topup_pulses_done, TOPUP_MAX_PULSES, delivered_g, target_weight_g, shortfall_g);
    } else {
        _grind_log.finish(delivered_g, "interrupted");
    }
}

// Single-character USB serial commands for pulling the on-flash grind log
// once a laptop is attached again: 'd' dump, 'X' clear (capital on purpose).
void GrinderController::processSerialCommands() {
    while (Serial.available() > 0) {
        char c = (char)Serial.read();
        if (c == 'd') {
            _grind_log.dump(Serial);
        } else if (c == 'X') {
            _grind_log.clear();
        }
    }
}

void GrinderController::update() {
    uint32_t now = millis();

    processSerialCommands();
    readSensors(now);
    readCupDetect(now);
    processBleCommands();
    runStateMachine(now);
    char phase[12] = "";
    if (_state_machine.isTopUpPulseActive()) {
        snprintf(phase, sizeof(phase), "TOP-UP %d/%d", _topup_pulses_done, TOPUP_MAX_PULSES);
    } else if (_overshoot_eval_pending && _status.state == STATE_IDLE) {
        snprintf(phase, sizeof(phase), "SETTLING");
    }
    _display.update(now, _status, phase, _state_machine.getSessionStartWeightG(),
                    _calibration.cup_profiles[_calibration.active_cup_profile_id],
                    _calibration.active_cup_profile_id,
                    _weight_source, _timemore.isConnected()); // rate-limited, after the stop check on purpose
    _ota.update(); // no-op unless an OTA_START was accepted; blocks this loop only mid-flash
    _ble.notifyStatus(_status, _calibration.active_cup_profile_id,
                       (uint8_t)_weight_source, _timemore.isConnected(),
                       _timemore.isEnabled(), _hx711_detected); // internally rate-limited
    _ble.notifyOtaStatus(); // internally rate-limited

    uint32_t after_boot, after_uptime_s;
    if (_ble.takeGrindLogRequest(after_boot, after_uptime_s)) {
        _grind_log.startSync(after_boot, after_uptime_s);
    }
    // Flash reads + notifies only while nothing time-sensitive is running.
    bool busy = _status.state == STATE_GRINDING || _status.state == STATE_PULL_SHOT ||
                _status.state == STATE_PULLING || _overshoot_eval_pending;
    if (!busy) {
        _grind_log.serviceSync(now, [this](const char *line) { _ble.notifyGrindLogLine(line); });
    }
}
