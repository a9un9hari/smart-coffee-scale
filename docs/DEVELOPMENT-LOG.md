# DEVELOPMENT LOG - Smart Grinder Controller

**Purpose:** Running record of what's been built, decided, and fixed on this
project, so future sessions (or future you) don't have to re-derive it from
git log alone.

**Repo:** git@github.com:a9un9hari/smart-coffee-scale.git
**Target hardware:** ESP32-C3 Super Mini (QFN32, embedded flash, native USB)

---

## 2026-09-11: Firmware skeleton (PROMPT 1.1 - 3.1)

Followed `VIBE-CODING-PROMPT-FRAMEWORK.md`'s roadmap end to end.

- **PROMPT 1.1** - Project structure: `platformio.ini`, `include/config.h`
  (pin map, timing constants, state/mode enums), `include/data_types.h`
  (`SensorReading`, `SystemStatus`, `CalibrationData`).
- **PROMPT 1.2** - HX711 driver (`hx711.h/.cpp`). Custom bit-bang
  implementation (25 clock pulses: 24 data bits + gain-128 select), not the
  `bogde/HX711` library - the prompt's algorithm section describes raw
  digitalRead/digitalWrite timing, and a library would have collided on the
  `HX711` class name anyway.
- **PROMPT 1.3** - Button debounce (`buttons.h/.cpp`). Time-based debounce
  (20ms), non-blocking, one-shot `wasPressed()` + live `isPressed()`.
- **PROMPT 2.1** - State machine (`state_machine.h/.cpp`). Grinder/Espresso
  mode transitions per the doc's table, plus two deviations noted at the
  time: no physical STOP button exists, so pressing START again mid-grind
  aborts it; and MODE only toggles grinder/espresso from an idle-ish state
  so it can't be pressed mid-operation.
- **PROMPT 2.2** - Motor control (`motor.h/.cpp`), SSR relay, 50ms
  start/stop settle time, 30s safety timeout via `emergencyStop()`. Wired
  into the state machine (`attachMotor()`): entering `GRINDING`/`PULL_SHOT`
  starts the motor, leaving them stops it.
- **PROMPT 3.1** - Integration: pulled all of the above out of `main.cpp`
  into a `GrinderController` orchestrator (`control.h/.cpp`). `main.cpp` is
  now just `begin()` + `update()`.

First `pio run`: SUCCESS, 4.3% RAM / 20.0% flash, no warnings outside the
(unused-at-the-time) TFT_eSPI library.

---

## 2026-09-11: Optional modules (encoder, display, EEPROM)

Added the three "optional prompts" listed at the end of the framework doc:

- **Encoder** (`encoder.h/.cpp`) - KY-040 quadrature decode (CLK falling
  edge, DT sets direction) + debounced SW button. Wired so encoder turns
  only affect `target_weight_g` while the state machine is in
  `STATE_SELECT_WEIGHT`/`STATE_UPDATE_WEIGHT` - ignored everywhere else.
  Range clamped 10-30g. The SW button's role was never defined by the doc
  and is still unused (`wasButtonPressed()` exists, nothing calls it).
- **Display** (`display.h/.cpp`) - ST7789 240x240 via TFT_eSPI, three
  independently-redrawn regions (weight / menu / status), rate-limited to
  ~30Hz inside `update()`.
- **EEPROM storage** (`storage.h/.cpp`) - `CalibrationData` (offset, scale
  factor, target weight, wear counter) persisted in two mirrored EEPROM
  slots with a CRC16/MODBUS checksum each; `restore()` self-heals the
  primary from the backup if its checksum fails. Added `target_weight_g`
  to `CalibrationData` (single-profile MVP, no multi-profile UI exists) and
  `HX711::getOffset()/setOffset()` so a saved offset can be restored
  without re-taring on every boot.
- Wear counter increments automatically whenever a grind finishes cleanly
  (`STATE_GRINDING` -> `STATE_IDLE` with `error_code == 0`), persisted
  right away along with whatever `target_weight_g` was last dialed in.

Repo initialized (`git init`), pushed to
`git@github.com:a9un9hari/smart-coffee-scale.git`.

---

## 2026-09-11: First hardware bring-up on real ESP32-C3

Installed PlatformIO (`pip install platformio --break-system-packages` -
no `pio`/`platformio` binary existed before this). Board connected at
`/dev/cu.usbmodem1101`.

Two crashes found and fixed - **see `include/config.h` and
`platformio.ini` comments for the permanent record**, summarized here:

