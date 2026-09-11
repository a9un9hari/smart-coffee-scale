#include "motor.h"
#include "config.h"

void MotorControl::begin() {
    pinMode(PIN_MOTOR_SSR, OUTPUT);
    digitalWrite(PIN_MOTOR_SSR, LOW);
    _state = MOTOR_OFF;
    _status = MOTOR_OK;
}

MotorStatus MotorControl::start() {
    if (_state != MOTOR_OFF) {
        return MOTOR_ERR_ALREADY_RUNNING;
    }

    digitalWrite(PIN_MOTOR_SSR, HIGH);
    _state = MOTOR_STARTING;
    _start_time_ms = millis();
    _status = MOTOR_OK;
    return MOTOR_OK;
}

MotorStatus MotorControl::stop() {
    if (_state == MOTOR_OFF) {
        return MOTOR_OK; // double-stop is fine
    }

    digitalWrite(PIN_MOTOR_SSR, LOW);
    _running_time_ms = millis() - _start_time_ms;
    _stop_time_ms = millis();
    _state = MOTOR_STOPPING;
    return MOTOR_OK;
}

void MotorControl::emergencyStop() {
    // immediate shutdown, no settle-time bookkeeping - safety takes priority
    digitalWrite(PIN_MOTOR_SSR, LOW);
    if (_state == MOTOR_STARTING || _state == MOTOR_RUNNING) {
        _running_time_ms = millis() - _start_time_ms;
    }
    _state = MOTOR_OFF;
}

void MotorControl::update() {
    uint32_t now = millis();

    switch (_state) {
        case MOTOR_STARTING:
            if (now - _start_time_ms >= START_SETTLE_MS) {
                _state = MOTOR_RUNNING;
            }
            break;

        case MOTOR_RUNNING:
            if (now - _start_time_ms >= MAX_RUNTIME_MS) {
                emergencyStop();
                _status = MOTOR_ERR_TIMEOUT;
            }
            break;

        case MOTOR_STOPPING:
            if (now - _stop_time_ms >= STOP_SETTLE_MS) {
                _state = MOTOR_OFF;
            }
            break;

        default:
            break; // MOTOR_OFF: nothing to do
    }
}

bool MotorControl::isRunning() const {
    return _state == MOTOR_STARTING || _state == MOTOR_RUNNING;
}

uint32_t MotorControl::getRunningTime() const {
    if (isRunning()) {
        return millis() - _start_time_ms;
    }
    return _running_time_ms;
}
