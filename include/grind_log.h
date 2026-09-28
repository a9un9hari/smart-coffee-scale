#ifndef GRIND_LOG_H
#define GRIND_LOG_H

#include <Arduino.h>
#include <functional>

// Persistent per-grind-session log on the ESP's own flash (LittleFS, the
// "spiffs" partition), so grinds can be reviewed later without a laptop
// attached while grinding. One CSV line per session, written when the
// session is fully over (after any top-up pulses), so it records the
// whole story: learned offset in use, main grind result, each top-up
// pulse's shortfall, the final dose, and how it ended.
//
// Retrieve over USB serial: send 'd' to dump the file, 'X' to clear it -
// or over BLE: the app requests every row newer than a (boot, uptime_s)
// cursor and gets them streamed one notify per row, ending with "#END"
// (see startSync/serviceSync). (boot, uptime_s) is unique per session, so
// it doubles as the row's identity for de-duplication on the app side.
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

    // Called with each finished row (no trailing newline) - wired to a BLE
    // notify so a connected app receives new grinds immediately.
    void setLiveSink(std::function<void(const char *)> sink) { _live_sink = sink; }

    // BLE sync: stream every stored row with (boot, uptime_s) greater than
    // the cursor. serviceSync() sends at most one row per
    // GRINDLOG_SYNC_INTERVAL_MS so it never holds the main loop for long;
    // call it every loop while it's safe to (the controller only does so
    // while the grinder is idle).
    void startSync(uint32_t after_boot, uint32_t after_uptime_s);
    void serviceSync(uint32_t now, const std::function<void(const char *)> &send);
    bool isSyncing() const { return _sync_active; }

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

    std::function<void(const char *)> _live_sink;

    bool _sync_active = false;
    uint8_t _sync_file_idx = 0; // 0 = rotated .old file, 1 = current file
    uint32_t _sync_pos = 0;
    uint32_t _sync_after_boot = 0;
    uint32_t _sync_after_uptime_s = 0;
    uint32_t _last_sync_send_ms = 0;

    void appendLine(const char *line);
};

#endif // GRIND_LOG_H
