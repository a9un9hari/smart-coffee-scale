#include "timemore_scale.h"
#include "config.h"

// Full 128-bit UUIDs, per the upstream Timemore Dot plugin.
static const NimBLEUUID TIMEMORE_SERVICE_UUID("0000fff0-0000-1000-8000-00805f9b34fb");
static const NimBLEUUID TIMEMORE_NOTIFY_CHAR_UUID("0000fff1-0000-1000-8000-00805f9b34fb");
static const NimBLEUUID TIMEMORE_COMMAND_CHAR_UUID("0000fff2-0000-1000-8000-00805f9b34fb");

static const char *TIMEMORE_DEVICE_NAME_MATCH = "TIMEMORE_Dot";
static const uint32_t TIMEMORE_RECONNECT_INTERVAL_MS = 5000;
static const uint32_t TIMEMORE_SCAN_DURATION_S = 5; // one scan window; update() restarts it on the throttle above

TimemoreScale *TimemoreScale::_instance = nullptr;

// NimBLE stops advertising/scanning-adjacent activity around a connect
// attempt - onDisconnect is our only reliable signal that the link dropped,
// same reasoning as ServerCallbacks::onDisconnect in ble.cpp for the
// peripheral side.
class TimemoreScale::ClientCallbacks : public NimBLEClientCallbacks {
public:
    explicit ClientCallbacks(TimemoreScale *owner) : _owner(owner) {}

    void onDisconnect(NimBLEClient *client) override {
        Serial.println("[Timemore] disconnected");
        _owner->_connected = false;
        _owner->_notify_char = nullptr;
        _owner->_command_char = nullptr;
    }

private:
    TimemoreScale *_owner;
};

class TimemoreScale::ScanCallbacks : public NimBLEAdvertisedDeviceCallbacks {
public:
    explicit ScanCallbacks(TimemoreScale *owner) : _owner(owner) {}

    void onResult(NimBLEAdvertisedDevice *device) override {
#if DEBUG_TIMEMORE_SCAN
        // Name-blank devices are normal (many peripherals only reveal a name
        // in the connect-time GATT database, not the ad packet) - the
        // service UUID check catches the Dot even if its name never shows
        // up here (advertised service 0xFFF0 per timemore_new.cpp upstream).
        bool has_timemore_service = device->isAdvertisingService(NimBLEUUID((uint16_t)0xFFF0));
        Serial.printf("[Timemore] scan saw \"%s\" [%s] rssi=%d svcFFF0=%d\n",
                       device->getName().c_str(), device->getAddress().toString().c_str(),
                       device->getRSSI(), has_timemore_service ? 1 : 0);
#endif
        if (!_owner->_enabled || _owner->_connected || _owner->_connecting) {
            return;
        }
        if (device->getName().find(TIMEMORE_DEVICE_NAME_MATCH) == std::string::npos) {
            return;
        }
        // Don't call NimBLEClient::connect() here - this callback runs on
        // NimBLE's own host task, and connect() blocks waiting for a GAP
        // event that only that same host task can deliver (self-deadlock).
        // Hand off to update() (Arduino loop task) instead.
        NimBLEDevice::getScan()->stop();
        _owner->_connecting = true;
        _owner->_pending_address = device->getAddress();
        _owner->_pending_connect = true;
    }

private:
    TimemoreScale *_owner;
};

// NimBLEScan::start() is overloaded: start(duration, bool is_continue) BLOCKS
// the calling task until the scan finishes (used for one-shot foreground
// scans), while start(duration, callback, bool) runs asynchronously and
// fires ScanCallbacks::onResult() from the NimBLE host task in the
// background - the one we want here, called every loop() from update().
// Passing a bare `false` as the 2nd arg resolves to the *blocking* overload
// (exact bool match beats the null-pointer-constant match for the callback
// param), which silently stalled scanning - always pass an explicit
// function-pointer type for the 2nd arg to force the async overload.
static void startAsyncScan() {
    NimBLEDevice::getScan()->start(TIMEMORE_SCAN_DURATION_S, (void (*)(NimBLEScanResults))nullptr, false);
}

void TimemoreScale::begin() {
    _instance = this;

    // Timemore Dot requires bonded/secure pairing to accept the handshake
    // (mirrors the official app's createBond() behavior) - this only
    // configures what NimBLEDevice offers/requests when *we* initiate as a
    // central, it does not force encryption on BleServer's own peripheral
    // characteristics (those stay open, no bonding requested of the app).
    NimBLEDevice::setSecurityAuth(true, false, true);
    NimBLEDevice::setSecurityIOCap(BLE_HS_IO_NO_INPUT_OUTPUT);

    NimBLEScan *scan = NimBLEDevice::getScan();
    scan->setAdvertisedDeviceCallbacks(new ScanCallbacks(this));
    scan->setActiveScan(true);
    scan->setInterval(100);
    scan->setWindow(99);
    startAsyncScan();
}

