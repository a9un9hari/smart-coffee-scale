#include <Arduino.h>
#include "control.h"

GrinderController g_controller;

void setup() {
    // Relay pin LOW before anything else (USB Serial, sensors, BLE): until
    // firmware takes over, the pin sits wherever the chip leaves it, and
    // that briefly switched the relay on and started the grinder on every
    // boot/OTA reboot (2026-09-28). Can't cover the ROM's own boot time -
    // see PIN_MOTOR_SSR in config.h for the hardware fix. (Doing this from
    // a static constructor instead - even earlier - crashed the board in a
    // boot loop: the Arduino core isn't initialised yet at that point.)
    pinMode(PIN_MOTOR_SSR, OUTPUT);
    digitalWrite(PIN_MOTOR_SSR, LOW);

    Serial.begin(115200);
    g_controller.begin();
    Serial.println("Smart Grinder Controller - Boot OK");
}

void loop() {
    g_controller.update();
}
