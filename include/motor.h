#ifndef MOTOR_H
#define MOTOR_H

#include <Arduino.h>
#include "config.h"

enum MotorState {
    MOTOR_OFF,
    MOTOR_STARTING,
    MOTOR_RUNNING,
    MOTOR_STOPPING
};

enum MotorStatus {
    MOTOR_OK,
    MOTOR_ERR_TIMEOUT,          // ran longer than MOTOR_MAX_RUNTIME_MS
    MOTOR_ERR_ALREADY_RUNNING,  // start() called while already on
    MOTOR_ERR_START_FAILED,
    MOTOR_ERR_STOP_FAILED
};

class MotorControl {
public:
    void begin();
    MotorStatus start();
    MotorStatus stop();
    void update();                    // call every 100ms - checks safety timeout
    void emergencyStop();             // immediate shutdown, bypasses settle time

    bool isRunning() const;
    uint32_t getRunningTime() const;  // ms elapsed of current/last run
    MotorStatus getStatus() const { return _status; }

private:
    static const uint32_t START_SETTLE_MS = 50;
    static const uint32_t STOP_SETTLE_MS = 50;
    static const uint32_t MAX_RUNTIME_MS = MOTOR_MAX_RUNTIME_MS; // config.h, 30s

    MotorState _state = MOTOR_OFF;
    MotorStatus _status = MOTOR_OK;
    uint32_t _start_time_ms = 0;
    uint32_t _stop_time_ms = 0;
    uint32_t _running_time_ms = 0;
};

#endif // MOTOR_H
