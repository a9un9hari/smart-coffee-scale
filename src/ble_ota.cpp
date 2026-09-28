#include "ble_ota.h"
#include "config.h"
#include <Update.h>

void BleOta::begin() {
    _lock = xSemaphoreCreateMutex();
}

void BleOta::fail(BleOtaError err) {
    if (Update.isRunning()) {
        Update.abort();
    }
    _error = err;
    setState(BLE_OTA_ERROR);
    Serial.printf("[BLE-OTA] failed: error=%d received=%lu/%lu\n",
                  (int)err, (unsigned long)_received, (unsigned long)_total);
}

void BleOta::onControlWrite(const uint8_t *data, size_t len) {
    if (len == 0) {
        return;
    }
    xSemaphoreTake(_lock, portMAX_DELAY);

    switch (data[0]) {
        case 0x01: { // BEGIN
            if (!_allowed) {
                _error = BLE_OTA_ERR_BUSY;
                setState(BLE_OTA_ERROR);
                Serial.println("[BLE-OTA] BEGIN refused: grinder busy");
                break;
            }
            if (len < 1 + 4 + 32) {
                _error = BLE_OTA_ERR_BAD_REQUEST;
                setState(BLE_OTA_ERROR);
                break;
            }
            if (Update.isRunning()) {
                Update.abort(); // a previous, never-finished attempt
            }
            uint32_t size;
            memcpy(&size, data + 1, 4);
            char md5[33];
            memcpy(md5, data + 5, 32);
            md5[32] = '\0';

            _received = 0;
            _total = size;
            _error = BLE_OTA_ERR_NONE;
            _ack_every = 0;
            if (len >= 1 + 4 + 32 + 2) {
                memcpy(&_ack_every, data + 37, 2);
            }
            _last_ack_at = 0;
            _ack_due = false;
            if (!Update.begin(size, U_FLASH)) {
                Serial.printf("[BLE-OTA] Update.begin(%lu) failed: %s\n", (unsigned long)size, Update.errorString());
                fail(BLE_OTA_ERR_BEGIN);
                break;
            }
            Update.setMD5(md5);
            _last_data_ms = millis();
            setState(BLE_OTA_RECEIVING);
            Serial.printf("[BLE-OTA] BEGIN size=%lu md5=%s ack_every=%u\n", (unsigned long)size, md5, _ack_every);
            break;
        }

        case 0x02: // END
            if (_state != BLE_OTA_RECEIVING) {
                _error = BLE_OTA_ERR_NOT_STARTED;
                setState(BLE_OTA_ERROR);
                break;
            }
            if (_received != _total) {
                fail(BLE_OTA_ERR_SIZE);
                break;
            }
            if (!Update.end()) { // checks the MD5 given at BEGIN
                Serial.printf("[BLE-OTA] Update.end failed: %s\n", Update.errorString());
                fail(BLE_OTA_ERR_VERIFY);
                break;
            }
            _success_ms = millis();
            setState(BLE_OTA_SUCCESS);
            Serial.println("[BLE-OTA] verified OK - rebooting shortly");
            break;

        case 0x03: // ABORT
            if (_state == BLE_OTA_RECEIVING) {
                fail(BLE_OTA_ERR_ABORTED);
            }
            break;

        default:
            break;
    }

    xSemaphoreGive(_lock);
}

void BleOta::onDataWrite(const uint8_t *data, size_t len) {
    xSemaphoreTake(_lock, portMAX_DELAY);
    if (_state != BLE_OTA_RECEIVING) {
        xSemaphoreGive(_lock);
        return; // stray chunks after an error/abort are expected while the app's queue drains
    }
    if (_received + len > _total || Update.write((uint8_t *)data, len) != len) {
        Serial.printf("[BLE-OTA] write failed: %s\n", Update.errorString());
        fail(BLE_OTA_ERR_WRITE);
    } else {
        _received += len;
        _last_data_ms = millis();
        if (_ack_every > 0 && (_received - _last_ack_at >= _ack_every || _received == _total)) {
            _last_ack_at = _received;
            _ack_due = true;
        }
    }
    xSemaphoreGive(_lock);
}

void BleOta::onDisconnect() {
    xSemaphoreTake(_lock, portMAX_DELAY);
    if (_state == BLE_OTA_RECEIVING) {
        fail(BLE_OTA_ERR_ABORTED);
    }
    xSemaphoreGive(_lock);
}

// Uses a fresh millis() with signed differences on purpose: _last_data_ms /
// _success_ms are written from the BLE task and can be *newer* than a `now`
// the main loop captured earlier - an unsigned `now - later` wraps to a huge
// value. That exact bug rebooted the board the instant END verified, before
// the SUCCESS notify went out, so the app reported a dropped connection
// for an update that had actually worked.
void BleOta::update(uint32_t /*now*/) {
    uint32_t t = millis();
    if (_state == BLE_OTA_RECEIVING && (int32_t)(t - _last_data_ms) > (int32_t)BLE_OTA_DATA_TIMEOUT_MS) {
        xSemaphoreTake(_lock, portMAX_DELAY);
        if (_state == BLE_OTA_RECEIVING && (int32_t)(millis() - _last_data_ms) > (int32_t)BLE_OTA_DATA_TIMEOUT_MS) {
            fail(BLE_OTA_ERR_TIMEOUT);
        }
        xSemaphoreGive(_lock);
    }
    if (_state == BLE_OTA_SUCCESS && (int32_t)(t - _success_ms) > (int32_t)BLE_OTA_REBOOT_DELAY_MS) {
        Serial.println("[BLE-OTA] rebooting into new firmware");
        Serial.flush();
        ESP.restart();
    }
}

bool BleOta::takeAckDue() {
    if (!_ack_due) {
        return false;
    }
    _ack_due = false;
    return true;
}

BleOtaWireStatus BleOta::getStatus() const {
    BleOtaWireStatus s;
    s.state = _state;
    s.error = _error;
    s.received = _received;
    s.total = _total;
    return s;
}
