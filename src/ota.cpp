#include "ota.h"
#include "config.h"
#include <WiFi.h>
#include <HTTPClient.h>
#include <HTTPUpdate.h>
#include <string.h>

void OtaManager::begin() {
    // Nothing to init eagerly - WiFi only comes up in start(), so idle power
    // draw and BLE radio behavior are unaffected until an OTA is requested.
}

void OtaManager::setSsid(const char *data, size_t len) {
    if (len > sizeof(_ssid) - 1) len = sizeof(_ssid) - 1;
    memcpy(_ssid, data, len);
    _ssid[len] = '\0';
}

void OtaManager::setPassword(const char *data, size_t len) {
    if (len > sizeof(_password) - 1) len = sizeof(_password) - 1;
    memcpy(_password, data, len);
    _password[len] = '\0';
}

void OtaManager::setUrl(const char *data, size_t len) {
    if (len > sizeof(_url) - 1) len = sizeof(_url) - 1;
    memcpy(_url, data, len);
    _url[len] = '\0';
}

void OtaManager::start() {
    if (_state == OTA_CONNECTING_WIFI || _state == OTA_UPDATING) {
        Serial.println("[OTA] start() ignored - already in progress");
        return; // already in progress
    }
    if (_ssid[0] == '\0' || _url[0] == '\0') {
        Serial.printf("[OTA] start() refused - missing config (ssid_len=%d url_len=%d)\n", (int)strlen(_ssid), (int)strlen(_url));
        _state = OTA_ERROR_NO_CONFIG;
        notifyStatusChanged();
        return;
    }

    Serial.printf("[OTA] start() - connecting to SSID '%s', url='%s'\n", _ssid, _url);

    // Diagnostic only: confirms whether the ESP32 itself can even see the AP
    // before attempting auth, and what security mode it's advertising - a
    // wrong guess here (e.g. WPA3-only/PMF-required) looks identical to a
    // wrong password from WiFi.status() alone.
    Serial.println("[OTA] scanning for target SSID...");
    WiFi.mode(WIFI_STA);
    Serial.printf("[OTA] this board's WiFi MAC: %s\n", WiFi.macAddress().c_str());
    int found_count = WiFi.scanNetworks();
    bool ssid_seen = false;
    for (int i = 0; i < found_count; i++) {
        if (WiFi.SSID(i) == String(_ssid)) {
            ssid_seen = true;
            Serial.printf("[OTA] scan: found '%s' RSSI=%d channel=%d encType=%d\n",
                _ssid, WiFi.RSSI(i), WiFi.channel(i), (int)WiFi.encryptionType(i));
        }
    }
    if (!ssid_seen) {
        Serial.printf("[OTA] scan: '%s' NOT seen (%d other networks visible)\n", _ssid, found_count);
    }
    WiFi.scanDelete();

    _progress = 0;
    _last_error_code = 0;
    _state = OTA_CONNECTING_WIFI;
    _wifi_connect_start_ms = millis();
    WiFi.mode(WIFI_STA);
    WiFi.begin(_ssid, _password[0] != '\0' ? _password : nullptr);
    notifyStatusChanged();
}

void OtaManager::cancel() {
    if (_state == OTA_CONNECTING_WIFI) {
        WiFi.disconnect(true);
        _state = OTA_IDLE;
        notifyStatusChanged();
    }
    // OTA_UPDATING can't be cancelled - performUpdate() is a blocking call
    // already in progress by the time anything could observe this request.
}

void OtaManager::update() {
    if (_state != OTA_CONNECTING_WIFI) {
        return;
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.printf("[OTA] WiFi connected, IP=%s\n", WiFi.localIP().toString().c_str());
        _state = OTA_UPDATING;
        notifyStatusChanged(); // last chance to reach the app before performUpdate() blocks the loop
        performUpdate();
        return;
    }

    if (millis() - _wifi_connect_start_ms > OTA_WIFI_CONNECT_TIMEOUT_MS) {
        Serial.printf("[OTA] WiFi connect timed out - last WiFi.status()=%d\n", (int)WiFi.status());
        WiFi.disconnect(true);
        _state = OTA_ERROR_WIFI;
        notifyStatusChanged();
    }
}

void OtaManager::performUpdate() {
    Serial.println("[OTA] starting HTTP update...");
    WiFiClient client;
    httpUpdate.rebootOnUpdate(true); // success path reboots on its own and never returns here

    // update() blocks the main loop() for the whole download/flash, so this
    // is the only way progress reaches the app before it's over - notify on
    // every tick HTTPUpdate gives us (still throttled inside
    // BleServer::notifyOtaStatus(), so this can't flood the link).
    httpUpdate.onProgress([this](int cur, int total) {
        _progress = (total > 0) ? (uint8_t)((cur * 100) / total) : 0;
        notifyStatusChanged();
    });

    t_httpUpdate_return result = httpUpdate.update(client, _url);

    switch (result) {
        case HTTP_UPDATE_OK:
            Serial.println("[OTA] HTTP_UPDATE_OK (unreachable - device should have rebooted)");
            _state = OTA_SUCCESS; // unreachable in practice (rebootOnUpdate)
            break;
        case HTTP_UPDATE_NO_UPDATES:
        case HTTP_UPDATE_FAILED:
        default:
            _last_error_code = (int8_t)httpUpdate.getLastError();
            Serial.printf("[OTA] update failed: result=%d error=%d (%s)\n",
                (int)result, httpUpdate.getLastError(), httpUpdate.getLastErrorString().c_str());
            _state = OTA_ERROR_UPDATE;
            break;
    }

    WiFi.disconnect(true); // done with the radio either way - back to BLE-only
    notifyStatusChanged(); // guarantee the final result reaches the app even if throttled earlier
}
