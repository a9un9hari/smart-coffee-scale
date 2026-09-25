#include <Arduino.h>
#include "config.h"

// Standalone relay output test - zero application code, just toggles
// PIN_MOTOR_SSR (GPIO2) directly so the switching action can be checked
// with a multimeter across the relay's output terminals, before trusting
// it wired into the grinder's actual AC line.
//
// NOTE: PIN_MOTOR_SSR drives a mechanical relay module (COM/NO/NC
// contacts), not the SSR-40DA originally planned - the Mazzer Mini's tap
// switch turned out to close a low-voltage DC sense loop to the grinder's
// own control board, not the AC mains line directly, and a triac-based
// SSR's zero-cross trigger never fires on a steady DC signal (confirmed
// with a multimeter). See docs/DEVELOPMENT-LOG.md.
//
// 2026-09-24: after 3 premade relay modules all showed marginal/unreliable
// switching when driven directly from a 3.3V GPIO (their onboard driver
// transistors needed a threshold referenced closer to their own 5V rail
// than 3.3V logic reliably provides - see project_relay_module_active_high_candidate
// memory for the full saga), switched to a bare ZN3F-DC5V-C relay
// (docs/photos/kakirelay.png) driven through our own discrete NPN
// transistor (BC547) stage instead of relying on a premade board's driver:
//
//   GPIO2 --[1k]--> Base (BC547)
//   Emitter -> GND
//   Collector -> relay coil pin (either coil pin, no polarity) + diode
//                anode (flyback, cathode/banded side -> the coil's +5V pin)
//   Other coil pin -> 5V
//   Relay COM (pin 5, confirmed via multimeter - see memory) -> AC source
//   Relay NO  (pin 4, confirmed via multimeter) -> AC load (grinder)
//   Relay NC  (pin 3) -> left unconnected
//
// This is a standard low-side switch: GPIO HIGH saturates the transistor,
// pulling the coil's other end to GND and energizing it (ON). GPIO LOW
// leaves the transistor off (relay OFF). Non-inverted, matches real
// motor.cpp's existing polarity (HIGH = start) exactly - no firmware
// polarity flip needed once this is confirmed working.
//
// Serial commands (115200 baud):
//   o  ->  relay ON  (GPIO HIGH)
//   f  ->  relay OFF (GPIO LOW)
//
// Also blinks the board's own onboard LED (GPIO8, the common pin on
// ESP32-C3 SuperMini boards, active-LOW like most onboard LEDs) in lockstep
// with the relay command - a visible, on-the-ESP32 confirmation of exactly
// when 'o'/'f' actually lands, independent of whether the relay itself
// responds.
//
// Auto-off after AUTO_OFF_MS in case a session gets left connected with
// the relay on and forgotten.
//
// Flash with:   pio run -e ssr_test -t upload
// Restore real firmware with: pio run -e esp32-c3-devkitc-02 -t upload

static const uint32_t AUTO_OFF_MS = 5000;
static const uint8_t ONBOARD_LED_PIN = 8;

static bool relay_on = false;
static uint32_t on_since_ms = 0;

static void setRelay(bool on) {
    digitalWrite(PIN_MOTOR_SSR, on ? HIGH : LOW);   // active-high via BC547 driver stage
    digitalWrite(ONBOARD_LED_PIN, on ? LOW : HIGH); // active-low onboard LED - lit while relay commanded ON
}

void setup() {
    Serial.begin(115200);
    pinMode(PIN_MOTOR_SSR, OUTPUT);
    pinMode(ONBOARD_LED_PIN, OUTPUT);
    setRelay(false);
    Serial.println("Relay test ready (BC547 driver, active-HIGH). Send 'o' = ON, 'f' = OFF");
}

void loop() {
    if (Serial.available()) {
        char c = Serial.read();
        if (c == 'o') {
            setRelay(true);
            relay_on = true;
            on_since_ms = millis();
            Serial.println("Relay ON");
        } else if (c == 'f') {
            setRelay(false);
            relay_on = false;
            Serial.println("Relay OFF");
        }
    }

    if (relay_on && millis() - on_since_ms >= AUTO_OFF_MS) {
        setRelay(false);
        relay_on = false;
        Serial.println("Relay auto-off (timeout)");
    }
}