void TimemoreScale::setEnabled(bool enabled) {
    if (enabled == _enabled) {
        return;
    }
    _enabled = enabled;

    if (!enabled) {
        NimBLEDevice::getScan()->stop();
        if (_client != nullptr) {
            if (_client->isConnected()) {
                _client->disconnect();
            }
            NimBLEDevice::deleteClient(_client);
            _client = nullptr;
        }
        _notify_char = nullptr;
        _command_char = nullptr;
        _connected = false;
        _connecting = false;
        _pending_connect = false;
        Serial.println("[Timemore] auto-connect disabled, disconnected");
    } else {
        _last_reconnect_attempt_ms = 0; // let update() scan immediately instead of waiting out the reconnect throttle
        Serial.println("[Timemore] auto-connect enabled");
    }
}

void TimemoreScale::update() {
#if DEBUG_TIMEMORE_SCAN
    {
        static uint32_t last_heartbeat_ms = 0;
        uint32_t now_hb = millis();
        if (now_hb - last_heartbeat_ms >= 3000) {
            last_heartbeat_ms = now_hb;
            Serial.printf("[Timemore] heartbeat enabled=%d connecting=%d connected=%d client_isConnected=%d weight=%.1f\n",
                          _enabled, _connecting, _connected,
                          _client != nullptr ? _client->isConnected() : -1, _weight_g);
        }
    }
#endif

    if (!_enabled) {
        return;
    }

    // Runs on the Arduino loop task (not NimBLE's host task) - safe to make
    // the blocking connect() call here. See ScanCallbacks::onResult().
    if (_pending_connect) {
        _pending_connect = false;
        connectToDevice(_pending_address);
        return;
    }

    if (_connected && (_client == nullptr || !_client->isConnected())) {
        _connected = false; // safety net in case onDisconnect was missed
    }

    if (_connected || _connecting) {
        return;
    }

    uint32_t now = millis();
    if (now - _last_reconnect_attempt_ms < TIMEMORE_RECONNECT_INTERVAL_MS) {
        return;
    }
    _last_reconnect_attempt_ms = now;

    if (_client != nullptr) {
        NimBLEDevice::deleteClient(_client);
        _client = nullptr;
    }
    startAsyncScan();
}

void TimemoreScale::connectToDevice(const NimBLEAddress &address) {
    _connecting = true;
    Serial.printf("[Timemore] connecting to %s\n", address.toString().c_str());

    _client = NimBLEDevice::createClient();
    _client->setClientCallbacks(new ClientCallbacks(this), false);
    // Default is 30s - connect() BLOCKS the whole GrinderController::update()
    // loop (HX711 reads, BLE peripheral notifies, motor safety) for up to
    // that long on a failed/hung attempt. 7s still gives the GAP connect +
    // bonding handshake room to complete, without stalling everything else
    // for half a minute if the Dot doesn't respond.
    _client->setConnectTimeout(7);

    bool ok = _client->connect(address);
    if (!ok) {
        // Most common cause: a stale bond from a previous pairing (e.g. the
        // scale was factory reset) - upstream's fix is to drop it and retry
        // once before giving up.
        Serial.printf("[Timemore] connect failed (err=%d), clearing bond and retrying once\n", _client->getLastError());
        NimBLEDevice::deleteBond(address);
        delay(500);
        ok = _client->connect(address);
        if (!ok) {
            Serial.printf("[Timemore] retry also failed (err=%d)\n", _client->getLastError());
        }
    }

    // NimBLEClient::connect() only establishes the plain GAP link - it does
    // NOT initiate pairing/bonding on its own, even with setSecurityAuth()
    // configured. Without this, the link stays unencrypted: the Dot won't
    // fully expose its GATT database (CCCD discovery silently comes back
    // empty) and won't trust us enough to actually stream weight notifies,
    // even though every step up to here appears to succeed.
    bool secure_ok = ok && _client->secureConnection();
    if (ok && !secure_ok) {
        Serial.println("[Timemore] secureConnection failed");
    }

    bool handshake_ok = ok && secure_ok && performHandshake();
    if (!ok || !secure_ok || !handshake_ok) {
        Serial.printf("[Timemore] giving up: connect_ok=%d secure_ok=%d handshake_ok=%d\n", ok, secure_ok, handshake_ok);
        NimBLEDevice::deleteClient(_client);
        _client = nullptr;
        _connecting = false;
        return;
    }

    _connected = true;
    _connecting = false;
    Serial.println("[Timemore] connected");
}

bool TimemoreScale::performHandshake() {
    NimBLERemoteService *service = _client->getService(TIMEMORE_SERVICE_UUID);
    if (service == nullptr) {
        Serial.println("[Timemore] service not found");
        return false;
    }

    _notify_char = service->getCharacteristic(TIMEMORE_NOTIFY_CHAR_UUID);
    _command_char = service->getCharacteristic(TIMEMORE_COMMAND_CHAR_UUID);
    if (_notify_char == nullptr || _command_char == nullptr) {
        Serial.println("[Timemore] characteristics not found");
        return false;
    }

    if (!_notify_char->canNotify() || !_notify_char->subscribe(true, TimemoreScale::notifyTrampoline)) {
        Serial.println("[Timemore] subscribe failed");
        return false;
    }

    _rx_buffer.clear();

    // The 6 initialization queries the official app sends after connecting,
    // to wake up the weight data stream - see timemore_new.cpp upstream.
    // 160ms between writes matches the vendor-specified pacing.
    static const uint8_t init_queries[] = {19, 8, 5, 2, 6, 12};
    for (uint8_t query : init_queries) {
        sendQueryCommand(query);
        delay(160);
    }

    return true;
}

