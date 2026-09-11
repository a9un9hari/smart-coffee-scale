#ifndef CONFIG_H
#define CONFIG_H

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

// Display: 1.3" ST7789VW 240x240 SPI, confirmed from the module's printed
// spec sheet. (The docs/Reference Guide claimed OLED I2C - that was wrong;
// this is genuinely the ST7789 the original prompt framework assumed.)
// Module exposes 7 pins (VCC/GND/SCK/SDA/RES/DC/BLK) - no CS pin, it's tied
// to GND internally, hence TFT_CS=-1 in platformio.ini. RES and BLK are
// wired straight to 3.3V (no GPIO needed) - TFT_RST=-1 tells TFT_eSPI to
// skip hardware reset and use a software reset command instead, and
// backlight just stays always-on.
#define PIN_DISPLAY_SCLK    8
#define PIN_DISPLAY_MOSI    10
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
