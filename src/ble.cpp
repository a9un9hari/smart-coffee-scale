#include "ble.h"

// Custom UUIDs - private to this device, not registered with the Bluetooth SIG.
#define GRINDER_SERVICE_UUID     "c4a10000-1000-4a4a-8a1a-2f5e9b6d0000"
#define STATUS_CHAR_UUID         "c4a10000-1000-4a4a-8a1a-2f5e9b6d0001"
#define COMMAND_CHAR_UUID        "c4a10000-1000-4a4a-8a1a-2f5e9b6d0002"
#define CUP_PROFILE_CHAR_UUID    "c4a10000-1000-4a4a-8a1a-2f5e9b6d0003"
#define CALIBRATION_STATUS_CHAR_UUID "c4a10000-1000-4a4a-8a1a-2f5e9b6d0004"
// ...0005 / ...0006 were the WiFi OTA config/status characteristics (removed).
#define GRIND_LOG_CHAR_UUID      "c4a10000-1000-4a4a-8a1a-2f5e9b6d0007"
#define BLE_OTA_CTRL_CHAR_UUID   "c4a10000-1000-4a4a-8a1a-2f5e9b6d0008"
#define BLE_OTA_DATA_CHAR_UUID   "c4a10000-1000-4a4a-8a1a-2f5e9b6d0009"

#pragma pack(push, 1)
struct BleStatusWire {
    float weight_g;
    float target_weight_g;
    uint8_t mode;
    uint8_t state;
    uint8_t error_code;
    uint8_t active_cup_profile_id;
    uint8_t weight_source;      // WeightSource - which sensor current_weight_g came from
    uint8_t timemore_connected; // 0/1 - Timemore Dot BLE central link state, independent of weight_source
    uint8_t timemore_autoconnect; // 0/1 - whether TimemoreScale's scan+reconnect loop is enabled
    uint8_t hx711_detected;       // 0/1 - whether the load cell is physically wired up
};

struct BleCupProfileWire {
    float cup_weight_g;
    float tolerance_g;
    char name[12]; // null-terminated within this fixed span
};

struct BleCalibrationStatusWire {
    uint8_t point_count;
    uint8_t last_save_ok; // 0/1, only meaningful right after a SAVE
    float last_point_raw;
    float last_point_weight_g;
};

#pragma pack(pop)

class CommandCallbacks : public NimBLECharacteristicCallbacks {
public:
    explicit CommandCallbacks(BleServer *server) : _server(server) {}

    void onWrite(NimBLECharacteristic *characteristic) override {
        std::string raw = characteristic->getValue();
        if (raw.empty()) {
            return;
        }

        BleCommand cmd = {};
        cmd.opcode = (uint8_t)raw[0];
        Serial.printf("[BLE] command write received: opcode=%d len=%d\n", cmd.opcode, (int)raw.size());

        switch (cmd.opcode) {
            case BLE_OP_SET_TARGET_WEIGHT:
                if (raw.size() >= 5) memcpy(&cmd.value_a, raw.data() + 1, 4);
                break;
            case BLE_OP_SET_MODE:
                if (raw.size() >= 2) cmd.mode = (uint8_t)raw[1];
                break;
            case BLE_OP_START:
            case BLE_OP_STOP:
            case BLE_OP_EMERGENCY_STOP:
            case BLE_OP_TARE:
            case BLE_OP_CAL_CLEAR:
            case BLE_OP_CAL_SAVE:
                break;
            case BLE_OP_CAL_ADD_POINT:
                if (raw.size() >= 5) memcpy(&cmd.value_a, raw.data() + 1, 4);
                break;
            case BLE_OP_SET_SMOOTHING_ALPHA:
                if (raw.size() >= 5) memcpy(&cmd.value_a, raw.data() + 1, 4);
                break;
            case BLE_OP_SELECT_CUP_PROFILE:
                if (raw.size() >= 2) cmd.id = (uint8_t)raw[1];
                break;
            case BLE_OP_SET_WEIGHT_SOURCE:
                if (raw.size() >= 2) cmd.mode = (uint8_t)raw[1];
                break;
            case BLE_OP_SET_TIMEMORE_AUTOCONNECT:
                if (raw.size() >= 2) cmd.id = (uint8_t)raw[1]; // reuses the id byte as a generic 0/1 payload
                break;
            case BLE_OP_SET_CUP_PROFILE_WEIGHT:
                if (raw.size() >= 10) {
                    cmd.id = (uint8_t)raw[1];
                    memcpy(&cmd.value_a, raw.data() + 2, 4);
                    memcpy(&cmd.value_b, raw.data() + 6, 4);
                }
                break;
            case BLE_OP_SET_CUP_PROFILE_NAME:
                if (raw.size() >= 2) {
                    cmd.id = (uint8_t)raw[1];
                    size_t name_len = raw.size() - 2;
                    if (name_len > sizeof(cmd.name) - 1) name_len = sizeof(cmd.name) - 1;
                    memcpy(cmd.name, raw.data() + 2, name_len);
                    cmd.name[name_len] = '\0';
                }
                break;
            default:
                return; // unknown opcode, drop
        }

        QueueHandle_t queue = _server->getCommandQueue();
        if (queue != nullptr) {
            xQueueSend(queue, &cmd, 0); // never block the BLE task; drop if full
        }
    }

private:
    BleServer *_server;
};

