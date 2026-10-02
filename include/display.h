#ifndef DISPLAY_H
#define DISPLAY_H

#include <Arduino.h>
#include <U8g2lib.h>
#include "config.h"
#include "data_types.h"

// Read-only status screen on a 0.96" SSD1306 128x64 I2C OLED, for
// phone-less grinding (see WeightSource persistence in PrefsStorage).
// No input - control stays on BLE/app and cup auto-detect.
//
// Optional hardware: begin() probes the I2C address and, if nothing
// answers, update() becomes a no-op so the grinder runs exactly as before
// without the module attached.
//
// Frames are drawn into the buffer every DISPLAY_REFRESH_MS but sent to
// the panel one 8-pixel tile row per update() call (~12ms each at 100kHz,
// see OLED_I2C_HZ) instead of one ~100ms blocking sendBuffer(), so the
// main loop - and the grind stop check - is never held for long. update()
// is also called after the state machine in GrinderController::update().
//
// Recovery: a corrupted command on a long, noisy I2C line can leave the
// SSD1306 all-white (entire-display-on), inverted, or off. Every
// DISPLAY_RECOVER_MS the controller's "follow RAM / normal / charge pump
// on / display on" commands are resent - none of them depend on panel
// orientation and none blank the screen, so a glitched panel heals within
// seconds without a visible flicker; the row-by-row resend repaints the
// content itself.
class Display {
public:
    Display();

    bool begin(); // true if the OLED answered on I2C
    // phase_label overrides the bottom-left state text when non-empty
    // (e.g. "SETTLING", "TOP-UP 1/3") - states the SystemState enum doesn't
    // distinguish on its own.
    void update(uint32_t now, const SystemStatus &status, const char *phase_label, float session_start_weight_g,
                const CupProfile &cup, uint8_t cup_id,
                WeightSource weight_source, bool timemore_connected);

private:
    U8G2_SSD1306_128X64_NONAME_F_HW_I2C _oled;
    bool _present = false;
    uint32_t _last_refresh_ms = 0;
    uint32_t _splash_until_ms = 0;
    bool _refresh_timing_logged = false;
    uint8_t _next_row = 0;      // next tile row (0-7) of the current frame to send
    bool _frame_pending = false; // a rendered frame is still being sent row by row
    uint32_t _last_recover_ms = 0;

    void sendRecoveryCommands();

    // "DONE" hold after a grind: GRINDING -> IDLE latches the session's
    // start weight so the big number keeps showing the net dose (not the
    // cup+coffee total) for DISPLAY_DONE_HOLD_MS, same as the app would.
    SystemState _prev_state = STATE_IDLE;
    uint32_t _done_ms = 0;
    bool _done_active = false;
    float _done_start_weight_g = 0.0f;

    void render(uint32_t now, const SystemStatus &status, const char *phase_label, float session_start_weight_g,
                const CupProfile &cup, uint8_t cup_id,
                WeightSource weight_source, bool timemore_connected);
};

#endif // DISPLAY_H
