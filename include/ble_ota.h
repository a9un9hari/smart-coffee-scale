#ifndef BLE_OTA_H
#define BLE_OTA_H

#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/semphr.h>

// Firmware update streamed straight from the app over BLE - no WiFi, no
// HTTP server, no router in the loop (the old WiFi OTA kept failing on
// exactly those and was removed, which also cut the image from 1.17MB to
// ~690KB). The grinder lives inside the machine on its own 5V supply, so
// this is the only wireless update path; USB is the fallback.
//
// Protocol (see BleServer for the characteristics):
//   BleOtaCtrl write  0x01 BEGIN  + uint32 size (LE) + 32 ASCII hex MD5
//                                  [+ uint16 ack_every (LE), optional]
//                     0x02 END    - verify MD5, mark the new slot bootable
//                     0x03 ABORT
//   BleOtaData write  raw image bytes, in order, any chunk size
//   BleOtaCtrl notify BleOtaWireStatus (state, error, bytes received)
// With ack_every set, the app streams data as write-without-response and
// pauses every ack_every bytes until a status notify confirms they all
// landed - sent immediately from the data callback, not throttled. That
// windowing is what makes the transfer several times faster than one
// acknowledged write per chunk, while a lost chunk still shows up within
// one window instead of only as an MD5 failure at the very end.
// On END success the board reboots into the new image ~1s later. Anything
// short of a verified END leaves the running firmware untouched - the new
// slot only becomes bootable inside Update.end().
enum BleOtaState : uint8_t {
    BLE_OTA_IDLE      = 0,
    BLE_OTA_RECEIVING = 1,
    BLE_OTA_SUCCESS   = 2, // verified, rebooting
    BLE_OTA_ERROR     = 3,
};

enum BleOtaError : uint8_t {
    BLE_OTA_ERR_NONE        = 0,
    BLE_OTA_ERR_BUSY        = 1, // grinder running - refused
    BLE_OTA_ERR_BEGIN       = 2, // Update.begin failed (too big / no OTA slot)
    BLE_OTA_ERR_WRITE       = 3, // flash write failed
    BLE_OTA_ERR_SIZE        = 4, // END before all declared bytes arrived
    BLE_OTA_ERR_VERIFY      = 5, // MD5 mismatch / Update.end failed
    BLE_OTA_ERR_TIMEOUT     = 6, // no data for BLE_OTA_DATA_TIMEOUT_MS
    BLE_OTA_ERR_ABORTED     = 7, // app sent ABORT, or disconnected mid-transfer
    BLE_OTA_ERR_NOT_STARTED = 8, // data/END without a BEGIN
    BLE_OTA_ERR_BAD_REQUEST = 9, // malformed BEGIN
};

#pragma pack(push, 1)
struct BleOtaWireStatus {
    uint8_t state;     // BleOtaState
    uint8_t error;     // BleOtaError
    uint32_t received; // bytes written so far
    uint32_t total;    // size declared in BEGIN
};
#pragma pack(pop)

class BleOta {
public:
    void begin();

    // GrinderController sets this every loop: false while a grind/pull is
    // running, so BEGIN is refused rather than pausing a motor mid-run.
    void setAllowed(bool allowed) { _allowed = allowed; }
    bool isActive() const { return _state == BLE_OTA_RECEIVING; }

    // Called from the NimBLE host task (characteristic write callbacks).
    void onControlWrite(const uint8_t *data, size_t len);
    void onDataWrite(const uint8_t *data, size_t len);
    void onDisconnect(); // an interrupted transfer can't resume - abort it

    // Main loop: data timeout + the post-success reboot.
    void update(uint32_t now);

    BleOtaWireStatus getStatus() const;
    // True once per completed ack window (see ack_every) - the data write
    // callback then notifies status right away.
    bool takeAckDue();
    // Bumped on every state change so the notifier can bypass its throttle
    // for transitions (only same-state progress updates get throttled).
    uint32_t getStateVersion() const { return _state_version; }

private:
    SemaphoreHandle_t _lock = nullptr; // Update.* from the BLE task vs. abort from the main loop

    volatile bool _allowed = true;
    volatile BleOtaState _state = BLE_OTA_IDLE;
    volatile BleOtaError _error = BLE_OTA_ERR_NONE;
    volatile uint32_t _received = 0;
    volatile uint32_t _total = 0;
    volatile uint32_t _last_data_ms = 0;
    volatile uint32_t _success_ms = 0;
    volatile uint32_t _state_version = 0;
    uint16_t _ack_every = 0;      // 0 = app doesn't window (older app), no immediate acks
    uint32_t _last_ack_at = 0;
    volatile bool _ack_due = false;

    void fail(BleOtaError err); // caller holds _lock
    void setState(BleOtaState s) { _state = s; _state_version++; }
};

#endif // BLE_OTA_H
