#include "ble.h"

// Custom UUIDs - private to this device, not registered with the Bluetooth SIG.
#define GRINDER_SERVICE_UUID     "c4a10000-1000-4a4a-8a1a-2f5e9b6d0000"
#define STATUS_CHAR_UUID         "c4a10000-1000-4a4a-8a1a-2f5e9b6d0001"
#define COMMAND_CHAR_UUID        "c4a10000-1000-4a4a-8a1a-2f5e9b6d0002"
#define CUP_PROFILE_CHAR_UUID    "c4a10000-1000-4a4a-8a1a-2f5e9b6d0003"

#pragma pack(push, 1)
struct BleStatusWire {
    float weight_g;
    float target_weight_g;
    uint8_t mode;
    uint8_t state;
    uint8_t error_code;
    uint8_t active_cup_profile_id;
};

struct BleCupProfileWire {
    float cup_weight_g;
    float tolerance_g;
    char name[12]; // null-terminated within this fixed span
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
                break;
            case BLE_OP_SELECT_CUP_PROFILE:
                if (raw.size() >= 2) cmd.id = (uint8_t)raw[1];
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

void BleServer::begin() {
    _command_queue = xQueueCreate(8, sizeof(BleCommand));

    NimBLEDevice::init("SmartGrinder");

    NimBLEServer *server = NimBLEDevice::createServer();
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

void BleServer::notifyStatus(const SystemStatus &status, uint8_t active_cup_profile_id) {
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

    _status_char->setValue((uint8_t *)&wire, sizeof(wire));
    _status_char->notify();
}
