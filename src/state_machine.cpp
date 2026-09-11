#include "state_machine.h"
#include <math.h>

#define WEIGHT_STABLE_EPSILON_G 0.3f

void StateMachine::init(SystemStatus *status) {
    _status = status;
    _status->mode = MODE_GRINDER;
    _status->state = STATE_IDLE;
    _status->state_entered_ms = millis();
    _status->error_code = 0;

    _pulling_tracking = false;
    _pulling_last_weight_g = 0.0f;
    _pulling_stable_since_ms = 0;
}

static bool isMotorState(SystemState s) {
    return s == STATE_GRINDING || s == STATE_PULL_SHOT;
}

void StateMachine::transitionTo(SystemState new_state) {
    SystemState old_state = _status->state;
    _status->state = new_state;
    _status->state_entered_ms = millis();

    if (new_state == STATE_PULLING) {
        _pulling_tracking = false; // (re)armed on first update() weight sample
    }

    if (_motor != nullptr) {
        bool was_motor_state = isMotorState(old_state);
        bool is_motor_state = isMotorState(new_state);

        if (is_motor_state && !was_motor_state) {
            _motor->start();
        } else if (was_motor_state && !is_motor_state) {
            _motor->stop();
        }
    }

    _status->motor_running = (_motor != nullptr) ? _motor->isRunning() : false;
}

void StateMachine::onEvent(StateMachineEvent event) {
    if (event == EVT_EMERGENCY_STOP) {
        transitionTo(STATE_IDLE);
        return;
    }

    if (event == EVT_ERROR_OCCURRED) {
        transitionTo(STATE_ERROR);
        return;
    }

    if (_status->mode == MODE_GRINDER) {
        handleGrinderEvent(event);
    } else {
        handleEspressoEvent(event);
    }
}

void StateMachine::onModeCommand(SystemMode requested_mode) {
    if (requested_mode == _status->mode) {
        return;
    }

    // only honored from an idle-ish state, can't yank the user out of an
    // active grind/pull
    if (_status->state == STATE_IDLE && requested_mode == MODE_ESPRESSO) {
        _status->mode = MODE_ESPRESSO;
        transitionTo(STATE_ESPRESSO_IDLE);
    } else if (_status->state == STATE_ESPRESSO_IDLE && requested_mode == MODE_GRINDER) {
        _status->mode = MODE_GRINDER;
        transitionTo(STATE_IDLE);
    }
}

void StateMachine::handleGrinderEvent(StateMachineEvent event) {
    switch (_status->state) {
        case STATE_IDLE:
            if (event == EVT_CUP_DETECTED || event == EVT_BLE_START) {
                _session_start_weight_g = _status->current_weight_g;
                transitionTo(STATE_GRINDING);
            }
            break;

        case STATE_GRINDING:
            if (event == EVT_TARGET_REACHED || event == EVT_BLE_STOP) {
                transitionTo(STATE_IDLE);
            }
            break;

        default:
            break; // events not relevant to grinder-mode states
    }
}

void StateMachine::handleEspressoEvent(StateMachineEvent event) {
    switch (_status->state) {
        case STATE_ESPRESSO_IDLE:
            if (event == EVT_BLE_START) {
                transitionTo(STATE_PULL_SHOT);
            }
            break;

        case STATE_PULL_SHOT:
            if (event == EVT_WEIGHT_CHANGED) {
                transitionTo(STATE_PULLING);
            } else if (event == EVT_BLE_STOP) {
                transitionTo(STATE_ESPRESSO_IDLE);
            }
            break;

        case STATE_PULLING:
            if (event == EVT_SHOT_STABLE || event == EVT_BLE_STOP) {
                transitionTo(event == EVT_SHOT_STABLE ? STATE_SHOT_COMPLETE : STATE_ESPRESSO_IDLE);
            }
            break;

        case STATE_SHOT_COMPLETE:
            if (event == EVT_BLE_STOP) {
                transitionTo(STATE_ESPRESSO_IDLE);
            }
            break;

        default:
            break;
    }
}

void StateMachine::update() {
    uint32_t now = millis();

    if (_motor != nullptr) {
        _status->motor_running = _motor->isRunning();
        if (_motor->getStatus() == MOTOR_ERR_TIMEOUT) {
            _status->error_code = 1;
            onEvent(EVT_ERROR_OCCURRED);
            return;
        }
    }

    switch (_status->state) {
        case STATE_GRINDING:
            if (_status->current_weight_g - _session_start_weight_g >= _status->target_weight_g) {
                onEvent(EVT_TARGET_REACHED);
            }
            break;

        case STATE_PULL_SHOT:
            // first weight movement off zero counts as "pull started"
            if (_status->current_weight_g > WEIGHT_STABLE_EPSILON_G) {
                onEvent(EVT_WEIGHT_CHANGED);
            }
            break;

        case STATE_PULLING: {
            float delta = fabsf(_status->current_weight_g - _pulling_last_weight_g);

            if (!_pulling_tracking || delta > WEIGHT_STABLE_EPSILON_G) {
                _pulling_tracking = true;
                _pulling_last_weight_g = _status->current_weight_g;
                _pulling_stable_since_ms = now;
            } else if (now - _pulling_stable_since_ms >= ESPRESSO_STABLE_MS) {
                onEvent(EVT_SHOT_STABLE);
            }
            break;
        }

        default:
            break; // no time-based transitions for other states
    }
}
