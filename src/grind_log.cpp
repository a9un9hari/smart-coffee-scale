#include "grind_log.h"
#include "config.h"
#include <LittleFS.h>
#include <Preferences.h>

static const char *LOG_PATH = "/grinds.csv";
static const char *LOG_OLD_PATH = "/grinds.old.csv";
static const char *LOG_HEADER =
    "boot,uptime_s,profile,target_g,learned_before_g,main_g,learned_after_g,pulses,pulse_short_g,final_g,result\n";

void GrindLog::begin() {
    Preferences prefs;
    prefs.begin("grindlog", false);
    _boot = prefs.getUInt("boot", 0) + 1;
    prefs.putUInt("boot", _boot);
    prefs.end();

    _mounted = LittleFS.begin(true); // formats the partition on first use
    if (!_mounted) {
        Serial.println("[GRINDLOG] LittleFS mount failed - grind log disabled");
        return;
    }
    size_t size = 0;
    if (LittleFS.exists(LOG_PATH)) {
        File f = LittleFS.open(LOG_PATH, "r");
        size = f.size();
        f.close();
    }
    Serial.printf("[GRINDLOG] boot #%lu, log %u bytes - send 'd' to dump, 'X' to clear\n",
                  (unsigned long)_boot, (unsigned)size);
}

void GrindLog::startSession(uint8_t profile_id, float target_g, float learned_offset_g) {
    _active = true;
    _start_s = millis() / 1000;
    _profile = profile_id;
    _target_g = target_g;
    _learned_before_g = learned_offset_g;
    _main_g = NAN;
    _learned_after_g = NAN;
    _pulses = 0;
    _pulse_short[0] = '\0';
}

void GrindLog::setMainResult(float delivered_g, float learned_after_g) {
    _main_g = delivered_g;
    _learned_after_g = learned_after_g;
}

void GrindLog::addPulse(float shortfall_g) {
    size_t used = strlen(_pulse_short);
    snprintf(_pulse_short + used, sizeof(_pulse_short) - used, "%s%.2f", _pulses ? ";" : "", shortfall_g);
    _pulses++;
}

void GrindLog::finish(float final_delivered_g, const char *result) {
    if (!_active) {
        return;
    }
    _active = false;

    char line[160];
    snprintf(line, sizeof(line), "%lu,%lu,%d,%.2f,%.2f,%.2f,%.2f,%d,%s,%.2f,%s\n",
             (unsigned long)_boot, (unsigned long)_start_s, _profile + 1, _target_g, _learned_before_g,
             _main_g, _learned_after_g, _pulses, _pulse_short, final_delivered_g, result);
    Serial.printf("[GRINDLOG] %s", line);
    appendLine(line);

    if (_live_sink) {
        line[strcspn(line, "\n")] = '\0';
        _live_sink(line);
    }
}

void GrindLog::appendLine(const char *line) {
    if (!_mounted) {
        return;
    }
    if (LittleFS.exists(LOG_PATH)) {
        File f = LittleFS.open(LOG_PATH, "r");
        size_t size = f.size();
        f.close();
        if (size > GRINDLOG_MAX_BYTES) { // keep one previous generation, drop anything older
            LittleFS.remove(LOG_OLD_PATH);
            LittleFS.rename(LOG_PATH, LOG_OLD_PATH);
        }
    }
    bool fresh = !LittleFS.exists(LOG_PATH);
    File f = LittleFS.open(LOG_PATH, "a");
    if (!f) {
        Serial.println("[GRINDLOG] open for append failed");
        return;
    }
    if (fresh) {
        f.print(LOG_HEADER);
    }
    f.print(line);
    f.close();
}

void GrindLog::dump(Stream &out) {
    if (!_mounted) {
        out.println("[GRINDLOG] not mounted");
        return;
    }
    const char *paths[] = {LOG_OLD_PATH, LOG_PATH};
    out.println("----- GRINDLOG BEGIN -----");
    for (const char *path : paths) {
        if (!LittleFS.exists(path)) continue;
        File f = LittleFS.open(path, "r");
        while (f.available()) {
            out.write(f.read());
        }
        f.close();
    }
    out.println("----- GRINDLOG END -----");
}

void GrindLog::startSync(uint32_t after_boot, uint32_t after_uptime_s) {
    _sync_active = true;
    _sync_file_idx = 0;
    _sync_pos = 0;
    _sync_after_boot = after_boot;
    _sync_after_uptime_s = after_uptime_s;
    Serial.printf("[GRINDLOG] BLE sync requested after boot=%lu uptime=%lu\n",
                  (unsigned long)after_boot, (unsigned long)after_uptime_s);
}

void GrindLog::serviceSync(uint32_t now, const std::function<void(const char *)> &send) {
    if (!_sync_active || now - _last_sync_send_ms < GRINDLOG_SYNC_INTERVAL_MS) {
        return;
    }
    const char *paths[] = {LOG_OLD_PATH, LOG_PATH};

    // Skipping already-synced rows costs no BLE traffic, so keep scanning
    // until one row is sent or everything is exhausted.
    while (_sync_file_idx < 2) {
        const char *path = paths[_sync_file_idx];
        if (!_mounted || !LittleFS.exists(path)) {
            _sync_file_idx++;
            _sync_pos = 0;
            continue;
        }
        File f = LittleFS.open(path, "r");
        if (!f || !f.seek(_sync_pos) || !f.available()) {
            f.close();
            _sync_file_idx++;
            _sync_pos = 0;
            continue;
        }
        String row = f.readStringUntil('\n');
        _sync_pos = f.position();
        f.close();

        unsigned long boot = 0, uptime = 0;
        if (sscanf(row.c_str(), "%lu,%lu,", &boot, &uptime) != 2) {
            continue; // header or malformed line
        }
        if (boot > _sync_after_boot || (boot == _sync_after_boot && uptime > _sync_after_uptime_s)) {
            send(row.c_str());
            _last_sync_send_ms = now;
            return;
        }
    }

    send("#END");
    _sync_active = false;
    Serial.println("[GRINDLOG] BLE sync done");
}

void GrindLog::clear() {
    if (!_mounted) return;
    LittleFS.remove(LOG_PATH);
    LittleFS.remove(LOG_OLD_PATH);
    Serial.println("[GRINDLOG] cleared");
}
