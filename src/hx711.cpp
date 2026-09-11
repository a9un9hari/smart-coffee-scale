#include "hx711.h"
#include "config.h"

// HX711 protocol notes:
// - DOUT idles HIGH, goes LOW when a conversion is ready to read.
// - Reading is 24 data bits (MSB first) clocked out on CLK pulses.
// - A 25th pulse (gain=128, channel A) must follow so the NEXT
//   conversion also uses gain 128 - this is why the loop below
//   runs 25 times instead of 24.

HX711::HX711(uint8_t dout_pin, uint8_t clk_pin)
    : _dout_pin(dout_pin), _clk_pin(clk_pin),
      _offset(0), _scale_factor(1.0f), _status(HX711_OK) {}

void HX711::begin() {
    pinMode(_dout_pin, INPUT);
    pinMode(_clk_pin, OUTPUT);
    digitalWrite(_clk_pin, LOW);
    _status = HX711_OK;
}

bool HX711::waitReady() {
    uint32_t start = micros();
    while (digitalRead(_dout_pin) == HIGH) {
        if ((uint32_t)(micros() - start) > READ_TIMEOUT_US) {
            return false;
        }
    }
    return true;
}

long HX711::readRawInternal() {
    long value = 0;

    for (uint8_t i = 0; i < 24; i++) {
        digitalWrite(_clk_pin, HIGH);
        delayMicroseconds(1);
        value = (value << 1) | digitalRead(_dout_pin);
        digitalWrite(_clk_pin, LOW);
        delayMicroseconds(1);
    }

    // 25th pulse: sets gain=128 / channel A for the next conversion
    digitalWrite(_clk_pin, HIGH);
    delayMicroseconds(1);
    digitalWrite(_clk_pin, LOW);
    delayMicroseconds(1);

    // sign-extend 24-bit two's complement into a 32-bit long
    if (value & 0x800000) {
        value |= 0xFF000000;
    }
    return value;
}

bool HX711::detect() {
    if (!waitReady()) {
        _status = HX711_DISCONNECTED;
        return false;
    }
    return true;
}

long HX711::readRaw() {
    if (!waitReady()) {
        _status = HX711_TIMEOUT;
        return 0;
    }
    long value = readRawInternal();
    _status = HX711_OK;
    return value;
}

float HX711::readWeight() {
    long sum = 0;
    uint8_t got = 0;

    for (uint8_t i = 0; i < AVG_SAMPLES; i++) {
        if (!waitReady()) {
            // one bad sample: skip it, don't fail the whole average
            continue;
        }
        sum += readRawInternal();
        got++;
    }

    if (got == 0) {
        _status = HX711_TIMEOUT;
        return 0.0f;
    }

    long avg_raw = sum / got;
    float weight_g = (float)(avg_raw - _offset) * _scale_factor;

    if (weight_g > LOAD_CELL_CAPACITY_G) {
        _status = HX711_OVERLOAD;
    } else {
        _status = HX711_OK;
    }

    return weight_g;
}

void HX711::tare() {
    long sum = 0;
    uint8_t got = 0;

    for (uint8_t i = 0; i < AVG_SAMPLES; i++) {
        if (!waitReady()) {
            continue;
        }
        sum += readRawInternal();
        got++;
    }

    if (got > 0) {
        _offset = sum / got;
        _status = HX711_OK;
    } else {
        _status = HX711_DISCONNECTED;
    }
}

void HX711::setCalibrationFactor(float grams_per_count) {
    _scale_factor = grams_per_count;
}
