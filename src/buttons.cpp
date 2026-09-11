#include "buttons.h"
#include "config.h"

void Buttons::begin() {
    _buttons[START_BUTTON].pin = PIN_BUTTON_START;
    _buttons[MODE_BUTTON].pin = PIN_BUTTON_MODE;

    for (uint8_t i = 0; i < BUTTON_COUNT; i++) {
        pinMode(_buttons[i].pin, INPUT_PULLUP);
        _buttons[i].raw_state = false;
        _buttons[i].debounced_state = false;
        _buttons[i].press_flag = false;
        _buttons[i].last_change_ms = 0;
    }
}

void Buttons::update() {
    uint32_t now = millis();

    for (uint8_t i = 0; i < BUTTON_COUNT; i++) {
        ButtonState &b = _buttons[i];

        // pull-up wiring: pressed = LOW
        bool raw = (digitalRead(b.pin) == LOW);

        if (raw != b.raw_state) {
            // raw reading just changed - restart debounce window
            b.raw_state = raw;
            b.last_change_ms = now;
            continue;
        }

        // raw state has been stable - check if debounce window elapsed
        if (now - b.last_change_ms >= DEBOUNCE_MS && raw != b.debounced_state) {
            b.debounced_state = raw;
            if (b.debounced_state) {
                b.press_flag = true; // confirmed press edge
            }
        }
    }
}

bool Buttons::wasPressed(ButtonId id) {
    if (_buttons[id].press_flag) {
        _buttons[id].press_flag = false;
        return true;
    }
    return false;
}

bool Buttons::isPressed(ButtonId id) const {
    return _buttons[id].debounced_state;
}
