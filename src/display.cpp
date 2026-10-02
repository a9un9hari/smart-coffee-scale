#include "display.h"
#include "build_stamp.h"
#include <Wire.h>

Display::Display() : _oled(U8G2_R0, U8X8_PIN_NONE) {}

bool Display::begin() {
    Wire.begin(PIN_OLED_SDA, PIN_OLED_SCL, OLED_I2C_HZ);
    Wire.beginTransmission(OLED_I2C_ADDR);
    _present = (Wire.endTransmission() == 0);
    Serial.printf("[DISPLAY] OLED at 0x%02X %s\n", OLED_I2C_ADDR, _present ? "found" : "not found - display disabled");
    if (!_present) {
        return false;
    }

    _oled.setI2CAddress(OLED_I2C_ADDR * 2); // U8g2 wants the 8-bit (shifted) address
    _oled.setBusClock(OLED_I2C_HZ);
    _oled.begin();
    _oled.clearBuffer();
    _oled.setFont(u8g2_font_ncenB12_tr);
    _oled.drawStr((128 - _oled.getStrWidth("La Mardjono")) / 2, 24, "La Mardjono");
    _oled.setFont(u8g2_font_6x10_tr);
    _oled.drawStr((128 - _oled.getStrWidth("Smart Grinder")) / 2, 40, "Smart Grinder");
    // Build stamp ("fw Sep 28 13:45") - after an OTA update the splash alone
    // confirms which firmware is now running, no phone needed.
    char build[20];
    snprintf(build, sizeof(build), "fw %.6s %.5s", BUILD_DATE, BUILD_TIME);
    _oled.drawStr((128 - _oled.getStrWidth(build)) / 2, 58, build);
    _oled.sendBuffer();
    _splash_until_ms = millis() + DISPLAY_SPLASH_MS;
    return true;
}

static const char *stateLabel(SystemState state) {
    switch (state) {
        case STATE_IDLE:          return "READY";
        case STATE_GRINDING:      return "GRINDING";
        case STATE_ESPRESSO_IDLE: return "ESPRESSO";
        case STATE_PULL_SHOT:     return "PULL SHOT";
        case STATE_PULLING:       return "PULLING";
        case STATE_SHOT_COMPLETE: return "SHOT DONE";
        case STATE_ERROR:         return "ERROR";
    }
    return "?";
}

void Display::update(uint32_t now, const SystemStatus &status, const char *phase_label, float session_start_weight_g,
                     const CupProfile &cup, uint8_t cup_id,
                     WeightSource weight_source, bool timemore_connected) {
    if (!_present) {
        return;
    }

    // Track the transition every call (not just on refresh ticks) so a
    // short grind can't slip between two refreshes unnoticed.
    if (_prev_state == STATE_GRINDING && status.state == STATE_IDLE) {
        _done_active = true;
        _done_ms = now;
        _done_start_weight_g = session_start_weight_g;
    }
    if (status.state != STATE_IDLE || now - _done_ms >= DISPLAY_DONE_HOLD_MS) {
        _done_active = false;
    }
    _prev_state = status.state;

    if ((int32_t)(now - _splash_until_ms) < 0) {
        return; // splash stays up without blocking the loop
    }

    // Finish sending the current frame first, one tile row per call - the
    // buffer isn't redrawn mid-send, so a frame never mixes two states.
    if (_frame_pending) {
        uint32_t start_us = micros();
        _oled.updateDisplayArea(0, _next_row, 16, 1);
        if (!_refresh_timing_logged) {
            Serial.printf("[DISPLAY] one tile row took %lu us\n", (unsigned long)(micros() - start_us));
            _refresh_timing_logged = true;
        }
        if (++_next_row >= 8) {
            _frame_pending = false;
        }
        return;
    }

    if (now - _last_recover_ms >= DISPLAY_RECOVER_MS) {
        _last_recover_ms = now;
        sendRecoveryCommands();
    }

    if (now - _last_refresh_ms < DISPLAY_REFRESH_MS) {
        return;
    }
    _last_refresh_ms = now;
    render(now, status, phase_label, session_start_weight_g, cup, cup_id, weight_source, timemore_connected);
    _next_row = 0;
    _frame_pending = true;
}

