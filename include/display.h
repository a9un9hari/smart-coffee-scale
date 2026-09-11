#ifndef DISPLAY_H
#define DISPLAY_H

#include <TFT_eSPI.h>
#include "config.h"
#include "data_types.h"

// ST7789 240x240 IPS via TFT_eSPI (driver/pins configured in platformio.ini
// build_flags). Screen is split into three fixed regions redrawn independently
// so each update only repaints what changed, not the whole panel.
class Display {
public:
    void begin();
    void update(const SystemStatus &status); // rate-limited to ~30Hz internally

    void drawWeight(float weight_g);
    void drawMenu(SystemMode mode, float target_weight_g);
    void drawStatus(SystemState state, uint8_t error_code);

private:
    static const uint32_t REFRESH_INTERVAL_MS = 33; // ~30Hz

    TFT_eSPI _tft = TFT_eSPI();
    uint32_t _last_refresh_ms = 0;
};

#endif // DISPLAY_H
