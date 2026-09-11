#ifndef STORAGE_H
#define STORAGE_H

#include <Arduino.h>
#include "data_types.h"

// Persists CalibrationData in flash-emulated EEPROM (ESP32 Arduino core).
// Two mirrored slots (primary + backup) with a CRC16 per slot: if the
// primary is corrupt (e.g. power loss mid-write), restore() self-heals it
// from the backup instead of falling back to factory defaults.
class Storage {
public:
    void begin();
    bool restore(CalibrationData &data);       // true if a valid slot was found
    void save(const CalibrationData &data);    // writes + commits both slots

private:
    static const int ADDR_PRIMARY = 0;
    static const int ADDR_BACKUP = sizeof(CalibrationData);

    static uint16_t computeChecksum(CalibrationData data); // by value: checksum zeroed before calc
    bool readSlot(int addr, CalibrationData &out);
    void writeSlot(int addr, CalibrationData data);
};

#endif // STORAGE_H
