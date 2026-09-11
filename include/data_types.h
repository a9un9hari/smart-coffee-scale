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
// CALIBRATION DATA (persisted to EEPROM)
// ============================================================

struct CalibrationData {
    long offset;             // Raw ADC reading at zero load
    float scale_factor;      // grams per ADC count
    float target_weight_g;   // last-used grind profile (single profile, MVP)
    uint32_t wear_counter;   // grinder maintenance tracking
    uint16_t checksum;       // CRC16 for validity check - must stay last
};

#endif // DATA_TYPES_H
