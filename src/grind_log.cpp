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

void GrindLog::clear() {
    if (!_mounted) return;
    LittleFS.remove(LOG_PATH);
    LittleFS.remove(LOG_OLD_PATH);
    Serial.println("[GRINDLOG] cleared");
}
