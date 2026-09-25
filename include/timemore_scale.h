#ifndef TIMEMORE_SCALE_H
#define TIMEMORE_SCALE_H

#include <Arduino.h>
#include <NimBLEDevice.h>
#include <vector>

// BLE central client for a Timemore Dot (Black Mirror) coffee scale, run
// alongside BleServer's own peripheral role on the same ESP32-C3 radio
// (NimBLE multiplexes both roles over one controller - no extra hardware).
//
// Protocol ported from Zer0-bit/esp-arduino-ble-scales (MIT licensed,
// github.com/Zer0-bit/esp-arduino-ble-scales, src/scales/timemore_new.cpp,
// plugin id "plugin-timemore-dot") - not re-derived from scratch. That
// project wraps a shared RemoteScales base class we don't have here, so
// this reimplements just the Timemore-specific handshake/framing directly
// against NimBLEDevice/NimBLEClient.
class TimemoreScale {
public:
    void begin();  // configures the central role and starts scanning for a TIMEMORE_Dot (enabled by default)
    void update();  // call every loop() iteration - drives reconnect attempts
    bool isConnected() const { return _connected; }
    float getWeightG() const { return _weight_g; }
    void tare();  // sends the scale's own tare command - does not touch HX711 calibration

    // App-facing on/off switch (BLE_OP_SET_TIMEMORE_AUTOCONNECT) - disabling
    // stops any in-progress scan and drops an existing connection right
    // away; re-enabling kicks off a fresh scan on the next update(). Not
    // persisted - defaults to enabled every boot, matching "auto-connect
    // unless the app says otherwise".
    void setEnabled(bool enabled);
    bool isEnabled() const { return _enabled; }

private:
    NimBLEClient *_client = nullptr;
    NimBLERemoteCharacteristic *_notify_char = nullptr;
    NimBLERemoteCharacteristic *_command_char = nullptr;

    bool _enabled = true;
    bool _connected = false;
    bool _connecting = false;
    uint32_t _last_reconnect_attempt_ms = 0;

    // ScanCallbacks::onResult() runs on NimBLE's own host task - calling the
    // blocking NimBLEClient::connect() directly from there would deadlock
    // (it waits on a GAP-connect-complete notification that only the host
    // task itself can deliver, and that task would be stuck inside our own
    // call). onResult() only records the match here; update() (running on
    // the Arduino loop task) does the actual connect.
    NimBLEAddress _pending_address;
    bool _pending_connect = false;

    std::vector<uint8_t> _rx_buffer;
    volatile float _weight_g = 0.0f;

    void connectToDevice(const NimBLEAddress &address);
    bool performHandshake();
    void sendQueryCommand(uint8_t query_type);
    void handleFrame(const uint8_t *frame, size_t len);
    bool decodeNextFrame(); // pulls one complete frame out of _rx_buffer if present
    static uint16_t crc16(const uint8_t *data, size_t length);

    // Trampolines: NimBLE 1.4.x's subscribe()/callback APIs take plain
    // function pointers, not member functions - both look up the single
    // TimemoreScale instance via _instance since this device only ever
    // talks to one Timemore Dot at a time.
    static TimemoreScale *_instance;
    static void notifyTrampoline(NimBLERemoteCharacteristic *characteristic, uint8_t *data, size_t length, bool is_notify);

    class ScanCallbacks;
    class ClientCallbacks;
    friend class ScanCallbacks;
    friend class ClientCallbacks;
};

#endif // TIMEMORE_SCALE_H
