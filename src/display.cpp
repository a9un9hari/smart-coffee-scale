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
    _tft.init(240, 240);
    _tft.setRotation(0);
    _tft.fillScreen(ST77XX_BLACK);
    _last_refresh_ms = 0;
}

void Display::printCentered(const char *text, int16_t y, uint8_t size, uint16_t color) {
    _tft.setTextSize(size);
    _tft.setTextColor(color);

    int16_t x1, y1;
    uint16_t w, h;
    _tft.getTextBounds(text, 0, 0, &x1, &y1, &w, &h);

    _tft.setCursor((240 - (int16_t)w) / 2, y);
    _tft.print(text);
}

void Display::drawWeight(float weight_g) {
    _tft.fillRect(0, 0, 240, 100, ST77XX_BLACK);

    char buf[12];
    snprintf(buf, sizeof(buf), "%.1fg", weight_g);
    printCentered(buf, 35, 4, ST77XX_WHITE);
}

void Display::drawMenu(SystemMode mode, float target_weight_g) {
    _tft.fillRect(0, 100, 240, 60, ST77XX_BLACK);
    printCentered(modeName(mode), 108, 2, ST77XX_CYAN);

    char buf[24];
    snprintf(buf, sizeof(buf), "target: %.1fg", target_weight_g);
    printCentered(buf, 138, 1, ST77XX_WHITE);
}

void Display::drawStatus(SystemState state, uint8_t error_code) {
    _tft.fillRect(0, 160, 240, 80, ST77XX_BLACK);

    uint16_t color = (state == STATE_ERROR) ? ST77XX_RED : ST77XX_GREEN;
    printCentered(stateName(state), 175, 2, color);

    if (error_code != 0) {
        char buf[24];
        snprintf(buf, sizeof(buf), "err code: %d", error_code);
        printCentered(buf, 210, 1, ST77XX_RED);
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
