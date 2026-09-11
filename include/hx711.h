#ifndef HX711_H
#define HX711_H

#include <Arduino.h>

// Status returned by readWeight()/detect()
enum HX711Status {
    HX711_OK,
    HX711_OVERLOAD,       // reading exceeds load cell capacity
    HX711_DISCONNECTED,   // DOUT never went low (not detected)
    HX711_TIMEOUT         // DOUT stuck high mid-read
};

class HX711 {
public:
    HX711(uint8_t dout_pin, uint8_t clk_pin);

    void begin();
    bool detect();                              // true if HX711 responds
    long readRaw();                             // single raw 24-bit read, sign-extended
    float readWeight();                         // averaged, calibrated weight in grams
    void tare();                                // zero the scale (sets offset from current reading)
    void setCalibrationFactor(float grams_per_count);

    long getOffset() const { return _offset; }         // for persisting calibration
    void setOffset(long offset) { _offset = offset; }  // for restoring calibration

    HX711Status getStatus() const { return _status; }

private:
    uint8_t _dout_pin;
    uint8_t _clk_pin;

    long _offset;
    float _scale_factor;   // grams per raw count
    HX711Status _status;

    static const uint8_t AVG_SAMPLES = 5;
    static const uint32_t READ_TIMEOUT_US = 10000; // 10ms

    bool waitReady();      // wait for DOUT low (data ready), false on timeout
    long readRawInternal(); // one bit-bang read, no averaging/offset/scale
};

#endif // HX711_H
