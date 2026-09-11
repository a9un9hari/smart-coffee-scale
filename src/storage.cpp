#include "storage.h"
#include <EEPROM.h>

void Storage::begin() {
    EEPROM.begin(2 * sizeof(CalibrationData)); // primary + backup slot
}

// CRC-16/MODBUS, computed over the struct with checksum zeroed out.
uint16_t Storage::computeChecksum(CalibrationData data) {
    data.checksum = 0;
    const uint8_t *bytes = reinterpret_cast<const uint8_t *>(&data);

    uint16_t crc = 0xFFFF;
    for (size_t i = 0; i < sizeof(data); i++) {
        crc ^= bytes[i];
        for (uint8_t bit = 0; bit < 8; bit++) {
            if (crc & 0x0001) {
                crc = (crc >> 1) ^ 0xA001;
            } else {
                crc >>= 1;
            }
        }
    }
    return crc;
}

bool Storage::readSlot(int addr, CalibrationData &out) {
    EEPROM.get(addr, out);
    return out.checksum == computeChecksum(out);
}

void Storage::writeSlot(int addr, CalibrationData data) {
    data.checksum = computeChecksum(data);
    EEPROM.put(addr, data);
}

bool Storage::restore(CalibrationData &data) {
    if (readSlot(ADDR_PRIMARY, data)) {
        return true;
    }

    CalibrationData backup;
    if (readSlot(ADDR_BACKUP, backup)) {
        data = backup;
        writeSlot(ADDR_PRIMARY, backup); // self-heal the corrupt primary
        EEPROM.commit();
        return true;
    }

    return false; // both slots invalid - caller should seed factory defaults
}

void Storage::save(const CalibrationData &data) {
    writeSlot(ADDR_PRIMARY, data);
    writeSlot(ADDR_BACKUP, data);
    EEPROM.commit();
}
