#ifndef OTA_H
#define OTA_H

#include <Arduino.h>
#include <functional>

// WiFi is otherwise unused on this board (BLE-only control from the Android
// app) - the radio only comes up here, on demand, to pull a new firmware
// binary over HTTP. Everything is driven from BLE-supplied config (SSID,
// password, firmware URL) so no credentials are hardcoded or persisted.
enum OtaState : uint8_t {
    OTA_IDLE            = 0,
    OTA_CONNECTING_WIFI = 1,
    OTA_UPDATING        = 2,
    OTA_SUCCESS         = 3, // rarely observed - a successful update reboots the board itself
    OTA_ERROR_WIFI      = 4, // WiFi association/DHCP didn't complete before the timeout
    OTA_ERROR_UPDATE    = 5, // connected, but the HTTP update itself failed
    OTA_ERROR_NO_CONFIG = 6, // start() called before SSID/URL were set
};

class OtaManager {
public:
    void begin();

    // Each setter copies into a fixed buffer, truncating to fit - callers
    // get the length back so BLE write handlers can size their reply/logs.
    void setSsid(const char *data, size_t len);
    void setPassword(const char *data, size_t len);
    void setUrl(const char *data, size_t len);

    void start();  // no-op (-> OTA_ERROR_NO_CONFIG) unless SSID + URL are set
    void cancel(); // only meaningful while CONNECTING_WIFI; UPDATING blocks update()

    void update(); // call every loop() iteration alongside everything else

    OtaState getState() const { return _state; }
    uint8_t getProgressPercent() const { return _progress; }
    int8_t getLastErrorCode() const { return _last_error_code; } // HTTPUpdate/HTTPClient's own small negative codes - fits int8_t

    // Fired on every state/progress change, including from inside the
    // blocking performUpdate() call - the main loop() doesn't run again
    // until that call returns, so without this the app would see nothing
    // between "Updating..." and the final result. GrinderController wires
    // this straight to BleServer::notifyOtaStatus() (still internally
    // throttled there, so a fast run of progress ticks doesn't flood BLE).
    void setStatusCallback(std::function<void()> cb) { _status_callback = cb; }

private:
    char _ssid[33] = {0};
    char _password[65] = {0};
    char _url[129] = {0}; // http:// or https:// firmware binary URL

    OtaState _state = OTA_IDLE;
    uint32_t _wifi_connect_start_ms = 0;
    uint8_t _progress = 0;
    int8_t _last_error_code = 0; // HTTPUpdate's own error code on OTA_ERROR_UPDATE
    std::function<void()> _status_callback = nullptr;

    void notifyStatusChanged() { if (_status_callback) _status_callback(); }

    // Blocking by design: flashing the new app partition can't be interleaved
    // with anything else. Only reached once WiFi is confirmed connected.
    // GrinderController is responsible for refusing start() while the motor
    // is running (STATE_IDLE / STATE_ESPRESSO_IDLE only) - the pause here
    // would otherwise stall a safety-relevant motor timeout.
    void performUpdate();
};

#endif // OTA_H
