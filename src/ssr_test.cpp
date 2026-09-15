#include <Arduino.h>
#include "config.h"

// Standalone SSR output test - zero application code, just toggles
// PIN_MOTOR_SSR (GPIO2) directly so the switching action can be checked
// with a multimeter across the SSR's load terminals (SSR unloaded, no
// mains/motor connected yet - see docs/DEVELOPMENT-LOG.md).
//
// Serial commands (115200 baud):
//   o  ->  SSR ON
//   f  ->  SSR OFF
//
// Auto-off after AUTO_OFF_MS in case a session gets left connected with
// the SSR on and forgotten.
//
// Flash with:   pio run -e ssr_test -t upload
// Restore real firmware with: pio run -e esp32-c3-devkitc-02 -t upload

static const uint32_t AUTO_OFF_MS = 5000;

static bool ssr_on = false;
static uint32_t on_since_ms = 0;

void setup() {
    Serial.begin(115200);
    pinMode(PIN_MOTOR_SSR, OUTPUT);
    digitalWrite(PIN_MOTOR_SSR, LOW);
    Serial.println("SSR test ready. Send 'o' = ON, 'f' = OFF");
}

void loop() {
    if (Serial.available()) {
        char c = Serial.read();
        if (c == 'o') {
            digitalWrite(PIN_MOTOR_SSR, HIGH);
            ssr_on = true;
            on_since_ms = millis();
            Serial.println("SSR ON");
        } else if (c == 'f') {
            digitalWrite(PIN_MOTOR_SSR, LOW);
            ssr_on = false;
            Serial.println("SSR OFF");
        }
    }

    if (ssr_on && millis() - on_since_ms >= AUTO_OFF_MS) {
        digitalWrite(PIN_MOTOR_SSR, LOW);
        ssr_on = false;
        Serial.println("SSR auto-off (timeout)");
    }
}
