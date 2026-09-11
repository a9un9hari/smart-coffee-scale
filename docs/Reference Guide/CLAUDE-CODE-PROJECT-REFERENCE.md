# 🎯 SMART GRINDER PROJECT - CLAUDE CODE REFERENCE GUIDE

**Purpose:** Complete project documentation for Claude Code debugging & development  
**Version:** 1.0 (Final)  
**Last Updated:** September 11, 2026  
**Status:** Ready for firmware development  

---

## 📋 TABLE OF CONTENTS

1. [Project Overview](#project-overview)
2. [Hardware Specifications](#hardware-specifications)
3. [Pin Assignments & Connections](#pin-assignments--connections)
4. [Software Architecture](#software-architecture)
5. [Development Timeline](#development-timeline)
6. [Common Errors & Solutions](#common-errors--solutions)
7. [Testing Procedures](#testing-procedures)
8. [Debugging Checklist](#debugging-checklist)
9. [Reference Materials](#reference-materials)

---

## 🎯 PROJECT OVERVIEW

### **Project Name:** Smart Grinder Controller for Mazzer Mini D

### **Goal:**
Build custom smart grinder controller with:
- Weight-based grinding (2kg load cell)
- Time-based espresso mode
- Adaptive overshoot learning
- Maintenance tracking
- Professional firmware ready for production

### **Target Device:** Mazzer Mini D Coffee Grinder
- Motor: AC 220V, ~110W, ~0.5A
- Will be controlled via SSR relay
- Needs precise weight measurement
- Requires safety timeouts

### **Project Duration:**
- Week 1: Foundation & core modules
- Week 2: State machine & logic
- Week 3: Integration & testing
- Total: ~2-3 weeks for MVP firmware

### **Success Criteria:**
```
✅ Code compiles without errors
✅ All modules tested individually
✅ State machine transitions work correctly
✅ Motor control logic verified
✅ Weight readings accurate (±0.5g variance)
✅ Button debouncing confirmed
✅ Display shows correct information
✅ Complete documentation
✅ Ready for hardware integration
```

---

## 🔧 HARDWARE SPECIFICATIONS

### **Microcontroller: ESP32-C3 Super Mini**

```
Model: ESP32-C3 Super Mini (by MH-ET LIVE or similar)
Manufacturer: Espressif
Size: 22.52 × 18mm (ultra-compact)
Connector: USB-C (for programming)

SPECIFICATIONS:
├─ Chip: ESP32-C3 (RISC-V architecture)
├─ Clock: 160MHz (fast enough)
├─ RAM: 400KB (limited, must optimize)
├─ Flash: 384KB (program storage)
├─ GPIO: 11 pins available (well-planned)
├─ I2C: Hardware I2C (pins 20/21)
├─ SPI: Hardware SPI (pins 13/14/15)
├─ ADC: 8 channels (1 used for HX711)
├─ UART: 2 channels (1 for USB serial)
├─ Built-in LED: GPIO 8 (blue, for status)
└─ WiFi/BLE: Capable (Phase 2 only)

POWER REQUIREMENTS:
├─ Logic level: 3.3V
├─ USB power: Via USB-C
├─ Current: ~150mA peak (active), ~10mA sleep
├─ Max GPIO current: 40mA per pin
└─ Supply: Samsung 5V/2A charger (via breadboard)

PIN COUNT:
├─ Used: 11 pins (all GPIO pins used)
├─ Free: 0 (fully utilized!)
└─ Note: Leave ROM pins alone (GPIO 8-12 strapping)
```

### **Display: OLED 0.96" I2C**

```
Model: SSD1306 OLED 0.96" I2C Module
Type: Monochrome OLED (bright blue/white pixels)
Resolution: 128×64 pixels
Interface: I2C (2-wire)
Size: ~27×27mm
Voltage: 3.3V-5V (we use 3.3V)

SPECIFICATIONS:
├─ Refresh rate: Up to 60Hz
├─ I2C address: 0x3C or 0x3D (auto-detect)
├─ Library: Adafruit_SSD1306
├─ Brightness: Very bright OLED (no backlight needed)
├─ Response: Instant pixel changes
└─ Power: ~50mA (included in system budget)

CONNECTIONS (I2C):
├─ VCC → 3.3V
├─ GND → Ground
├─ SDA → GPIO 21 (I2C Serial Data)
└─ SCL → GPIO 20 (I2C Serial Clock)

IMPORTANT: Only 2 wires data! (SDA, SCL)
           This saves GPIO compared to SPI
```

### **Load Cell & ADC: 2kg + HX711**

```
Load Cell Specifications:
├─ Capacity: 2kg (2000g max)
├─ Type: Aluminum alloy strain gauge
├─ Output: ~2mV full scale (tiny signal!)
├─ Excitation: ~5V DC recommended
├─ Bridge type: Full Wheatstone bridge (4-wire)
└─ Wires: Red (Exc+), Black (Exc-), White (Sig+), Green (Sig-)

HX711 ADC Module:
├─ Resolution: 24-bit (16.7M counts)
├─ Gain: 128× (recommended for 2kg)
├─ Sampling rate: 80Hz internal
├─ Our read rate: 100ms (10Hz)
├─ Voltage: 3.3V-5V (we use 3.3V)
├─ Protocol: Bit-bang (no SPI/I2C)
└─ Library: HX711 Arduino Library (or custom)

HX711 CONNECTIONS:
├─ DOUT → GPIO 0 (data output)
├─ CLK → GPIO 1 (clock input)
├─ VCC → 3.3V
├─ GND → Ground
├─ E+ ← Load Cell Red wire (excitation +)
├─ E- ← Load Cell Black wire (excitation -)
├─ A+ ← Load Cell White wire (signal +)
└─ A- ← Load Cell Green wire (signal -)

CALIBRATION FACTORS:
├─ Offset: ~127000 counts (empty scale)
├─ Scale: ~411 counts per gram (example)
├─ Must be stored in EEPROM
└─ Can be recalibrated (tare function)
```

### **Motor Control: SSR-40 DA Relay**

```
Model: FOTEK SSR-40 DA (Solid State Relay)
Type: DC input, AC output
Size: ~27×27×10mm

INPUT SIDE (DC control):
├─ Voltage range: 3-32VDC
├─ Logic level: 3.3V compatible ✅
├─ Current: 10mA max input
├─ Hysteresis: ~0.5V typical
└─ Response: <1ms (electronic, no mechanical lag)

OUTPUT SIDE (AC motor):
├─ Voltage: 24-380VAC (handles 220V Mazzer!) ✅
├─ Current: 40A max (more than Mazzer needs ~0.5A)
├─ Protection: Zero-crossing detection
└─ Activation: ≥3V input = motor ON

CONNECTIONS:
├─ Input+ (control) → GPIO 2 (ESPPins 3.3V)
├─ Input- (control) → Ground
├─ Output1 → Motor Phase (AC Live)
├─ Output2 → Motor Neutral (AC Neutral)
└─ Safety: Motor MUST stop at power-off!

SAFETY FEATURES:
├─ Timeout: Motor auto-stops after 30 seconds
├─ Logic: HIGH = motor ON, LOW = motor OFF
├─ Fail-safe: Loss of power = motor off
└─ No manual bypass needed (all controlled by GPIO)
```

### **Protection Components**

```
Fuse 5A Ceramic:
├─ Rating: 5A (CRITICAL - not 3A or 10A!)
├─ Voltage: 250VAC minimum
├─ Type: Fast-blow ceramic
├─ Purpose: Protects AC mains circuit
├─ Location: Inline in AC mains
└─ Replacement: If motor shorts, fuse blows (good!)

Diode 1N4007:
├─ Type: Rectifier diode
├─ Rating: 1000V PIV, 1A
├─ Band marking: Cathode (negative side)
├─ Purpose: Blocks back-EMF spike from motor
├─ Placement: Across motor terminals
├─ Polarity: Band = GND side, other = Phase side
└─ Safety: Prevents relay damage from voltage spike

Capacitor 100nF:
├─ Value: 100nanofarad (same as 0.1µF)
├─ Voltage: 250V minimum
├─ Type: Film or ceramic (NON-POLARIZED!)
├─ Purpose: Filters EMI noise from motor
├─ Placement: Across motor terminals
├─ Frequency: Blocks noise >1kHz
└─ Critical: Use only non-polarized type!
```

### **Input Controls**

```
KY-040 Rotary Encoder:
├─ Type: Mechanical rotary encoder + button
├─ Detents: 20 per rotation (click-click-click)
├─ Pins: 5 (GND, +, CLK, DT, SW)
├─ Pull-ups: 10kΩ built-in
├─ Voltage: 5V typical (3.3V OK with pullups)
├─ Resolution: 1 detent = 1 step
└─ Use: Menu navigation, weight selection

CONNECTIONS:
├─ GND → Ground
├─ + → 3.3V
├─ CLK → GPIO 5 (clock signal)
├─ DT → GPIO 6 (direction signal)
└─ SW → GPIO 7 (push button)

DIP4 Tactile Switches (×2):
├─ Type: Momentary pushbutton
├─ Rating: 6×6×5mm standard
├─ Contact rating: 50mA @ 24VDC
├─ Voltage: Works at 3.3V
├─ Response: Audible/tactile click
└─ Bounce: ~20ms typical (debounce required)

CONNECTIONS:
Button 1 (START/GRIND):
├─ Pin 1 → GPIO 3
├─ Pin 2 → Ground
└─ Pull-up: Internal 20kΩ

Button 2 (MODE):
├─ Pin 1 → GPIO 4
├─ Pin 2 → Ground
└─ Pull-up: Internal 20kΩ

DEBOUNCE STRATEGY:
├─ Detection time: 20ms after press
├─ Confirmation: Must stay LOW for 20ms
├─ Release detection: Wait for HIGH again
└─ Re-arm: Clear flag after reading
```

---

## 📍 PIN ASSIGNMENTS & CONNECTIONS

### **ESP32-C3 Super Mini - GPIO Mapping**

```
┌─────────────────────────────────────────────────┐
│              ESP32-C3 Super Mini                │
│                  22.52 × 18mm                   │
└─────────────────────────────────────────────────┘

SIDE 1 (Left side):
┌─────────────────────────┐
│ GND  (Ground)           │ ← Power return
│ 3.3V (Power)            │ ← Power supply (from breadboard)
│ GPIO 0 (HX711 DOUT)     │ ← Load cell data
│ GPIO 1 (HX711 CLK)      │ ← Load cell clock
│ GPIO 2 (SSR Control)    │ ← Motor relay control
│ GPIO 3 (Start Button)   │ ← START/GRIND button
│ GPIO 4 (Mode Button)    │ ← MODE selector button
└─────────────────────────┘

SIDE 2 (Right side):
┌─────────────────────────┐
│ GPIO 5 (Encoder CLK)    │ ← Encoder clock
│ GPIO 6 (Encoder DT)     │ ← Encoder direction
│ GPIO 7 (Encoder SW)     │ ← Encoder push button
│ GND  (Ground)           │ ← Power return
└─────────────────────────┘

BACK (Not easily accessible):
├─ GPIO 8: Built-in LED (status indicator) ← Important!
├─ GPIO 9-12: Strapping pins (DO NOT USE!)
├─ GPIO 13: SPI MOSI (available but not used)
├─ GPIO 14: SPI CLK (available but not used)
├─ GPIO 15: SPI CS (available but not used)
├─ GPIO 20: I2C SDA (OLED data)
└─ GPIO 21: I2C SCL (OLED clock)

SUMMARY:
Used: 0,1,2,3,4,5,6,7,20,21 (10 pins) ✅
LED: GPIO 8
Free: None remaining
Strapping: 8-12 (reserved, don't touch)

I2C PINS (Built-in):
├─ SDA (Serial Data): GPIO 20
├─ SCL (Serial Clock): GPIO 21
└─ Voltage: 3.3V (with internal pull-ups)
```

### **Breadboard Wiring Diagram (Text Format)**

```
POWER SECTION:
┌─────────────────────────────────────────┐
│ Samsung Charger (5V/2A)                 │
│ USB-C Output                            │
└──────────┬────────────────────────────┘
           │
           ├─ +5V → Breadboard + rail (RED row)
           └─ GND → Breadboard - rail (BLACK row)

(Note: For 3.3V, we use ESP32's 3.3V output pin)
```

```
ESP32-C3 BREADBOARD PLACEMENT:
┌──────────────────────────────────────────┐
│         Breadboard (830-point)           │
├──────────────────────────────────────────┤
│                                          │
│  +5V ─────────────────────────────────── │ (RED row)
│   │                                      │
│   │  ┌──────────────────────┐            │
│   │  │  ESP32-C3 Super Mini │            │
│   │  │  ┌────────────────┐  │            │
│   │  │  │      USB-C     │  │            │
│   │  │  │    (top back)  │  │            │
│   │  │  └────────────────┘  │            │
│   │  │                       │            │
│   ├─────>3.3V ┌─────────┐   │            │
│   │         │ GND   3.3V│   │            │
│   │         │ GPIO 0  1 │   │            │
│   │         │ GPIO 2  3 │   │            │
│   │         │ GPIO 4  5 │   │            │
│   │         │ GPIO 6  7 │   │            │
│   │         │ GPIO8 GND │   │            │
│   │         │    (back) │   │            │
│   │         └─────────┘    │            │
│   │  │                      │            │
│  GND ─────────────────────────── │ (BLACK row)
│
[Other components connected via jumpers]
```

### **Component Connection Summary**

```
HX711 LOAD CELL CIRCUIT:
GPIO0 ──── HX711 DOUT
GPIO1 ──── HX711 CLK
3.3V  ──── HX711 VCC
GND   ──── HX711 GND
        └─ Load Cell (4 wires: Red, Black, White, Green)

OLED I2C DISPLAY:
GPIO20 ─── OLED SDA (Serial Data)
GPIO21 ─── OLED SCL (Serial Clock)
3.3V  ──── OLED VCC
GND   ──── OLED GND

BUTTONS (with internal pull-ups):
GPIO3 ──── Button 1 (START) ──── GND
GPIO4 ──── Button 2 (MODE)  ──── GND

ENCODER:
GPIO5 ──── Encoder CLK
GPIO6 ──── Encoder DT
GPIO7 ──── Encoder SW ──── GND
3.3V  ──── Encoder VCC
GND   ──── Encoder GND

MOTOR CONTROL:
GPIO2 ──── SSR-40 Input+ (control)
GND   ──── SSR-40 Input- (control)
      ┌─── SSR-40 Output1 ──── Motor Phase (AC 220V)
      └─── SSR-40 Output2 ──── Motor Neutral (AC)
          │
          └─── [5A Fuse] ─── [Diode 1N4007] ─── [Cap 100nF]
               (AC protection)

POWER:
5V (from charger) ──┬── Breadboard +5V rail
                    ├── ESP32 3.3V pin (if needed)
                    └── [Components via regulators if needed]

GND (from charger) ──── Breadboard -/GND rail (all GNDs connected)
```

---

## 🏗️ SOFTWARE ARCHITECTURE

### **Project Structure**

```
platformio_project/
├── platformio.ini (board, libraries, settings)
├── include/
│   ├── config.h (PIN definitions, constants)
│   ├── data_types.h (structs, enums)
│   ├── hx711.h (load cell driver)
│   ├── buttons.h (button handler)
│   ├── motor.h (SSR relay control)
│   ├── state_machine.h (main logic)
│   ├── display.h (OLED display functions)
│   └── encoder.h (rotary encoder handler)
│
├── src/
│   ├── main.cpp (main loop, initialization)
│   ├── hx711.cpp (load cell implementation)
│   ├── buttons.cpp (button debouncing)
│   ├── motor.cpp (motor control logic)
│   ├── state_machine.cpp (state transitions)
│   ├── display.cpp (display rendering)
│   └── encoder.cpp (encoder input handling)
│
└── lib/ (PlatformIO libraries, auto-managed)
```

### **Key Libraries Required**

```
platformio.ini dependencies:

[env:esp32-c3-devkitc-02]
platform = espressif32
board = esp32-c3-devkitc-02
framework = arduino

lib_deps =
    https://github.com/bogde/HX711.git
    adafruit/Adafruit SSD1306 @ ^2.5.0
    adafruit/Adafruit GFX Library @ ^1.11.0

IMPORTANT:
├─ HX711: For load cell reading
├─ Adafruit SSD1306: For OLED display (NOT TFT_eSPI!)
├─ Adafruit GFX: Graphics library (dependency)
└─ Arduino framework: Built-in (ESP32 support)
```

### **State Machine Design**

```
GRINDER MODE (Default):

    ┌─ IDLE
    │   ↓ (Button START pressed)
    ├─ SELECT_WEIGHT
    │   ↓ (Encoder turn)
    ├─ UPDATE_WEIGHT
    │   ↓ (Button START pressed again)
    ├─ GRINDING
    │   ├─ (weight reaches target)
    │   └─→ IDLE (motor stops)
    │
    └─ EMERGENCY (if motor runs >30s)
        └─→ MOTOR_STOP + ALARM

ESPRESSO MODE (Alternative):

    ┌─ ESPRESSO_IDLE
    │   ↓ (Button START pressed)
    ├─ PULL_SHOT
    │   ├─ (weight change detected)
    │   └─→ PULLING
    │
    ├─ PULLING (tracking weight ratio)
    │   ├─ (weight stable for 3s)
    │   └─→ SHOT_COMPLETE
    │
    └─ SHOT_COMPLETE
        └─ (display recommendation)

STATE ENUM:
typedef enum {
    IDLE,
    SELECT_WEIGHT,
    UPDATE_WEIGHT,
    GRINDING,
    ESPRESSO_IDLE,
    PULL_SHOT,
    PULLING,
    SHOT_COMPLETE,
    MOTOR_STOP,
    ALARM,
    ERROR
} SystemState_t;
```

### **Main Loop Flow**

```
setup() {
    // Initialize all modules
    Serial.begin(115200);
    hx711.begin(GPIO0, GPIO1);
    buttons.begin();
    motor.begin(GPIO2);
    encoder.begin();
    display.begin();
    stateMachine.init();
}

loop() {
    // Called ~10 times per second (100ms)
    
    // 1. Read sensors
    float weight = hx711.readWeight();
    
    // 2. Handle input
    buttons.update();  // debounce logic
    encoder.update();  // detect rotation
    
    // 3. Update state machine
    if (buttons.wasPressed(START_BTN)) {
        stateMachine.onEvent(BUTTON_START);
    }
    
    // 4. Control motor
    motor.update();
    if (stateMachine.shouldRunMotor()) {
        motor.start();
    } else {
        motor.stop();
    }
    
    // 5. Update display
    display.showWeight(weight);
    display.showState(stateMachine.getState());
    
    // 6. Safety checks
    if (motor.getRunningTime() > 30000) {
        motor.emergencyStop();
    }
}
```

---

## 📅 DEVELOPMENT TIMELINE

### **Week 1: Foundation & Core Modules**

**Days 1-2: Project Structure**
```
PROMPT 1.1: Create platformio.ini + main.cpp skeleton
├─ Board settings (ESP32-C3)
├─ Library dependencies
├─ GPIO pin definitions (config.h)
├─ Main loop structure
└─ Test: Code compiles successfully
```

**Days 2-4: HX711 Load Cell Driver**
```
PROMPT 1.2: Create hx711.h & hx711.cpp
├─ Bit-banging protocol (24-bit reads)
├─ Averaging filter (5-reading smoothing)
├─ Calibration system (offset + scale)
├─ Error handling
└─ Test: readWeight() returns reasonable values
```

**Days 4-5: Button Input System**
```
PROMPT 1.3: Create buttons.h & buttons.cpp
├─ Debounce logic (20ms settle time)
├─ Edge detection (falling edge = press)
├─ Non-blocking update() function
├─ Flag system (wasPressed vs isPressed)
└─ Test: Buttons detected without false triggers
```

**Day 5-6: Initial Testing**
```
Compile entire Week 1 code
└─ Fix any compilation errors
└─ Verify all modules link correctly
└─ Prepare for hardware testing
```

### **Week 2: Logic & Control**

**Days 6-7: Main State Machine**
```
PROMPT 2.1: Create state_machine.h & state_machine.cpp
├─ State enums (IDLE, GRINDING, ESPRESSO, etc)
├─ Event handling system
├─ Transition table (valid state changes)
├─ Timer management
└─ Test: Simulate state transitions manually
```

**Days 7-8: Motor Control (SSR Relay)**
```
PROMPT 2.2: Create motor.h & motor.cpp
├─ GPIO control (HIGH = ON, LOW = OFF)
├─ State tracking (OFF, STARTING, RUNNING, STOPPING)
├─ Timeout protection (30 second max)
├─ Error flags & status returns
└─ Test: Logic flow verified (no hardware yet)
```

**Days 8-9: Integration**
```
Connect state machine to motor control
├─ State machine calls motor.start()
├─ State machine monitors weight → motor.stop()
├─ Timeout logic verified
└─ Test: Full control flow works
```

**Days 9-10: Testing & Debug**
```
Test all modules together on SIMULATOR
├─ Compile all code
├─ Verify no errors
├─ Check logic flow
└─ Fix any issues before hardware
```

### **Week 3: Hardware Integration & Testing**

**Days 10-12: Physical Breadboard Setup**
```
Wire all components
├─ Power delivery (3.3V)
├─ ESP32 to breadboard
├─ OLED display (I2C)
├─ Load cell (HX711)
├─ Buttons
├─ Encoder
└─ Motor relay (with light bulb first, not motor)
```

**Days 12-14: Component-by-Component Testing**
```
Test each module on REAL HARDWARE:
├─ ESP32 LED blink (confirms USB & upload works)
├─ OLED display (can write text?)
├─ HX711 load cell (stable readings?)
├─ Buttons (debouncing working?)
├─ Encoder (direction detection?)
├─ Motor relay with light bulb (turns on/off?)
└─ Fix any hardware issues
```

**Days 14-20: Full System Integration**
```
All components working together
├─ Real weight readings
├─ Button input to state machine
├─ Motor control from state machine
├─ Display shows live data
├─ Safety timeouts working
└─ Complete debugging & refinement
```

**Day 21: Final Testing & Documentation**
```
✅ MVP Firmware Complete & Ready!
├─ All features working
├─ Documented code
├─ Testing procedures completed
└─ Ready for Mazzer grinder integration
```

---

## ⚠️ COMMON ERRORS & SOLUTIONS

### **Compilation Errors**

#### **Error: "HX711 not found"**
```
CAUSE: Missing #include or wrong filename

SOLUTION:
1. Verify hx711.h exists in include/ folder
2. In main.cpp, add: #include "hx711.h"
3. Verify filename case matches (Linux-sensitive)
4. Check platformio.ini has correct include paths

FIX CODE:
#include "config.h"
#include "hx711.h"  ← Must be in include/
#include "buttons.h"
#include "motor.h"
```

#### **Error: "undefined reference to HX711 constructor"**
```
CAUSE: hx711.cpp not compiled, or wrong class name

SOLUTION:
1. Verify hx711.cpp exists in src/ folder
2. Check class name matches in .h and .cpp
3. platformio.ini must list both .cpp files
4. Try: Clean build (PlatformIO → Clean)

PLATFORMIO.INI:
[env:esp32-c3-devkitc-02]
platform = espressif32
board = esp32-c3-devkitc-02
framework = arduino

lib_deps =
    https://github.com/bogde/HX711.git
    adafruit/Adafruit SSD1306 @ ^2.5.0
    adafruit/Adafruit GFX Library @ ^1.11.0
```

#### **Error: "I2C SCL/SDA not found"**
```
CAUSE: Wrong pin assignments or I2C not initialized

SOLUTION:
1. Check config.h has correct GPIO assignments
   #define I2C_SDA 21
   #define I2C_SCL 20

2. Initialize I2C in setup():
   Wire.begin(21, 20);  // SDA, SCL

3. Verify Adafruit_SSD1306 library installed
4. Check OLED address (0x3C or 0x3D)
```

### **Runtime Errors**

#### **ESP32 Not Recognized by Computer**
```
SYMPTOMS: Upload fails, device not found

SOLUTION:
1. Try different USB cable (data cable, not charge-only)
2. Install CH340 driver:
   - Windows: Download from WCH.CN
   - Mac: Install via Homebrew
   - Linux: Usually included

3. Check Device Manager (Windows):
   - Should show "COM port" or "CH340"
   - If "Unknown Device", driver is missing

4. Select correct board in PlatformIO:
   Board: esp32-c3-devkitc-02

5. Try manual reset:
   - Plug ESP32
   - Press RST button while uploading
```

#### **OLED Display Not Working**
```
SYMPTOMS: I2C scanner doesn't find device, or no display

SOLUTION:
1. Verify I2C wiring:
   SDA = GPIO 21 (data)
   SCL = GPIO 20 (clock)
   VCC = 3.3V
   GND = Ground

2. Run I2C scanner:
   Upload Adafruit I2C scanner example
   Serial monitor should show address (0x3C or 0x3D)

3. If no address found:
   - Check wiring again (swap SDA/SCL?)
   - Measure voltage on VCC pin (should be 3.3V)
   - Try 100Ω pull-up resistors on SDA/SCL

4. If address found but display blank:
   - Library might need init: display.begin(0x3C)
   - Check brightness settings
   - Verify OLED actually has 4 pins (not SPI variant)
```

#### **HX711 Not Reading Weight**
```
SYMPTOMS: Stuck at same value, or timeout errors

SOLUTION:
1. Verify wiring (DOUT=GPIO0, CLK=GPIO1):
   - DOUT must be INPUT (can read)
   - CLK must be OUTPUT (can write)

2. Check load cell connection to HX711:
   - Red wire → E+ (excitation positive)
   - Black wire → E- (excitation negative)
   - White wire → A+ (signal positive)
   - Green wire → A- (signal negative)

3. Timing issue:
   HX711 needs specific timing (1µs pulses)
   Arduino digitalWrite() might be too slow
   Solution: Use faster GPIO writes if available

4. Test with light load first:
   - Place 100g weight on load cell
   - Should see value change
   - If not, hardware issue (check wiring)

5. Calibration:
   hx711.tare();  // Zero the scale
   Then apply known weight, adjust scale factor
```

#### **Motor Not Starting (SSR Relay)**
```
SYMPTOMS: motor.start() called but motor doesn't run

SOLUTION:
1. Verify GPIO2 is actually changing state:
   Add Serial.println(digitalRead(GPIO2));
   Should show HIGH/LOW changes

2. Test SSR relay manually:
   - Use breadboard to connect GPIO2 → SSR input
   - Motor phase/neutral → SSR output
   - When HIGH, should hear SSR click

3. Check SSR wiring:
   - Input+ (control) → GPIO2 & 3.3V
   - Input- (control) → Ground
   - Output1 & Output2 → Motor terminals

4. Confirm 5A fuse is good:
   - Use multimeter continuity test
   - Should beep (not open circuit)

5. Test motor directly:
   - Plug motor into wall outlet temporarily
   - Does it work? (If not, motor issue, not relay)

6. Safety check:
   - motor.update() must be called every loop
   - 30-second timeout will auto-stop if running too long
```

#### **Buttons Not Debouncing**
```
SYMPTOMS: Buttons trigger multiple times per press

SOLUTION:
1. Verify debounce time (should be ~20ms):
   #define DEBOUNCE_MS 20

2. Check buttons.update() called every loop:
   loop() { buttons.update(); }

3. Verify button wiring:
   - Both pins of button → GND
   - GPIO pin should have internal pull-up enabled
   Serial.println(analogRead(GPIO3)); 
   Should read HIGH (~4095 on 12-bit ADC)
   Should drop to LOW (~0) when pressed

4. If still bouncing:
   - Increase debounce time to 30ms
   - Add external 0.1µF capacitor across button
```

### **Logic Errors**

#### **State Machine Stuck in State**
```
SYMPTOMS: State never changes despite events

SOLUTION:
1. Verify events are being generated:
   Add Serial debugging:
   if (stateMachine.wasEvent(EVENT_X)) {
       Serial.println("Event detected!");
   }

2. Check state transition table:
   Some transitions might be blocked
   Review switch/case logic

3. Verify event flags clear:
   wasPressed() should clear flag internally
   Can't re-trigger until button released

4. Test with simple state transition:
   IDLE → GRINDING → IDLE
   Simplest possible flow

5. Add debugging output:
   Serial.print("State: ");
   Serial.println(stateMachine.getState());
```

#### **Weight Reading Unstable"**
```
SYMPTOMS: Weight jumps around (±5g variance instead of ±0.5g)

SOLUTION:
1. Increase averaging filter:
   Currently: Average of 5 readings
   Try: Average of 10 readings (slower, more stable)

2. Check for EMI (electromagnetic interference):
   - Keep HX711 wires away from motor/relay
   - Use shielded cable if available
   - Capacitor 100nF should filter noise

3. Verify load cell is actually loading properly:
   - No side forces (must be centered)
   - No friction (load cell can rotate slightly)

4. Check ground connections:
   - All GNDs connected together
   - No ground loops

5. Power supply stability:
   - Measure 3.3V with multimeter
   - Should be stable (±0.1V)
   - If fluctuating, power issue
```

#### **Motor Won't Stop"**
```
SYMPTOMS: motor.stop() called but motor keeps running

SOLUTION:
1. Check GPIO2 state:
   Serial.println(digitalRead(GPIO2));
   Should show LOW after stop()

2. Verify SSR relay works:
   Manually test: HIGH → LOW should stop motor immediately

3. Check if code ever reaches motor.stop():
   Add Serial.println("Stop called!");

4. Emergency stop safety:
   - System auto-stops after 30 seconds
   - If running >30s, check timeout logic
   - Verify motor.update() called every loop

5. Hardware failure:
   - Test motor directly (plug into wall)
   - Does it have an on/off switch stuck?
   - If stuck running, motor issue, not ESP32
```

---

## 🧪 TESTING PROCEDURES

### **Phase 1: Compilation Testing (No Hardware)**

```
STEP 1: Build Project
1. Open PlatformIO → Build
2. Wait for compilation
3. Expected: "✓ Succeeded" message

If errors appear:
4. Check error messages carefully
5. Usually missing #include or wrong filename
6. Fix and rebuild

STEP 2: Fix Compilation Errors
Common issues:
- Missing #include "filename.h"
- Wrong file paths (check case sensitivity)
- Typos in function names
- Missing semicolons
- Unclosed brackets

STEP 3: Successful Compilation
Once "✓ Succeeded":
- Code is syntactically correct
- All includes found
- All functions defined
- Ready for upload to hardware
```

### **Phase 2: Hardware Bring-Up (Basic Tests)**

```
STEP 1: Power-On Test
1. Breadboard wired (power supply connected)
2. Connect charger (should light LED if present)
3. Measure voltage with multimeter:
   - 3.3V rail: Should read 3.3V ± 0.1V
   - GND: Should read 0V
4. No magic smoke 🎆 = Good!

STEP 2: ESP32 USB Connection
1. Connect ESP32 to computer via USB-C
2. Check Device Manager (Windows) or System Report (Mac)
3. Should show COM port or CH340 device
4. If not recognized:
   - Try different USB cable
   - Install CH340 driver (see driver section)

STEP 3: Upload Blink Sketch
1. Create simple test:
   void setup() { pinMode(8, OUTPUT); }
   void loop() { 
       digitalWrite(8, HIGH); delay(1000);
       digitalWrite(8, LOW);  delay(1000);
   }

2. Select correct board & COM port
3. Upload sketch
4. Watch ESP32 blue LED blink every second
5. If it blinks: Upload working! ✅

STEP 4: Serial Monitor
1. Open Serial Monitor (115200 baud)
2. Restart ESP32
3. Should see boot messages
4. No messages = Serial or driver issue
```

### **Phase 3: Component Testing (One at a Time)**

```
COMPONENT 1: HX711 Load Cell

Code:
#include "hx711.h"
HX711 hx711(0, 1);  // DOUT, CLK pins

void setup() {
    Serial.begin(115200);
    hx711.begin();
    hx711.tare();  // Zero with nothing on load
}

void loop() {
    float weight = hx711.readWeight();
    Serial.println(weight);  // Print weight
    delay(100);
}

Testing:
1. Upload code
2. Open Serial Monitor (115200 baud)
3. Place 100g weight on load cell
4. Should see value around 100
5. Remove weight, should return to ~0
6. Variance should be ±0.5g (not ±5g)

Expected values:
- Empty: ~0g (within ±1g tolerance)
- 100g weight: ~100g (±0.5g)
- 500g weight: ~500g (±2g typical)

Troubleshooting:
- Not reading? Check GPIO0, GPIO1 wires
- Always same value? Check load cell wires (Red/Black/White/Green)
- Very noisy? Add averaging filter, check for EMI
```

```
COMPONENT 2: OLED Display

Code:
#include <Wire.h>
#include <Adafruit_SSD1306.h>

Adafruit_SSD1306 display(128, 64, &Wire, -1);

void setup() {
    Serial.begin(115200);
    display.begin(SSD1306_I2C_ADDRESS, 0x3C);
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(1);  // White
    display.setCursor(0, 0);
    display.println("Hello World!");
    display.display();
}

void loop() {}

Testing:
1. Upload code
2. Should see "Hello World!" on OLED screen
3. If blank:
   - Check I2C address (0x3C or 0x3D)
   - Run I2C scanner to find address
   - Verify SDA=GPIO21, SCL=GPIO20

Expected output:
- Screen shows text clearly
- Text is bright white/blue
- No flickering
- Instant response to changes

Troubleshooting:
- Blank screen? Check wiring & address
- Dim display? Normal (OLED is bright)
- Only see part of text? Check cursor positioning
```

```
COMPONENT 3: Buttons

Code:
#include "buttons.h"

void setup() {
    Serial.begin(115200);
    buttons.begin();
}

void loop() {
    buttons.update();
    
    if (buttons.wasPressed(START_BUTTON)) {
        Serial.println("Start button pressed!");
    }
    
    if (buttons.wasPressed(MODE_BUTTON)) {
        Serial.println("Mode button pressed!");
    }
    
    delay(10);  // Update every 10ms
}

Testing:
1. Upload code
2. Open Serial Monitor (115200 baud)
3. Press START button
4. Should see "Start button pressed!" message
5. Press MODE button
6. Should see "Mode button pressed!" message
7. Each press = 1 message (no repeats = debouncing works!)

Expected behavior:
- One message per press
- ~20ms delay before message (debounce)
- No multiple messages from single press

Troubleshooting:
- Multiple messages? Debounce time too short
- No messages? Check GPIO3, GPIO4 wiring
- Always triggered? Check pull-up enabled
```

```
COMPONENT 4: Motor Control (Light Bulb Test)

⚠️ SAFETY FIRST!
NEVER test with actual motor initially!
Use 60W light bulb as motor substitute:
- Same AC 220V connection
- Visible indicator (glows when on)
- Safe to experiment
- No risk of unexpected spinup

Code:
#include "motor.h"

void setup() {
    Serial.begin(115200);
    motor.begin(GPIO2);
}

void loop() {
    // Test sequence
    Serial.println("Starting motor...");
    motor.start();
    delay(3000);  // Run 3 seconds
    
    Serial.println("Stopping motor...");
    motor.stop();
    delay(3000);  // Stop 3 seconds
    
    // Repeat
}

Testing:
1. Wire light bulb to SSR output instead of motor
2. Upload code
3. Watch light bulb:
   - Should glow for 3 seconds
   - Turn off for 3 seconds
   - Repeat pattern

Expected behavior:
- Light on = GPIO2 HIGH (SSR activated)
- Light off = GPIO2 LOW (SSR inactive)
- Timing precise (±100ms)
- No unexpected on/off

Troubleshooting:
- Light won't turn on? Check GPIO2 wiring to SSR
- Light stays on? Check GPIO2 LOW not working
- Flickering? Timing issue, but logic OK

Once light bulb test works:
→ Then ready to test with actual motor!
```

### **Phase 4: Full System Integration**

```
STEP 1: All Components Wired
Checklist:
☐ Power supply (3.3V working)
☐ ESP32 on breadboard
☐ OLED display (I2C)
☐ HX711 + load cell
☐ Buttons (×2)
☐ Encoder
☐ Motor relay (with light bulb for safety)

STEP 2: Run Integration Test
1. Upload complete firmware
2. Open Serial Monitor
3. Boot messages appear
4. Display shows initial state
5. Test each component:
   - Press button → State changes
   - Rotate encoder → Value changes
   - Motor starts → Light glows
   - Weight changes → Displayed
   - Timer reaches → Motor stops

STEP 3: Verify Safety Features
1. Motor auto-stop at 30 seconds
   - Motor starts
   - Run for 30+ seconds
   - Should auto-stop (safety works!)

2. Button debouncing
   - Press & hold button
   - Should only trigger once per press

3. Weight accuracy
   - Place test weight
   - Should read within ±0.5g

STEP 4: Test State Machine
1. IDLE → SELECT_WEIGHT (press button)
2. Adjust weight (rotate encoder)
3. SELECT → GRINDING (press button)
4. Watch weight decrease (light bulb on)
5. Stop when target reached
6. Back to IDLE

STEP 5: Document Test Results
Record:
- Timestamp of tests
- Pass/fail status
- Any issues found
- Fixes applied
- Final status (Ready/Not Ready)
```

---

## ✅ DEBUGGING CHECKLIST

When something doesn't work, use this checklist:

```
┌─ COMPILATION ERROR
│  ├─ All #include statements present?
│  ├─ All .cpp files in src/ folder?
│  ├─ platformio.ini has correct board?
│  ├─ Library dependencies listed?
│  ├─ No typos in filenames?
│  └─ Try: Clean rebuild (PlatformIO → Clean)
│
├─ UPLOAD FAILS
│  ├─ ESP32 connected via USB?
│  ├─ CH340 driver installed?
│  ├─ Correct COM port selected?
│  ├─ Correct board selected?
│  ├─ Try: Manual reset (hold RST while uploading)
│  └─ Check: Device Manager shows port
│
├─ CODE RUNS BUT NO OUTPUT
│  ├─ Serial.begin(115200) called?
│  ├─ Serial Monitor opened?
│  ├─ Baud rate 115200 selected?
│  ├─ Try: Restart ESP32 (power cycle)
│  └─ Check: Correct COM port
│
├─ DISPLAY SHOWS NOTHING
│  ├─ I2C address correct (0x3C or 0x3D)?
│  ├─ SDA/SCL wired to GPIO 21/20?
│  ├─ Voltage 3.3V on VCC pin?
│  ├─ GND connected?
│  ├─ Try: Run I2C scanner example
│  └─ Library Adafruit_SSD1306 installed?
│
├─ LOAD CELL READING WRONG
│  ├─ DOUT wire to GPIO 0?
│  ├─ CLK wire to GPIO 1?
│  ├─ Load cell 4 wires connected (Red/Black/White/Green)?
│  ├─ Calibration performed (tare)?
│  ├─ Check: No metal near load cell (EMI)
│  └─ Try: Increase averaging filter
│
├─ BUTTONS NOT WORKING
│  ├─ GPIO3/GPIO4 wired correctly?
│  ├─ Other pin to GND?
│  ├─ buttons.update() called in loop?
│  ├─ Debounce time >= 20ms?
│  ├─ Serial output shows events?
│  └─ Try: Press & hold to confirm physical press
│
├─ MOTOR WON'T START
│  ├─ GPIO2 wired to SSR input+?
│  ├─ GND wired to SSR input-?
│  ├─ SSR output wired to motor/bulb?
│  ├─ AC power plugged in?
│  ├─ 5A fuse intact (continuity check)?
│  ├─ Serial shows motor.start() called?
│  └─ Try: Check GPIO2 with multimeter (should go HIGH)
│
├─ STATE MACHINE STUCK
│  ├─ Events being generated?
│  ├─ State transition valid?
│  ├─ Event flags clearing properly?
│  ├─ Serial debug output enabled?
│  └─ Try: Simplest possible state flow
│
└─ SYSTEM UNSTABLE
   ├─ Voltage stable (3.3V ±0.1V)?
   ├─ No noise on power rails?
   ├─ All GND wires connected?
   ├─ Wires not crossing (EMI)?
   ├─ Try: Add 100µF capacitor on power
   └─ Check: No short circuits on breadboard
```

---

## 📚 REFERENCE MATERIALS

### **Datasheet Quick Links**

```
Component Datasheets:
├─ ESP32-C3: https://www.espressif.com/sites/default/files/documentation/esp32-c3_datasheet_en.pdf
├─ HX711: https://cdn.sparkfun.com/datasheets/Sensors/ForceFlex/HX711_datasheet.pdf
├─ SSD1306: https://cdn-shop.adafruit.com/datasheets/SSD1306.pdf
├─ FOTEK SSR-40: https://www.futurlec.com/Datasheet/Relay/SSR-40DA.pdf
└─ Load Cell: 2kg strain gauge specifications

Library Documentation:
├─ Arduino ESP32 Core: https://github.com/espressif/arduino-esp32
├─ Adafruit_SSD1306: https://github.com/adafruit/Adafruit_SSD1306
├─ HX711 Arduino: https://github.com/bogde/HX711
└─ Wire (I2C): Built-in Arduino library
```

### **Important Constants**

```
TIMING:
#define MAIN_LOOP_MS 100  // 100ms between readings
#define DEBOUNCE_MS 20    // 20ms button debounce
#define MOTOR_TIMEOUT_S 30 // Max 30 seconds running
#define I2C_SPEED 400000  // Standard I2C speed (400kHz)

HX711 CALIBRATION:
// Example values (must calibrate for actual load cell):
#define HX711_OFFSET 127000   // Empty scale reading
#define HX711_SCALE 411       // Counts per gram

STATE MACHINE:
#define STATE_COUNT 11  // Number of states

DISPLAY:
#define DISPLAY_WIDTH 128
#define DISPLAY_HEIGHT 64
#define DISPLAY_ADDRESS 0x3C  // or 0x3D, auto-detect
```

### **Useful Functions**

```
CORE FUNCTIONS:

HX711:
hx711.begin(DOUT_PIN, CLK_PIN);
float weight = hx711.readWeight();
hx711.tare();  // Zero scale
hx711.setCalibrationFactor(SCALE_FACTOR);

Buttons:
buttons.begin();
buttons.update();  // Every loop!
bool pressed = buttons.wasPressed(BUTTON_ID);
bool current = buttons.isPressed(BUTTON_ID);

Motor:
motor.begin(RELAY_PIN);
motor.start();
motor.stop();
motor.emergencyStop();
bool running = motor.isRunning();

Display:
display.begin(I2C_ADDRESS, 0x3C);
display.clearDisplay();
display.println("Text here");
display.display();  // Update screen

State Machine:
stateMachine.init();
stateMachine.update();
stateMachine.onEvent(EVENT_ID);
uint8_t state = stateMachine.getCurrentState();
```

### **Serial Debugging**

```
Essential for troubleshooting:

Baud rate: 115200
(Default Arduino setting)

Common patterns:

// Status check
Serial.print("Weight: ");
Serial.println(hx711.readWeight());

// Event logging
if (buttons.wasPressed(START_BTN)) {
    Serial.println("START PRESSED");
}

// State transitions
Serial.print("State: ");
Serial.println(stateMachine.getState());

// Variable inspection
Serial.print("GPIO2: ");
Serial.println(digitalRead(GPIO2));

// Timing
Serial.print("Time elapsed: ");
Serial.println(millis() - startTime);

// Error reporting
if (error_condition) {
    Serial.println("ERROR: Description");
    Serial.print("Debug info: ");
    Serial.println(debug_value);
}
```

---

## 🎯 WHEN YOU GET STUCK

**Follow this process:**

```
STEP 1: Identify the problem
├─ What's not working?
├─ When did it break?
├─ What changed before it broke?
└─ Compile error or runtime?

STEP 2: Search this guide
├─ Check "Common Errors & Solutions" section
├─ Check "Debugging Checklist"
├─ Read the relevant component section
└─ Most issues already documented!

STEP 3: Add debugging output
├─ Serial.println() at key points
├─ Print variable values
├─ Print state transitions
├─ Use Serial Monitor to observe

STEP 4: If still stuck
├─ Simplify: Test just one component
├─ Isolate: Disable other code
├─ Check wiring: Verify physical connections
├─ Measure: Use multimeter to verify voltages

STEP 5: Ask Claude Code for help
├─ Copy error message
├─ Describe what you tried
├─ Share relevant code snippet
├─ Claude will help debug!

EXAMPLE ASK:
"This code won't compile. Error: 'HX711' was not 
declared. I have hx711.h in include/ and hx711.cpp 
in src/. What's wrong?"

Claude will:
✅ Identify the issue
✅ Provide solution
✅ Explain why
✅ Help prevent future errors
```

---

## 📞 QUICK REFERENCE

**Most important things to remember:**

```
1. WIRING:
   └─ Double-check EVERY connection before power-on!

2. SAFETY:
   ├─ Never test motor directly initially (use light bulb)
   ├─ Motor auto-stops after 30 seconds
   └─ All wires properly connected

3. DEBUGGING:
   ├─ Use Serial Monitor (115200 baud)
   ├─ Add Serial.println() everywhere
   ├─ Test one component at a time
   └─ Use this guide!

4. COMPILATION:
   ├─ Check all #includes
   ├─ Verify file names match
   ├─ platformio.ini has libraries
   └─ Clean rebuild if stuck

5. HARDWARE BRING-UP:
   ├─ Power test first (no USB)
   ├─ USB recognition second
   ├─ LED blink third
   ├─ Component tests one-by-one
   └─ Integration last

6. WHEN STUCK:
   ├─ Read this guide
   ├─ Check Serial output
   ├─ Verify wiring
   ├─ Ask Claude Code
   └─ Test with simpler code
```

---

## 🚀 SUCCESS INDICATORS

**You're on the right track when:**

```
✅ Code compiles without errors
✅ ESP32 recognized by computer
✅ LED blinks (upload works)
✅ Serial Monitor shows output
✅ OLED displays text
✅ HX711 reads weight changes
✅ Buttons trigger events (no bounces)
✅ Motor starts/stops on command
✅ State machine transitions work
✅ All components communicate
✅ Complete firmware runs smoothly

When ALL these are working:
└─ Ready for production use! 🎉
```

---

**SELESAI! Ini adalah COMPLETE REFERENCE untuk Claude Code!** 🎉

**Dalam dokumen ini:**
✅ Semua hardware specifications
✅ Semua pin assignments
✅ Semua software architecture
✅ Semua common errors & solutions
✅ Semua testing procedures
✅ Semua debugging tips

**Kapan perlu help debugging firmware:**
1. Copy error message
2. Buka dokumen ini
3. Cari error di "Common Errors & Solutions"
4. Jika tidak ketemu, paste ke Claude Code:
   "I'm getting this error: [error]. I checked the reference guide but need help."

Claude akan instantly understand project context dari dokumen ini! 💪

