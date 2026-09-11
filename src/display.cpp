#include "display.h"

// Fixed screen regions (240x240):
//   0-100   weight (big number)
//   100-160 menu (mode + target)
//   160-240 status (state + error)

static const char *modeName(SystemMode mode) {
    return (mode == MODE_GRINDER) ? "GRINDER" : "ESPRESSO";
}

static const char *stateName(SystemState state) {
    switch (state) {
        case STATE_IDLE:            return "IDLE";
        case STATE_SELECT_WEIGHT:   return "SELECT WEIGHT";
        case STATE_UPDATE_WEIGHT:   return "SET WEIGHT";
        case STATE_GRINDING:        return "GRINDING";
        case STATE_ESPRESSO_IDLE:   return "ESPRESSO READY";
        case STATE_PULL_SHOT:       return "PULL SHOT";
        case STATE_PULLING:         return "PULLING";
        case STATE_SHOT_COMPLETE:   return "SHOT COMPLETE";
        case STATE_ERROR:           return "ERROR";
        default:                    return "?";
    }
}

void Display::begin() {
    _tft.init();
    _tft.setRotation(0);
    _tft.fillScreen(TFT_BLACK);
    _last_refresh_ms = 0;
}

void Display::drawWeight(float weight_g) {
    _tft.fillRect(0, 0, 240, 100, TFT_BLACK);
    _tft.setTextColor(TFT_WHITE, TFT_BLACK);
    _tft.setTextDatum(MC_DATUM);
    _tft.setTextSize(1);
    _tft.drawFloat(weight_g, 1, 120, 50, 6); // font 6: large digits
}

void Display::drawMenu(SystemMode mode, float target_weight_g) {
    _tft.fillRect(0, 100, 240, 60, TFT_BLACK);
    _tft.setTextColor(TFT_CYAN, TFT_BLACK);
    _tft.setTextDatum(MC_DATUM);
    _tft.drawString(modeName(mode), 120, 118, 4);

    char buf[24];
    snprintf(buf, sizeof(buf), "target: %.1fg", target_weight_g);
    _tft.setTextColor(TFT_WHITE, TFT_BLACK);
    _tft.drawString(buf, 120, 146, 2);
}

void Display::drawStatus(SystemState state, uint8_t error_code) {
    _tft.fillRect(0, 160, 240, 80, TFT_BLACK);
    _tft.setTextDatum(MC_DATUM);

    uint16_t color = (state == STATE_ERROR) ? TFT_RED : TFT_GREEN;
    _tft.setTextColor(color, TFT_BLACK);
    _tft.drawString(stateName(state), 120, 190, 4);

    if (error_code != 0) {
        char buf[24];
        snprintf(buf, sizeof(buf), "err code: %d", error_code);
        _tft.setTextColor(TFT_RED, TFT_BLACK);
        _tft.drawString(buf, 120, 220, 2);
    }
}

void Display::update(const SystemStatus &status) {
    uint32_t now = millis();
    if (now - _last_refresh_ms < REFRESH_INTERVAL_MS) {
        return;
    }
    _last_refresh_ms = now;

    drawWeight(status.current_weight_g);
    drawMenu(status.mode, status.target_weight_g);
    drawStatus(status.state, status.error_code);
}
