#include <Arduino.h>
#include "control.h"

GrinderController g_controller;

void setup() {
    Serial.begin(115200);
    g_controller.begin();
    Serial.println("Smart Grinder Controller - Boot OK");
}

void loop() {
    g_controller.update();
}