void Display::sendRecoveryCommands() {
    u8x8_t *u8x8 = _oled.getU8x8();
    u8x8_cad_StartTransfer(u8x8);
    u8x8_cad_SendCmd(u8x8, 0xA4); // display follows RAM (undoes a stray 0xA5 "entire display on" = all white)
    u8x8_cad_SendCmd(u8x8, 0xA6); // normal, not inverted
    u8x8_cad_SendCmd(u8x8, 0x8D); // charge pump...
    u8x8_cad_SendCmd(u8x8, 0x14); // ...enabled
    u8x8_cad_SendCmd(u8x8, 0xAF); // display on
    u8x8_cad_EndTransfer(u8x8);
}

void Display::render(uint32_t now, const SystemStatus &status, const char *phase_label, float session_start_weight_g,
                     const CupProfile &cup, uint8_t cup_id,
                     WeightSource weight_source, bool timemore_connected) {
    char buf[24];
    _oled.clearBuffer();

    // --- top row: active cup (left), weight source link (right) ---
    _oled.setFont(u8g2_font_6x10_tr);
    if (cup.cup_weight_g < 0.0f) {
        snprintf(buf, sizeof(buf), "Cup%d: not set", cup_id + 1);
    } else if (cup.name[0] != '\0') {
        snprintf(buf, sizeof(buf), "%.*s", (int)sizeof(cup.name), cup.name);
    } else {
        snprintf(buf, sizeof(buf), "Cup %d", cup_id + 1);
    }
    _oled.drawStr(0, 9, buf);

    const char *link;
    if (weight_source == WEIGHT_SOURCE_TIMEMORE) {
        // blink while disconnected so it reads as "searching", not a fixed label
        link = timemore_connected ? "DOT" : (((now / 500) % 2) ? "DOT?" : "");
    } else {
        link = "LOAD";
    }
    _oled.drawStr(128 - _oled.getStrWidth(link), 9, link);
    _oled.drawHLine(0, 12, 128);

    // --- big number: net dose while grinding / just done, else raw weight ---
    bool grinding = (status.state == STATE_GRINDING);
    float shown_g = status.current_weight_g;
    if (grinding) {
        shown_g -= session_start_weight_g;
    } else if (_done_active) {
        shown_g -= _done_start_weight_g;
    }
    snprintf(buf, sizeof(buf), "%.1f", shown_g);
    _oled.setFont(u8g2_font_logisoso24_tn);
    int16_t num_w = _oled.getStrWidth(buf);
    _oled.setFont(u8g2_font_6x10_tr);
    int16_t unit_w = _oled.getStrWidth("g");
    int16_t x = (128 - (num_w + 2 + unit_w)) / 2;
    _oled.setFont(u8g2_font_logisoso24_tn);
    _oled.drawStr(x, 40, buf);
    _oled.setFont(u8g2_font_6x10_tr);
    _oled.drawStr(x + num_w + 2, 40, "g");

    // --- bottom row: state (left), target (right) ---
    const char *label = _done_active ? "DONE" : stateLabel(status.state);
    if (phase_label != nullptr && phase_label[0] != '\0') {
        label = phase_label;
    }
    if (status.state == STATE_ERROR) {
        snprintf(buf, sizeof(buf), "ERROR %d", status.error_code);
        label = buf;
    }
    _oled.drawStr(0, 53, label);

    char target[16];
    snprintf(target, sizeof(target), "T %.1fg", status.target_weight_g);
    _oled.drawStr(128 - _oled.getStrWidth(target), 53, target);

    // --- progress bar while grinding ---
    if (grinding && status.target_weight_g > 0.0f) {
        float frac = shown_g / status.target_weight_g;
        if (frac < 0.0f) frac = 0.0f;
        if (frac > 1.0f) frac = 1.0f;
        _oled.drawFrame(0, 57, 128, 7);
        _oled.drawBox(1, 58, (uint8_t)(126 * frac), 5);
    }
    // Sent row by row from update(), not here - see display.h.
}
