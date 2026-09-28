// Recompiled on every build by scripts/build_stamp.py, so this always holds
// the time the current firmware image was built - shown on the OLED splash
// to confirm which firmware is running after an OTA update.
#include "build_stamp.h"

const char BUILD_DATE[] = __DATE__; // "Sep 28 2026"
const char BUILD_TIME[] = __TIME__; // "15:04:12"
