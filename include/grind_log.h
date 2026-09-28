#ifndef GRIND_LOG_H
#define GRIND_LOG_H

#include <Arduino.h>

// Persistent per-grind-session log on the ESP's own flash (LittleFS, the
// "spiffs" partition), so grinds can be reviewed later without a laptop
// attached while grinding. One CSV line per session, written when the
// session is fully over (after any top-up pulses), so it records the
// whole story: learned offset in use, main grind result, each top-up
// pulse's shortfall, the final dose, and how it ended.
//
// Retrieve over USB serial: send 'd' to dump the file, 'X' to clear it.
// No real-time clock on the board - rows carry a boot counter plus uptime
// seconds instead, enough to order sessions and spot power cycles.
class GrindLog {
public:
    void begin();

    void startSession(uint8_t profile_id, float target_g, float learned_offset_g);
    void setMainResult(float delivered_g, float learned_after_g);
    void addPulse(float shortfall_g);
    // result: short tag - "ok", "gave_up", "cup_removed", "no_dot",
    // "user_stop", "interrupted", "error". No-op if no session is open.
    void finish(float final_delivered_g, const char *result);
    bool isSessionOpen() const { return _active; }

    void dump(Stream &out);
    void clear();

private:
    bool _mounted = false;
    uint32_t _boot = 0;

    bool _active = false;
    uint32_t _start_s = 0;
    uint8_t _profile = 0;
    float _target_g = 0.0f;
    float _learned_before_g = 0.0f;
    float _main_g = NAN;
    float _learned_after_g = NAN;
    uint8_t _pulses = 0;
    char _pulse_short[48] = ""; // "0.45;0.21" - shortfall before each pulse

    void appendLine(const char *line);
};

#endif // GRIND_LOG_H
