#include "display.h"

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
    Wire.begin(PIN_DISPLAY_SDA, PIN_DISPLAY_SCL);
    _oled.begin(SSD1306_SWITCHCAPVCC, I2C_ADDRESS);
    _oled.clearDisplay();
    _oled.display();
    _last_refresh_ms = 0;
}

void Display::drawWeight(float weight_g) {
    _oled.fillRect(0, 0, 128, 18, SSD1306_BLACK);
    _oled.setTextSize(2);
    _oled.setTextColor(SSD1306_WHITE);
    _oled.setCursor(0, 0);

    char buf[12];
    snprintf(buf, sizeof(buf), "%.1fg", weight_g);
    _oled.print(buf);
    _oled.display();
}

void Display::drawMenu(SystemMode mode, float target_weight_g) {
    _oled.fillRect(0, 20, 128, 20, SSD1306_BLACK);
    _oled.setTextSize(1);
    _oled.setTextColor(SSD1306_WHITE);

    _oled.setCursor(0, 20);
    _oled.print(modeName(mode));

    char buf[24];
    snprintf(buf, sizeof(buf), "target: %.1fg", target_weight_g);
    _oled.setCursor(0, 32);
    _oled.print(buf);
    _oled.display();
}

void Display::drawStatus(SystemState state, uint8_t error_code) {
    _oled.fillRect(0, 42, 128, 22, SSD1306_BLACK);
    _oled.setTextSize(1);
    _oled.setTextColor(SSD1306_WHITE);

    _oled.setCursor(0, 44);
    _oled.print(stateName(state));

    if (error_code != 0) {
        char buf[24];
        snprintf(buf, sizeof(buf), "err code: %d", error_code);
        _oled.setCursor(0, 54);
        _oled.print(buf);
    }
    _oled.display();
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
