#include "encoder.h"
#include "config.h"

void Encoder::begin() {
    pinMode(PIN_ENCODER_CLK, INPUT_PULLUP);
    pinMode(PIN_ENCODER_DT, INPUT_PULLUP);
    pinMode(PIN_ENCODER_SW, INPUT_PULLUP);

    _last_clk_state = digitalRead(PIN_ENCODER_CLK);
    _pending_delta = 0;
    _sw_raw_state = false;
    _sw_debounced_state = false;
    _sw_press_flag = false;
    _sw_last_change_ms = 0;
}

void Encoder::updateRotation() {
    // Quadrature: one detent click = one CLK falling edge. DT's state at
    // that instant tells direction (standard KY-040 behavior).
    uint8_t clk = digitalRead(PIN_ENCODER_CLK);

    if (clk != _last_clk_state) {
        _last_clk_state = clk;

        if (clk == LOW) {
            int8_t step = (digitalRead(PIN_ENCODER_DT) != clk) ? 1 : -1;
            _pending_delta += step;

            _value += step;
            if (_value < _min_val) _value = _min_val;
            if (_value > _max_val) _value = _max_val;
        }
    }
}

void Encoder::updateButton() {
    uint32_t now = millis();
    bool raw = (digitalRead(PIN_ENCODER_SW) == LOW); // pull-up wiring: pressed = LOW

    if (raw != _sw_raw_state) {
        _sw_raw_state = raw;
        _sw_last_change_ms = now;
        return;
    }

    if (now - _sw_last_change_ms >= SW_DEBOUNCE_MS && raw != _sw_debounced_state) {
        _sw_debounced_state = raw;
        if (_sw_debounced_state) {
            _sw_press_flag = true;
        }
    }
}

void Encoder::update() {
    updateRotation();
    updateButton();
}

int8_t Encoder::getDelta() {
    int8_t d = _pending_delta;
    _pending_delta = 0;
    return d;
}

bool Encoder::wasButtonPressed() {
    if (_sw_press_flag) {
        _sw_press_flag = false;
        return true;
    }
    return false;
}

void Encoder::setRange(int min_val, int max_val) {
    _min_val = min_val;
    _max_val = max_val;
    if (_value < _min_val) _value = _min_val;
    if (_value > _max_val) _value = _max_val;
}

void Encoder::setValue(int val) {
    if (val < _min_val) val = _min_val;
    if (val > _max_val) val = _max_val;
    _value = val;
}
