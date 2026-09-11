#ifndef CONFIG_H
#define CONFIG_H

// ============================================================
// PIN DEFINITIONS - ESP32-C3 Super Mini
// (Available GPIO: 0-10, 13-15, 21)
// ============================================================

// HX711 Load Cell ADC
#define PIN_HX711_DOUT      0
#define PIN_HX711_CLK       1

// Motor control (SSR-40 DA relay)
#define PIN_MOTOR_SSR       2

// Buttons
#define PIN_BUTTON_START    3
#define PIN_BUTTON_MODE     4

// Rotary encoder (KY-040)
#define PIN_ENCODER_CLK     5
#define PIN_ENCODER_DT      6
#define PIN_ENCODER_SW      7

// Display (ST7789 SPI) - Phase 2
#define PIN_DISPLAY_SCLK    14
#define PIN_DISPLAY_MOSI    13
#define PIN_DISPLAY_CS      15
#define PIN_DISPLAY_DC      21

// ============================================================
// SENSOR CONSTANTS
// ============================================================

#define LOAD_CELL_CAPACITY_G    2000.0f   // 2kg load cell
#define HX711_GAIN              128       // Channel A, gain 128
#define HX711_READ_INTERVAL_MS  100       // 10Hz sampling
#define HX711_AVG_SAMPLES       5         // Moving average window
#define HX711_TIMEOUT_MS        10        // Bit-read timeout

// ============================================================
// TIMING CONSTANTS
// ============================================================

#define BUTTON_DEBOUNCE_MS      20
#define BUTTON_UPDATE_MS        10
#define STATE_MACHINE_UPDATE_MS 100
#define MOTOR_MAX_RUNTIME_MS    30000    // 30s safety timeout
#define ESPRESSO_STABLE_MS      3000     // weight stable = shot done

// ============================================================
// SYSTEM STATE MACHINE
// ============================================================

enum SystemMode {
    MODE_GRINDER,
    MODE_ESPRESSO
};

enum SystemState {
    STATE_IDLE,
    STATE_SELECT_WEIGHT,
    STATE_UPDATE_WEIGHT,
    STATE_GRINDING,
    STATE_ESPRESSO_IDLE,
    STATE_PULL_SHOT,
    STATE_PULLING,
    STATE_SHOT_COMPLETE,
    STATE_ERROR
};

#endif // CONFIG_H
