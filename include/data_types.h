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

#endif // DATA_TYPES_H