void TimemoreScale::sendQueryCommand(uint8_t query_type) {
    if (_command_char == nullptr) {
        return;
    }
    uint8_t payload[8] = {0xA5, 0x5A, 0x02 /* QUERY_CMD */, query_type, 0x00, 0x00, 0x00, 0x00};
    bool write_ok = _command_char->writeValue(payload, sizeof(payload), true);
#if DEBUG_TIMEMORE_SCAN
    Serial.printf("[Timemore] query %d write_ok=%d\n", query_type, write_ok);
#endif
}

void TimemoreScale::tare() {
    if (!_connected || _command_char == nullptr) {
        return;
    }
    uint8_t packet[8] = {0xA5, 0x5A, 0x03 /* CTRL_CMD */, 0x0D /* TARE_ACTION */, 0x00, 0x00, 0x00, 0x00};
    uint16_t crc = crc16(packet, 6);
    packet[6] = crc & 0xFF;
    packet[7] = (crc >> 8) & 0xFF;
    _command_char->writeValue(packet, sizeof(packet), false);
}

void TimemoreScale::notifyTrampoline(NimBLERemoteCharacteristic *characteristic, uint8_t *data, size_t length, bool is_notify) {
#if DEBUG_TIMEMORE_SCAN
    {
        char hex[3 * 32 + 1] = {0};
        size_t n = length < 32 ? length : 32;
        for (size_t i = 0; i < n; i++) {
            snprintf(hex + i * 3, 4, "%02X ", data[i]);
        }
        Serial.printf("[Timemore] notify len=%d data=%s\n", (int)length, hex);
    }
#endif
    if (_instance == nullptr) {
        return;
    }
    _instance->_rx_buffer.insert(_instance->_rx_buffer.end(), data, data + length);
    while (_instance->decodeNextFrame()) {
        // drains every complete frame already sitting in the buffer
    }
}

bool TimemoreScale::decodeNextFrame() {
    if (_rx_buffer.size() < 2) {
        return false;
    }

    int header_index = -1;
    for (size_t i = 0; i + 1 < _rx_buffer.size(); i++) {
        if (_rx_buffer[i] == 0xA5 && _rx_buffer[i + 1] == 0x5A) {
            header_index = (int)i;
            break;
        }
    }
    if (header_index == -1) {
        _rx_buffer.clear(); // no header anywhere in the buffer - discard garbage
        return false;
    }
    if (header_index > 0) {
        _rx_buffer.erase(_rx_buffer.begin(), _rx_buffer.begin() + header_index);
    }

    if (_rx_buffer.size() < 6) {
        return false; // header found, but length field not in yet
    }

    uint16_t payload_len = (_rx_buffer[4] << 8) | _rx_buffer[5];
    size_t full_len = 6 + payload_len + 2; // header(2) + type(2) + length(2) + payload(N) + CRC(2)
    if (_rx_buffer.size() < full_len) {
        return false; // wait for the rest of this frame
    }

    handleFrame(_rx_buffer.data(), full_len);
    _rx_buffer.erase(_rx_buffer.begin(), _rx_buffer.begin() + full_len);
    return _rx_buffer.size() >= 6;
}

void TimemoreScale::handleFrame(const uint8_t *frame, size_t len) {
    // WEIGHT_DATA (type 0x01, subtype 0x01): 32-bit signed big-endian raw
    // weight at payload offset 0 (frame offset 6), tenths of a gram.
    if (frame[2] == 0x01 && frame[3] == 0x01 && len >= 10) {
        int32_t raw = ((int32_t)frame[6] << 24) | ((int32_t)frame[7] << 16) |
                      ((int32_t)frame[8] << 8) | (int32_t)frame[9];
        _weight_g = raw / 10.0f;
#if DEBUG_TIMEMORE_SCAN
        Serial.printf("[Timemore] weight=%.1fg\n", _weight_g);
#endif
    }
}

uint16_t TimemoreScale::crc16(const uint8_t *data, size_t length) {
    // Standard MODBUS-style CRC16: init 0x0000, poly 0xA001, LSB-first -
    // only used for outgoing command packets (tare), incoming frames aren't
    // CRC-checked here, matching upstream.
    uint16_t crc = 0x0000;
    for (size_t i = 0; i < length; i++) {
        crc ^= data[i];
        for (int bit = 0; bit < 8; bit++) {
            if (crc & 0x0001) {
                crc = (crc >> 1) ^ 0xA001;
            } else {
                crc >>= 1;
            }
        }
    }
    return crc;
}