1. **Bootloop (`TG1WDT_SYS_RST`, resetting every ~1s).** Root cause:
   display SPI pins were assigned to GPIO13/14/15, which on this
   embedded-flash (QFN32) package are wired internally to the flash chip
   itself. Toggling them as GPIO corrupts flash access. Fix: remapped
   display SPI to GPIO8 (SCLK)/10 (MOSI)/20 (CS)/21 (DC) - only GPIO8 is a
   strapping pin, everything else is clean.
2. **`Serial` producing no output at all**, even once the bootloop was
   gone. Root cause: ESP32-C3 has native USB, not a UART bridge - without
   `ARDUINO_USB_CDC_ON_BOOT=1`, `Serial.print()` goes to the unused UART0
   pins (GPIO20/21 - which by then were also claimed by the display fix
   above, making this doubly broken) instead of the USB port being
   monitored.
3. **`Guru Meditation Error: Store access fault` in
   `TFT_eSPI::begin_tft_write()`**, deterministic, every single boot, 27x
   observed in a row. Decoded via `riscv32-esp-elf-addr2line` against the
   built ELF. Root cause: `TFT_eSPI@2.5.43`'s ESP32-C3 backend computes a
   raw SPI hardware register pointer (`_spi_user`) via macros that resolve
   incorrectly under `espressif32@7.1.2` (Arduino core 4.20017.x) - the
   pointer ends up pointing at address `0x10` instead of a real peripheral
   register. Fix: pinned `platform = espressif32@6.5.0` (Arduino core
   2.0.14) in `platformio.ini`, a combination confirmed stable on this
   exact board.

**Diagnostic method worth remembering:** toggling one subsystem's
`begin()`/`update()` calls off in `control.cpp`, reflashing, and reading
raw serial (`pyserial`, since `pio device monitor` needs an interactive
TTY and can't run from a script) is the fast way to bisect which
subsystem is crashing. Triggering a clean reset from a script without
unplugging anything: toggle DTR/RTS on the serial port
(`ser.rts = True; sleep; ser.rts = False` mirrors what `esptool`/`pio
device monitor` do on connect).

After both fixes: verified stable boot (`Smart Grinder Controller - Boot
OK`) with no resets over multiple repeated flashes.

**Not yet tested on hardware:** HX711 (no load cell wired up), buttons,
motor/SSR, rotary encoder. Only the boot path and display init have been
exercised on real silicon so far.

---

## 2026-09-11: Reference guide added, display driver corrected

User added `docs/Reference Guide/` (prepared separately, describing itself
as part of a larger 16-document set most of which isn't present in this
repo). Reading it surfaced two things worth recording:

1. **The physical display received is an OLED SSD1306 128x64 (I2C)**, not
   the ST7789 SPI panel the original prompt framework's optional "Display
   Driver" prompt assumed - the reference guide explicitly notes this was a
   substitution ("Received OLED I2C instead of ST7789 SPI"). Everything
   built for ST7789/TFT_eSPI so far (including the `espressif32@6.5.0`
   platform pin from the previous entry, which worked around a TFT_eSPI/
   ESP32-C3 bug) was for hardware that was never actually connected.
   Fixed: rewrote `display.h/.cpp` on `Adafruit_SSD1306` + `Adafruit_GFX`
   over I2C (SDA=GPIO21, SCL=GPIO20). Public API (`drawWeight`/`drawMenu`/
   `drawStatus`/`update`) unchanged. Verified stable boot on hardware after
   the swap; flash usage actually went *down* (23.0% -> 21.3%).
2. **The reference guide's own pin map is partly wrong**: it lists
   GPIO13/14/15 as free/available SPI pins, which directly contradicts the
   hardware-confirmed bootloop documented in the hardware bring-up entry
   above. It was written before hardware bring-up happened and was never
   corrected against real test results. `include/config.h`'s comments are
   the trustworthy source for anything above GPIO7.

The `espressif32@6.5.0` platform pin was left in place even though the bug
it worked around no longer applies (no TFT_eSPI left in the project at
all) - it's simply the combo that's actually been verified stable on this
board, and there's no pressing need to churn it.

---

## Known deviations from the original prompt framework

Kept here so they don't get "fixed" back to the letter of the doc by
mistake later:

- No physical STOP button exists (`config.h` only defines START/MODE) -
  pressing START again while `STATE_GRINDING` acts as abort.
- Emergency stop (`EVT_EMERGENCY_STOP`) is holding START+MODE together for
  2 seconds, detected in `GrinderController::readButtons()`.
- `CalibrationData` supports exactly one saved grind profile
  (`target_weight_g`), not multiple named profiles - there's no UI to pick
  between profiles.
- Display pins and the pinned PlatformIO platform version (see hardware
  bring-up section above) are hardware-bring-up fixes, not part of the
  original prompt framework at all.
