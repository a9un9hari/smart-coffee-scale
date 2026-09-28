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
#include "timemore_scale.h"
#include "display.h"
#include "grind_log.h"

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
    TimemoreScale _timemore;
    Display _display;
    GrindLog _grind_log;

    // Which sensor feeds _status.current_weight_g - app-selectable via
    // BLE_OP_SET_WEIGHT_SOURCE, persisted in _prefs so phone-less grinding
    // keeps using the same scale after a power cycle. Overwritten from
    // EEPROM in begin().
    WeightSource _weight_source = WEIGHT_SOURCE_TIMEMORE;

    PrefsStorage _prefs_storage;
    PrefsData _prefs;

    // Tracks whether the HX711 is physically wired up, independent of
    // _weight_source - the app uses this to hide HX711-only UI (manual
    // calibration, corner-consistency check) when the load cell isn't
    // there, e.g. while running a Timemore-Dot-only test rig. Updated every
    // readSensors() from HX711::getStatus() (HX711_DISCONNECTED means DOUT
    // never toggled - a physically-absent sensor, not a transient misread).
    bool _hx711_detected = true;

    SystemStatus _status;
    CalibrationData _calibration;

    OvershootStorage _overshoot_storage;
    OvershootData _overshoot; // learned per-cup-profile stop-early correction, see data_types.h

    // GRINDING->IDLE fires the instant current_weight_g first crosses the
    // stop threshold - current_weight_g at that exact tick is essentially
    // just the threshold value itself, not the true final delivered
    // weight (grounds still falling + the scale's own reporting lag,
    // worse over BLE with Timemore Dot, both need a moment to catch up).
    // So overshoot learning doesn't sample immediately: it captures the
    // session's start weight/target/profile right at the transition, then
    // waits OVERSHOOT_SETTLE_MS and reads current_weight_g *then* for the
    // actual learning update - see updateOvershootLearning().
    bool _overshoot_eval_pending = false;
    uint32_t _overshoot_eval_start_ms = 0;
    float _overshoot_eval_session_start_weight_g = 0.0f;
    float _overshoot_eval_target_weight_g = 0.0f;
    uint8_t _overshoot_eval_profile_id = 0;
    bool _overshoot_eval_after_pulse = false; // settle check after a top-up pulse: top-up only, no learning

    // Top-up sequence for the current grind session (see TOPUP_* in config.h).
    uint8_t _topup_pulses_done = 0;
    // Set by an app Stop/E-Stop during a session - a deliberate stop must
    // never be "topped up", nor learned from as if it were a completion.
    bool _grind_stopped_by_user = false;

    uint32_t _last_sensor_read_ms = 0;
    uint32_t _last_state_update_ms = 0;
    SystemState _prev_state = STATE_IDLE; // to detect GRINDING -> IDLE (grind completed)

    bool _cup_settling = false;
    uint32_t _cup_settle_start_ms = 0;
    float _cup_settle_ref_g = 0.0f;

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
    void processSerialCommands();
    void runStateMachine(uint32_t now);
    void updateOvershootLearning(float session_start_weight_g, float target_weight_g, uint8_t profile_id);
    void maybeStartTopUp(float session_start_weight_g, float target_weight_g);

    long sampleRawAveraged(uint8_t samples);
    void handleTare();
    void handleCalAddPoint(float known_weight_g);
    void handleCalSave();
};

#endif // CONTROL_H