class CupProfileQueryCallbacks : public NimBLECharacteristicCallbacks {
public:
    explicit CupProfileQueryCallbacks(BleServer *server) : _server(server) {}

    void onWrite(NimBLECharacteristic *characteristic) override {
        std::string raw = characteristic->getValue();
        if (!raw.empty()) {
            _server->setQueriedProfileId((uint8_t)raw[0]);
        }
    }

    void onRead(NimBLECharacteristic *characteristic) override {
        const CalibrationData *cal = _server->getCalibration();
        uint8_t id = _server->getQueriedProfileId();

        BleCupProfileWire wire = {};
        if (cal != nullptr && id < CUP_PROFILE_COUNT) {
            wire.cup_weight_g = cal->cup_profiles[id].cup_weight_g;
            wire.tolerance_g = cal->cup_profiles[id].tolerance_g;
            memcpy(wire.name, cal->cup_profiles[id].name, sizeof(wire.name) - 1);
            wire.name[sizeof(wire.name) - 1] = '\0';
        }
        characteristic->setValue((uint8_t *)&wire, sizeof(wire));
    }

private:
    BleServer *_server;
};

// Write: uint32 after_boot + uint32 after_uptime_s (little-endian) - a sync
// request for every grind-log row newer than that cursor. Only recorded
// here; GrinderController picks it up from the main loop, which is also
// where the rows are read from flash and notified back (one CSV row per
// notify, then "#END").
class GrindLogCallbacks : public NimBLECharacteristicCallbacks {
public:
    explicit GrindLogCallbacks(BleServer *server) : _server(server) {}

    void onWrite(NimBLECharacteristic *characteristic) override {
        std::string raw = characteristic->getValue();
        if (raw.size() < 8) {
            return;
        }
        uint32_t after_boot, after_uptime_s;
        memcpy(&after_boot, raw.data(), 4);
        memcpy(&after_uptime_s, raw.data() + 4, 4);
        _server->setGrindLogRequest(after_boot, after_uptime_s);
    }

private:
    BleServer *_server;
};

// BleOtaCtrl / BleOtaData writes go straight into BleOta (see ble_ota.h) -
// it guards its own state with a mutex, since the main loop also touches
// it (timeout, reboot).
class BleOtaCtrlCallbacks : public NimBLECharacteristicCallbacks {
public:
    explicit BleOtaCtrlCallbacks(BleServer *server) : _server(server) {}

