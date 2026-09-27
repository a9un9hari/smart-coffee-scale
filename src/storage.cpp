#include "storage.h"
#include <EEPROM.h>

// Sizes for Storage's, OvershootStorage's and PrefsStorage's slots -
// EEPROM.begin() is only ever called once, here, since calling it again
// with a different size is not well-defined on the ESP32 Arduino core's
// flash-emulated EEPROM. OvershootStorage::begin() is a no-op.
void Storage::begin() {
    EEPROM.begin(PrefsStorage::END_ADDR);
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

void OvershootStorage::begin() {
    // no-op - Storage::begin() already sized EEPROM to cover both classes'
    // slots in one EEPROM.begin() call.
}

uint16_t OvershootStorage::computeChecksum(OvershootData data) {
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

bool OvershootStorage::readSlot(int addr, OvershootData &out) {
    EEPROM.get(addr, out);
    return out.checksum == computeChecksum(out);
}

void OvershootStorage::writeSlot(int addr, OvershootData data) {
    data.checksum = computeChecksum(data);
    EEPROM.put(addr, data);
}

bool OvershootStorage::restore(OvershootData &data) {
    if (readSlot(ADDR_PRIMARY, data)) {
        return true;
    }

    OvershootData backup;
    if (readSlot(ADDR_BACKUP, backup)) {
        data = backup;
        writeSlot(ADDR_PRIMARY, backup); // self-heal the corrupt primary
        EEPROM.commit();
        return true;
    }

    return false; // both slots invalid - caller should seed zeros (no learning yet)
}

void OvershootStorage::save(const OvershootData &data) {
    writeSlot(ADDR_PRIMARY, data);
    writeSlot(ADDR_BACKUP, data);
    EEPROM.commit();
}

uint16_t PrefsStorage::computeChecksum(PrefsData data) {
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

bool PrefsStorage::readSlot(int addr, PrefsData &out) {
    EEPROM.get(addr, out);
    return out.checksum == computeChecksum(out);
}

void PrefsStorage::writeSlot(int addr, PrefsData data) {
    data.checksum = computeChecksum(data);
    EEPROM.put(addr, data);
}

bool PrefsStorage::restore(PrefsData &data) {
    if (readSlot(ADDR_PRIMARY, data)) {
        return true;
    }

    PrefsData backup;
    if (readSlot(ADDR_BACKUP, backup)) {
        data = backup;
        writeSlot(ADDR_PRIMARY, backup); // self-heal the corrupt primary
        EEPROM.commit();
        return true;
    }

    return false; // both slots invalid - caller should seed defaults
}

void PrefsStorage::save(const PrefsData &data) {
    writeSlot(ADDR_PRIMARY, data);
    writeSlot(ADDR_BACKUP, data);
    EEPROM.commit();
}
