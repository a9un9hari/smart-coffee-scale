#ifndef DATA_TYPES_H
#define DATA_TYPES_H

#include <stdint.h>
#include "config.h"

// ============================================================
// SENSOR READING
// ============================================================

struct SensorReading {
    float weight_g;         // Filtered weight in grams
    uint32_t timestamp_ms;  // millis() at read time
    bool valid;             // false if HX711 read failed/timed out
};

// ============================================================
// SYSTEM STATE
// ============================================================

struct SystemStatus {
    SystemMode mode;
    SystemState state;
    float target_weight_g;
    float current_weight_g;
    bool motor_running;
    uint32_t state_entered_ms;
    uint8_t error_code;      // 0 = OK
};

// ============================================================
// CUP PROFILE (dosing cup auto-detect, set from the Android app)
// ============================================================

#define CUP_PROFILE_COUNT 4

struct CupProfile {
    char name[12];        // not null-terminated if exactly 12 bytes used - always treat with explicit length
    float cup_weight_g;   // empty cup weight to watch for
    float tolerance_g;    // +/- match window
};

// ============================================================
// CALIBRATION DATA (persisted to EEPROM)
// ============================================================

struct CalibrationData {
    long offset;             // Raw ADC reading at zero load
    float scale_factor;      // grams per ADC count
    float target_weight_g;   // last-used grind dose
    uint32_t wear_counter;   // grinder maintenance tracking
    CupProfile cup_profiles[CUP_PROFILE_COUNT];
    uint8_t active_cup_profile_id;
    uint16_t checksum;       // CRC16 for validity check - must stay last
};

// ============================================================
// OVERSHOOT LEARNING (persisted to EEPROM, separate slot from
// CalibrationData - see storage.h/.cpp OvershootStorage)
// ============================================================

// EMA-smoothed (actual delivered weight - target weight) per cup profile
// slot, in grams - learned from completed grinds so GRINDING can stop
// this many grams early next time to compensate for the grounds that keep
// falling after the motor is commanded off (momentum/gravity in the
// chute). 0 = no learning yet for that slot (falls back to today's
// stop-exactly-at-target behavior). Indexed by CalibrationData's own
// active_cup_profile_id, so each dosing cup slot learns its own
// correction independently - kept in a separate struct/EEPROM slot
// (rather than added to CalibrationData) specifically so this can evolve
// without risking the CRC-invalidation-wipes-everything gotcha that a
// CalibrationData layout change would trigger on already-provisioned
// boards (see project_relay_module memory equivalent for calibration:
// docs/DEVELOPMENT-LOG.md's HX711 entry / Storage class comments).
struct OvershootData {
    float learned_overshoot_g[CUP_PROFILE_COUNT];
    uint16_t checksum; // CRC16 for validity check - must stay last
};

// ============================================================
// DEVICE PREFERENCES (persisted to EEPROM, own slot after
// OvershootData - see storage.h/.cpp PrefsStorage)
// ============================================================

// Settings that must survive a power cycle without the app connected
// (phone-less grinding). Kept out of CalibrationData for the same
// CRC-invalidation reason as OvershootData above.
struct PrefsData {
    uint8_t weight_source; // WeightSource
    uint16_t checksum;     // CRC16 for validity check - must stay last
};

#endif // DATA_TYPES_H
