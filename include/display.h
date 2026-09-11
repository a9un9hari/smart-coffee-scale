#ifndef DISPLAY_H
#define DISPLAY_H

#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>
#include "config.h"
#include "data_types.h"

// ST7789 240x240 IPS, software SPI on arbitrary GPIO (no hardware CS pin
// on this module - tied to -1/"none" in the constructor below, and RES/BLK
// are wired straight to 3.3V rather than driven from a GPIO).
// Screen is split into three fixed regions redrawn independently so each
// update only repaints what changed, not the whole panel.
class Display {
public:
    void begin();
    void update(const SystemStatus &status); // rate-limited to ~30Hz internally

    void drawWeight(float weight_g);
    void drawMenu(SystemMode mode, float target_weight_g);
    void drawStatus(SystemState state, uint8_t error_code);

private:
    static const uint32_t REFRESH_INTERVAL_MS = 33; // ~30Hz

    // cs=-1 (no CS pin on this module)
    Adafruit_ST7789 _tft = Adafruit_ST7789(-1, PIN_DISPLAY_DC, PIN_DISPLAY_MOSI, PIN_DISPLAY_SCLK, PIN_DISPLAY_RST);
    uint32_t _last_refresh_ms = 0;

    void printCentered(const char *text, int16_t y, uint8_t size, uint16_t color);
};

#endif // DISPLAY_H
