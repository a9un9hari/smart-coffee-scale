#ifndef ENCODER_H
#define ENCODER_H

#include <Arduino.h>

// KY-040 rotary encoder: quadrature rotation + push button.
// update() must be called often (every loop() iteration) so CLK edges
// aren't missed - this is a mechanical switch, not an interrupt source here.
class Encoder {
public:
    void begin();
    void update();

    int8_t getDelta();          // signed step count since last call, then clears it
    bool wasButtonPressed();    // one-shot debounced SW press

    void setRange(int min_val, int max_val);
    void setValue(int val);
    int getValue() const { return _value; }

private:
    static const uint32_t SW_DEBOUNCE_MS = 20;

    int _value = 0;
    int _min_val = 0;
    int _max_val = 100;

    uint8_t _last_clk_state = HIGH;
    int8_t _pending_delta = 0;

    bool _sw_raw_state = false;
    bool _sw_debounced_state = false;
    bool _sw_press_flag = false;
    uint32_t _sw_last_change_ms = 0;

    void updateRotation();
    void updateButton();
};

#endif // ENCODER_H
