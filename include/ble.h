#ifndef BLE_H
#define BLE_H

#include <Arduino.h>
#include <NimBLEDevice.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include "data_types.h"

// All ATT payloads kept <=20 bytes so the default BLE MTU (23 bytes total,
// 20 usable) is always enough - no MTU negotiation needed on either side.
enum BleOpcode : uint8_t {
    BLE_OP_SET_TARGET_WEIGHT     = 1, // + float grams
    BLE_OP_SET_MODE              = 2, // + uint8 SystemMode
    BLE_OP_START                 = 3, // no payload
    BLE_OP_STOP                  = 4, // no payload
    BLE_OP_EMERGENCY_STOP        = 5, // no payload
    BLE_OP_SELECT_CUP_PROFILE    = 6, // + uint8 id
    BLE_OP_SET_CUP_PROFILE_WEIGHT = 7, // + uint8 id, float cup_weight_g, float tolerance_g
    BLE_OP_SET_CUP_PROFILE_NAME  = 8, // + uint8 id, char name[<=11] (rest of payload)
    BLE_OP_TARE                  = 9,  // no payload - zero the scale at its current (empty) load
    BLE_OP_CAL_CLEAR             = 10, // no payload - discard any in-progress calibration points
    BLE_OP_CAL_ADD_POINT         = 11, // + float known_weight_g - capture current raw ADC paired with this weight
    BLE_OP_CAL_SAVE              = 12, // no payload - least-squares fit over captured points, apply + persist
};

// Parsed form of a Command characteristic write, handed to
// GrinderController::update() one at a time via popCommand().
struct BleCommand {
    uint8_t opcode;
    uint8_t id;       // cup profile id, when relevant
    uint8_t mode;      // SystemMode, when relevant
    float value_a;     // target_weight_g OR cup_weight_g
    float value_b;      // tolerance_g
    char name[12];       // null-terminated, when relevant
};

class BleServer {
public:
    void begin();

    // Attach the live CalibrationData so cup-profile read requests can be
    // served directly from the BLE task without going through the queue -
    // mutations to it still only ever happen from the main loop (in
    // GrinderController, after popCommand()).
    void attachCalibration(const CalibrationData *calibration) { _calibration = calibration; }

    bool popCommand(BleCommand &out); // true if a command was dequeued into out

    void notifyStatus(const SystemStatus &status, uint8_t active_cup_profile_id); // internally rate-limited

    // point_count/last_point_raw/last_point_weight_g reflect the in-progress
    // calibration session; last_save_ok is only meaningful right after a SAVE.
    void notifyCalibrationStatus(uint8_t point_count, bool last_save_ok, float last_point_raw, float last_point_weight_g);

    // Called by the CupProfileQuery characteristic's write callback to
    // record which profile id a subsequent read should serve.
    void setQueriedProfileId(uint8_t id) { _queried_profile_id = id; }
    uint8_t getQueriedProfileId() const { return _queried_profile_id; }
    const CalibrationData *getCalibration() const { return _calibration; }

    QueueHandle_t getCommandQueue() const { return _command_queue; }

private:
    const CalibrationData *_calibration = nullptr;
    QueueHandle_t _command_queue = nullptr;
    uint8_t _queried_profile_id = 0;
    uint32_t _last_notify_ms = 0;

    NimBLECharacteristic *_status_char = nullptr;
    NimBLECharacteristic *_calibration_status_char = nullptr;
};

#endif // BLE_H
