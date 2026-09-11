#ifndef DISPLAY_H
#define DISPLAY_H

#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "config.h"
#include "data_types.h"

// OLED SSD1306 128x64 over I2C. Screen is split into three fixed regions
// redrawn independently so each update only repaints what changed:
//   y 0-18   weight (big number, text size 2)
//   y 20-40  menu (mode + target)
//   y 42-63  status (state + error)
class Display {
public:
    void begin();
    void update(const SystemStatus &status); // rate-limited to ~30Hz internally

    void drawWeight(float weight_g);
    void drawMenu(SystemMode mode, float target_weight_g);
    void drawStatus(SystemState state, uint8_t error_code);

private:
    static const uint32_t REFRESH_INTERVAL_MS = 33; // ~30Hz
    static const uint8_t I2C_ADDRESS = 0x3C;

    Adafruit_SSD1306 _oled = Adafruit_SSD1306(128, 64, &Wire, -1);
    uint32_t _last_refresh_ms = 0;
};

#endif // DISPLAY_H
