#ifndef STORAGE_H
#define STORAGE_H

#include <Arduino.h>
#include "data_types.h"

// Persists CalibrationData in flash-emulated EEPROM (ESP32 Arduino core).
// Two mirrored slots (primary + backup) with a CRC16 per slot: if the
// primary is corrupt (e.g. power loss mid-write), restore() self-heals it
// from the backup instead of falling back to factory defaults.
//
// NOTE: this class owns EEPROM addresses [0, 2*sizeof(CalibrationData)) -
// OvershootStorage and PrefsStorage below pick up right after it. All share one
// EEPROM.begin() call (see Storage::begin()).
class Storage {
public:
    void begin();
    bool restore(CalibrationData &data);       // true if a valid slot was found
    void save(const CalibrationData &data);    // writes + commits both slots

    static const int ADDR_PRIMARY = 0;
    static const int ADDR_BACKUP = sizeof(CalibrationData);
    static const int END_ADDR = ADDR_BACKUP + sizeof(CalibrationData); // first free byte after this class's slots

private:
    static uint16_t computeChecksum(CalibrationData data); // by value: checksum zeroed before calc
    bool readSlot(int addr, CalibrationData &out);
    void writeSlot(int addr, CalibrationData data);
};

// Same mirrored-slot-plus-CRC pattern as Storage, but for OvershootData -
// kept as an entirely separate EEPROM record (not a field added to
// CalibrationData) so this feature can change shape later without ever
// invalidating a board's already-provisioned scale calibration/cup
// profiles (see OvershootData's own comment in data_types.h). Placed
// right after Storage's two slots in EEPROM (Storage::END_ADDR).
class OvershootStorage {
public:
    void begin(); // does NOT call EEPROM.begin() itself - see GrinderController::begin()
    bool restore(OvershootData &data);
    void save(const OvershootData &data);

private:
    static const int ADDR_PRIMARY = Storage::END_ADDR;
    static const int ADDR_BACKUP = ADDR_PRIMARY + sizeof(OvershootData);

public:
    static const int END_ADDR = ADDR_BACKUP + sizeof(OvershootData);

private:
    static uint16_t computeChecksum(OvershootData data);
    bool readSlot(int addr, OvershootData &out);
    void writeSlot(int addr, OvershootData data);
};

// Same pattern again for PrefsData, placed right after OvershootStorage's
// slots (OvershootStorage::END_ADDR). This is the last record - its
// END_ADDR is what Storage::begin() sizes EEPROM to.
class PrefsStorage {
public:
    bool restore(PrefsData &data);
    void save(const PrefsData &data);

private:
    static const int ADDR_PRIMARY = OvershootStorage::END_ADDR;
    static const int ADDR_BACKUP = ADDR_PRIMARY + sizeof(PrefsData);

public:
    static const int END_ADDR = ADDR_BACKUP + sizeof(PrefsData);

private:
    static uint16_t computeChecksum(PrefsData data);
    bool readSlot(int addr, PrefsData &out);
    void writeSlot(int addr, PrefsData data);
};

#endif // STORAGE_H