    void onWrite(NimBLECharacteristic *characteristic) override {
        BleOta *ota = _server->getBleOta();
        if (ota == nullptr) {
            return;
        }
        std::string raw = characteristic->getValue();
        ota->onControlWrite((const uint8_t *)raw.data(), raw.size());
    }

private:
    BleServer *_server;
};

class BleOtaDataCallbacks : public NimBLECharacteristicCallbacks {
public:
    explicit BleOtaDataCallbacks(BleServer *server) : _server(server) {}

    void onWrite(NimBLECharacteristic *characteristic) override {
        BleOta *ota = _server->getBleOta();
        if (ota == nullptr) {
            return;
        }
        std::string raw = characteristic->getValue();
        ota->onDataWrite((const uint8_t *)raw.data(), raw.size());
        if (ota->takeAckDue()) {
            _server->notifyBleOtaStatusNow(); // app is waiting on this to send the next window
        }
    }

private:
    BleServer *_server;
};

// NimBLE stops advertising once a central connects, and does NOT resume it
// automatically on disconnect - without this, the device becomes invisible
// to everyone else (or even to the same central reconnecting) the moment
// any one client disconnects, until the board is rebooted.
class ServerCallbacks : public NimBLEServerCallbacks {
public:
    explicit ServerCallbacks(BleServer *owner) : _owner(owner) {}

    void onDisconnect(NimBLEServer *server) override {
        NimBLEDevice::getAdvertising()->start();
        if (_owner->getBleOta() != nullptr) {
            _owner->getBleOta()->onDisconnect(); // a half-sent image can't resume
        }
    }
    void onMTUChange(uint16_t mtu, ble_gap_conn_desc *desc) override {
        Serial.printf("[BLE] MTU negotiated: %d\n", mtu);
    }

private:
    BleServer *_owner;
};

