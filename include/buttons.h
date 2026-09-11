#ifndef BUTTONS_H
#define BUTTONS_H

#include <Arduino.h>

enum ButtonId {
    START_BUTTON,
    MODE_BUTTON,
    BUTTON_COUNT   // keep last: number of buttons
};

class Buttons {
public:
    void begin();
    void update();                     // call every ~10ms from loop(), non-blocking
    bool wasPressed(ButtonId id);      // one-shot: true once per press, clears flag
    bool isPressed(ButtonId id) const; // live debounced state

private:
    static const uint32_t DEBOUNCE_MS = 20;

    struct ButtonState {
        uint8_t pin;
        bool raw_state;          // last raw GPIO reading (true = LOW = pressed)
        bool debounced_state;    // confirmed stable state
        bool press_flag;         // set on confirmed press, cleared by wasPressed()
        uint32_t last_change_ms; // when raw_state last changed
    };

    ButtonState _buttons[BUTTON_COUNT];
};

#endif // BUTTONS_H
