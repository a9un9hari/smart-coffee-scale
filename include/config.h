#ifndef CONFIG_H
#define CONFIG_H

#include <stdint.h>

// ============================================================
// PIN DEFINITIONS - ESP32-C3 Super Mini (QFN32, embedded flash)
//
// GPIO12-17 are wired internally to the embedded flash chip on this
// package - do NOT use them as GPIO, doing so corrupts flash access and
// crashes the chip (TG1WDT_SYS_RST bootloop, confirmed on hardware).
// GPIO18/19 are the native USB D-/D+ lines (in use since Serial runs over
// USB-CDC here). GPIO2/8/9 are boot strapping pins - usable post-boot but
// avoid loading them externally where possible.
// Usable: 0-10, 18-21 (18/19 reserved for USB as noted above)
//
// No physical input (buttons/encoder) - control is entirely over BLE from
// the companion Android app. Physical peripherals: HX711 load cell, the
// motor relay, and an optional read-only SSD1306 OLED status screen.
// ============================================================

// HX711 Load Cell ADC
#define PIN_HX711_DOUT      0
#define PIN_HX711_CLK       1

// Motor control (SSR-40 DA relay)
#define PIN_MOTOR_SSR       2

// SSD1306 0.96" 128x64 I2C OLED (optional - firmware runs without it).
// GPIO8 is a strapping pin with the onboard LED on it; fine for I2C.
#define PIN_OLED_SDA        10
#define PIN_OLED_SCL        8
#define OLED_I2C_ADDR       0x3C

// ============================================================
// SENSOR CONSTANTS
// ============================================================

#define LOAD_CELL_CAPACITY_G    2000.0f   // 2kg load cell
#define HX711_GAIN              128       // Channel A, gain 128
#define HX711_READ_INTERVAL_MS  100       // 10Hz sampling
#define HX711_AVG_SAMPLES       5         // Moving average window
#define HX711_TIMEOUT_MS        10        // Bit-read timeout
#define WEIGHT_SMOOTHING_ALPHA  0.35f     // extra EMA on top of HX711_AVG_SAMPLES; lower = smoother but more lag

// Prints raw ADC count / offset / computed weight over Serial (115200) a
// few times a second - for diagnosing weight jitter/drift (electrical vs
// mechanical) independent of the smoothing filter. Turn off once done.
#define DEBUG_HX711_RAW         1
#define DEBUG_HX711_RAW_INTERVAL_MS 250

// Prints every BLE device seen during a TimemoreScale scan (name + address),
// connect/handshake step results, and raw notify bytes - for diagnosing
// Timemore Dot connectivity issues. Off by default - noisy in a BLE-dense
// room (prints every nearby device's scan result). Flip to 1 if the Dot
// stops connecting/streaming weight again.
#define DEBUG_TIMEMORE_SCAN     0

// ============================================================
// TIMING CONSTANTS
// ============================================================

// Overshoot learning (see OvershootData in data_types.h): how fast the
// per-cup-profile learned correction adapts to each new completed grind.
// Higher = reacts faster to a grind-setting/bean change but noisier;
// lower = smoother but slower to catch up. Clamp bounds keep a single bad
// reading (jam, bump, static) from ever pushing the correction so far
// that a grind stops almost immediately or barely stops early at all.
// How long to wait after GRINDING->IDLE before sampling current_weight_g
// for the overshoot learning update - current_weight_g right at the
// transition is essentially just the stop-threshold value itself (that's
// what triggered the transition), not the true final delivered weight:
// grounds are still physically settling, and Timemore Dot's own BLE
// reporting lags a bit further behind reality than that. Long enough to
// let both catch up; short enough not to collide with a fast cup swap
// starting the next grind (see the STATE_IDLE re-check before sampling).
// 2026-09-25: was 800ms, confirmed too short on the bench via
// [OVERSHOOT-TRACE] checkpoints - Timemore Dot's reading kept climbing
// until ~1.5s after the motor stopped (200ms:4.15g, 500ms:4.54g,
// 800ms:4.92g, 1500ms:5.09g, 3000ms/5000ms:5.10g - stable only from
// ~1.5-2s on). 2000ms gives real margin past where it actually settles.
#define OVERSHOOT_SETTLE_MS       2000
#define OVERSHOOT_EMA_ALPHA       0.3f
#define OVERSHOOT_CLAMP_MIN_G     -2.0f
#define OVERSHOOT_CLAMP_MAX_G     5.0f
// Effective stop threshold (target - learned overshoot) is never allowed
// to drop below this fraction of the requested target - a safety floor
// against a corrupted/runaway learned value making GRINDING stop almost
// instantly.
#define OVERSHOOT_MIN_TARGET_FRACTION 0.5f

#define STATE_MACHINE_UPDATE_MS 100
#define MOTOR_MAX_RUNTIME_MS    30000    // 30s safety timeout
#define ESPRESSO_STABLE_MS      3000     // weight stable = shot done
#define CUP_DETECT_SETTLE_MS    400      // weight must hold near cup profile this long before auto-start
#define DISPLAY_REFRESH_MS      200      // OLED refresh (~5Hz) - each full refresh blocks ~25ms, see display.h
#define DISPLAY_SPLASH_MS       2000     // boot splash hold, non-blocking
#define DISPLAY_DONE_HOLD_MS    15000    // keep showing the net dose + "DONE" this long after a grind
#define BLE_NOTIFY_INTERVAL_MS  150      // status notify throttle (~6-7Hz)
#define OTA_NOTIFY_INTERVAL_MS  500      // OTA status notify throttle - slow-moving state, no need for 6-7Hz
#define OTA_WIFI_CONNECT_TIMEOUT_MS 15000 // give up and report OTA_ERROR_WIFI if WiFi doesn't associate in time

// ============================================================
// SYSTEM STATE MACHINE
// ============================================================

enum SystemMode {
    MODE_GRINDER,
    MODE_ESPRESSO
};

enum SystemState {
    STATE_IDLE,
    STATE_GRINDING,
    STATE_ESPRESSO_IDLE,
    STATE_PULL_SHOT,
    STATE_PULLING,
    STATE_SHOT_COMPLETE,
    STATE_ERROR
};

// Which sensor drives current_weight_g - app-selectable via
// BLE_OP_SET_WEIGHT_SOURCE and persisted to EEPROM (PrefsStorage), so a
// phone-less power cycle keeps grinding off the same scale.
enum WeightSource : uint8_t {
    WEIGHT_SOURCE_HX711    = 0,
    WEIGHT_SOURCE_TIMEMORE = 1
};

#endif // CONFIG_H