void BleServer::begin() {
    _command_queue = xQueueCreate(8, sizeof(BleCommand));

    NimBLEDevice::init("SmartGrinder");

    // Default MTU (23 bytes) is enough for every fixed-size command frame,
    // but BLE OTA chunks and grind-log rows need more - raised once here.
    // Purely additive: every <=20-byte payload still works with no
    // negotiation required on the app side.
    NimBLEDevice::setMTU(247);

    NimBLEServer *server = NimBLEDevice::createServer();
    server->setCallbacks(new ServerCallbacks(this));
    NimBLEService *service = server->createService(GRINDER_SERVICE_UUID);

    _status_char = service->createCharacteristic(
        STATUS_CHAR_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);

    NimBLECharacteristic *command_char = service->createCharacteristic(
        COMMAND_CHAR_UUID,
        NIMBLE_PROPERTY::WRITE);
    command_char->setCallbacks(new CommandCallbacks(this));

    NimBLECharacteristic *cup_profile_char = service->createCharacteristic(
        CUP_PROFILE_CHAR_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE);
    cup_profile_char->setCallbacks(new CupProfileQueryCallbacks(this));

    _calibration_status_char = service->createCharacteristic(
        CALIBRATION_STATUS_CHAR_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);

    _grind_log_char = service->createCharacteristic(
        GRIND_LOG_CHAR_UUID,
        NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::NOTIFY);
    _grind_log_char->setCallbacks(new GrindLogCallbacks(this));

    _ble_ota_ctrl_char = service->createCharacteristic(
        BLE_OTA_CTRL_CHAR_UUID,
        NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::NOTIFY);
    _ble_ota_ctrl_char->setCallbacks(new BleOtaCtrlCallbacks(this));

    NimBLECharacteristic *ble_ota_data_char = service->createCharacteristic(
        BLE_OTA_DATA_CHAR_UUID,
        NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
    ble_ota_data_char->setCallbacks(new BleOtaDataCallbacks(this));

    service->start();

    NimBLEAdvertising *advertising = NimBLEDevice::getAdvertising();
    advertising->addServiceUUID(GRINDER_SERVICE_UUID);
    advertising->start();
}

bool BleServer::popCommand(BleCommand &out) {
    if (_command_queue == nullptr) {
        return false;
    }
    return xQueueReceive(_command_queue, &out, 0) == pdTRUE;
}

void BleServer::notifyStatus(const SystemStatus &status, uint8_t active_cup_profile_id,
                              uint8_t weight_source, bool timemore_connected,
                              bool timemore_autoconnect, bool hx711_detected) {
    if (_status_char == nullptr) {
        return;
    }

    uint32_t now = millis();
    if (now - _last_notify_ms < BLE_NOTIFY_INTERVAL_MS) {
        return;
    }
    _last_notify_ms = now;

    BleStatusWire wire;
    wire.weight_g = status.current_weight_g;
    wire.target_weight_g = status.target_weight_g;
    wire.mode = (uint8_t)status.mode;
    wire.state = (uint8_t)status.state;
    wire.error_code = status.error_code;
    wire.active_cup_profile_id = active_cup_profile_id;
    wire.weight_source = weight_source;
    wire.timemore_connected = timemore_connected ? 1 : 0;
    wire.timemore_autoconnect = timemore_autoconnect ? 1 : 0;
    wire.hx711_detected = hx711_detected ? 1 : 0;

    _status_char->setValue((uint8_t *)&wire, sizeof(wire));
    _status_char->notify();
}

void BleServer::notifyBleOtaStatus() {
    if (_ble_ota_ctrl_char == nullptr || _ble_ota == nullptr) {
        return;
    }
    uint32_t now = millis();
    uint32_t version = _ble_ota->getStateVersion();
    bool state_changed = version != _last_ble_ota_state_version;
    if (!state_changed && (!_ble_ota->isActive() || now - _last_ble_ota_notify_ms < BLE_OTA_NOTIFY_INTERVAL_MS)) {
        return; // nothing new, or a progress tick inside the throttle window
    }
    _last_ble_ota_notify_ms = now;
    _last_ble_ota_state_version = version;

    BleOtaWireStatus wire = _ble_ota->getStatus();
    _ble_ota_ctrl_char->setValue((uint8_t *)&wire, sizeof(wire));
    _ble_ota_ctrl_char->notify();
}

void BleServer::notifyBleOtaStatusNow() {
    if (_ble_ota_ctrl_char == nullptr || _ble_ota == nullptr) {
        return;
    }
    BleOtaWireStatus wire = _ble_ota->getStatus();
    _ble_ota_ctrl_char->setValue((uint8_t *)&wire, sizeof(wire));
    _ble_ota_ctrl_char->notify();
}

void BleServer::setGrindLogRequest(uint32_t after_boot, uint32_t after_uptime_s) {
    _grind_log_req_boot = after_boot;
    _grind_log_req_uptime_s = after_uptime_s;
    _grind_log_req_pending = true; // set last - main loop reads the cursor only after seeing this
}

bool BleServer::takeGrindLogRequest(uint32_t &after_boot, uint32_t &after_uptime_s) {
    if (!_grind_log_req_pending) {
        return false;
    }
    after_boot = _grind_log_req_boot;
    after_uptime_s = _grind_log_req_uptime_s;
    _grind_log_req_pending = false;
    return true;
}

void BleServer::notifyGrindLogLine(const char *line) {
    if (_grind_log_char == nullptr) {
        return;
    }
    _grind_log_char->setValue((const uint8_t *)line, strlen(line));
    _grind_log_char->notify();
}

void BleServer::notifyCalibrationStatus(uint8_t point_count, bool last_save_ok, float last_point_raw, float last_point_weight_g) {
    if (_calibration_status_char == nullptr) {
        return;
    }

    BleCalibrationStatusWire wire;
    wire.point_count = point_count;
    wire.last_save_ok = last_save_ok ? 1 : 0;
    wire.last_point_raw = last_point_raw;
    wire.last_point_weight_g = last_point_weight_g;

    _calibration_status_char->setValue((uint8_t *)&wire, sizeof(wire));
    _calibration_status_char->notify();
}

