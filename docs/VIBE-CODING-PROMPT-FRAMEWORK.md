# VIBE CODING PROMPT FRAMEWORK - SMART GRINDER PROJECT

**Status:** Ready-to-use prompts for Claude Code development  
**Methodology:** Foundation-first, modular component approach  
**Timeline:** 2-3 weeks before hardware assembly  
**Goal:** Firmware READY when ESP32 & Display arrive  

---

## 📖 WHAT IS "VIBE CODING"?

```
VIBE CODING = Using Claude Code extension in VSCode
             to AI-assist code generation + development

HOW IT WORKS:
1. You write detailed prompt (specify requirements)
2. Copy prompt into Claude Code sidepanel
3. Claude generates code skeleton
4. You customize + test locally (simulator)
5. Repeat for each component/module
6. Integrate all modules together
7. Ready for hardware testing when ESP32 arrives!

BENEFITS:
✅ Fast development (hours not days)
✅ Professional code structure
✅ Component isolation (test each separately)
✅ Reusable patterns (learn as you go)
✅ Claude handles boilerplate (you handle logic)
✅ Ready for breadboard when hardware lands
```

---

## 🏗️ DEVELOPMENT ROADMAP (3-Week Timeline)

```
WEEK 1 (Foundation & Core):
├─ Project setup + library selection
├─ HX711 ADC driver (load cell reading)
├─ GPIO/button input system
└─ OLED/Display driver (no ST7789 yet, use simulator)

WEEK 2 (State Machine & Logic):
├─ Main state machine (IDLE, GRINDING, ESPRESSO, etc)
├─ Encoder input handling
├─ Motor control (SSR relay logic)
├─ Overshoot compensation algorithm
└─ UI menu system (navigation logic)

WEEK 3 (Integration & Testing):
├─ Integrate all modules
├─ Full system simulation
├─ Error handling & edge cases
├─ Documentation & comments
└─ Ready for ESP32 hardware test!
```

---

## 🎯 PROMPT STRUCTURE (How to Write Good Prompts)

```
ELEMENT 1: CONTEXT (What we're building)
"We're building a smart grinder controller for Mazzer Mini D.
 Component: HX711 24-bit ADC for 2kg load cell.
 This module reads weight and outputs values to main system."

ELEMENT 2: REQUIREMENTS (What the code must do)
"- Initialize HX711 on GPIO 0 (DOUT) and GPIO 1 (CLK)
 - Read weight value every 100ms
 - Apply offset calibration
 - Return weight as float (grams)
 - Handle error states"

ELEMENT 3: CONSTRAINTS (Technical limits)
"- Arduino framework (PlatformIO)
- ESP32-C3 Super Mini microcontroller
- Limited RAM (must be efficient)
- No floating-point math for performance
- 25Hz sampling adequate (100ms interval)"

ELEMENT 4: OUTPUT FORMAT (What you want back)
"Generate code as a complete .cpp module with:
 - Header file (.h) with function declarations
 - Implementation (.cpp) with full logic
 - Example usage showing how to call functions
 - Comments explaining each section"

ELEMENT 5: TESTING CRITERIA (How to verify)
"When run on ESP32:
 - Should read stable values (±0.5g variance)
 - Should detect overload (>2kg warning)
 - Should initialize without errors"
```

---

## 📋 PHASE 1: FOUNDATION & SETUP

### **PROMPT 1.1: Project Structure & Libraries**

```
Copy this entire prompt into Claude Code:

═══════════════════════════════════════════════════════════════

PROJECT: Smart Grinder Controller for Mazzer Mini D
TARGET: ESP32-C3 Super Mini (PlatformIO + Arduino)
PHASE: Week 1 - Foundation & Core Modules

TASK: Create project structure and library configuration

CONTEXT:
We're building a complete smart grinder controller with:
- Weight-based grinding (2kg load cell)
- Time-based mode (espresso pulling)
- Grinder maintenance tracking
- Adaptive learning (overshoot compensation)

REQUIREMENTS:
1. Create platformio.ini with correct dependencies:
   - Arduino framework for ESP32-C3
   - HX711 library (for load cell ADC)
   - TFT_eSPI library (for ST7789 display - Phase 2)
   - Libraries for GPIO, SPI, interrupts

2. Create main.cpp skeleton with:
   - Pin definitions (ESP32-C3 GPIO assignments)
   - Global variables for system state
   - Setup() function structure
   - loop() function skeleton
   - Comments for each major section

3. Create config.h header with:
   - PIN definitions (DOUT, CLK, SSR, Buttons, Encoder, Display SPI pins)
   - Sensor constants (2kg load cell, HX711 gain)
   - Timing constants (100ms read interval)
   - State machine enum (IDLE, GRINDING, ESPRESSO, etc)

4. Create data_types.h header with:
   - struct for sensor readings (weight, time)
   - struct for system state
   - struct for calibration data (stored in EEPROM)

CONSTRAINTS:
- ESP32-C3 has 11 GPIO available (GPIO 0-10, 13-15, 21)
- Limited RAM (~400KB) - must be efficient
- All code must compile without errors
- Must follow Arduino conventions

OUTPUT FORMAT:
Provide:
1. Complete platformio.ini with all dependencies
2. Main.cpp with function skeletons and comments
3. config.h with all pin/constant definitions
4. data_types.h with struct definitions
5. Brief explanation of structure

NOTES:
- No actual hardware testing yet (ESP32 not arrived)
- Code should compile successfully
- Structure should make it easy to add modules later
- Assume ST7789 display will be added in Phase 2

═══════════════════════════════════════════════════════════════
```

**Expected Output:**
- ✅ platformio.ini with HX711, Arduino, ESP32 board
- ✅ main.cpp with setup/loop skeleton
- ✅ config.h with all GPIO pins mapped
- ✅ data_types.h with structs
- ✅ Compiles without errors

---

### **PROMPT 1.2: HX711 ADC Driver Module**

```
Copy this entire prompt into Claude Code:

═══════════════════════════════════════════════════════════════

COMPONENT: HX711 24-bit ADC Module + 2kg Load Cell
PURPOSE: Read weight values for grind targeting
PHASE: Week 1 - Core module

TASK: Create HX711 driver library

CONTEXT:
The HX711 is a 24-bit ADC that reads analog voltage from
a load cell strain gauge. Our system needs:
- Stable weight readings every 100ms
- Calibration capability (tare/zero)
- Overload detection (>2kg warning)
- Noise filtering for accurate measurements

LOAD CELL SPECIFICATIONS:
- Capacity: 2kg (2000g max)
- Excitation: ~5V DC (we provide from ESP32)
- Output: ~2mV full scale (tiny signal!)
- Bridge type: Full Wheatstone bridge (4-wire)
- Wires: Red (Excitation+), Black (Excitation-), White (Signal+), Green (Signal-)

HX711 SPECIFICATIONS:
- ADC resolution: 24-bit (16,777,216 counts)
- Gain options: 128 (recommended for 2kg)
- Sampling rate: 80Hz (internal)
- Our read rate: 100ms (10Hz, well below max)

HARDWARE CONNECTIONS:
- HX711 E+ ← Load Cell Red wire
- HX711 E- ← Load Cell Black wire
- HX711 A+ ← Load Cell White wire
- HX711 A- ← Load Cell Green wire
- HX711 DOUT → ESP32 GPIO 0
- HX711 CLK → ESP32 GPIO 1
- HX711 VCC → 3.3V
- HX711 GND → GND

REQUIREMENTS:
1. Create hx711.h header file with:
   - HX711 class definition
   - Constructor (takes DOUT and CLK pin numbers)
   - Function: begin() - initialize pins and reset HX711
   - Function: readRaw() - read 24-bit raw ADC value
   - Function: readWeight() - return weight in grams
   - Function: tare() - zero/calibrate the scale
   - Function: setCalibrationFactor() - set grams-per-count ratio
   - Function: detect() - check if HX711 is responding

2. Create hx711.cpp implementation with:
   - GPIO pin setup (input for DOUT, output for CLK)
   - HX711 power-up sequence (30 clock pulses to initialize)
   - Bit-reading loop (read 24 bits + 1 gain select bit)
   - Read timing (pulse CLK, read DOUT, proper timing)
   - Averaging filter (average last 5 readings for stability)
   - Calibration storage (offset value in EEPROM)
   - Error handling (timeout if DOUT doesn't respond)

3. Design calibration system:
   - Offset: Reading with empty scale (subtract from all readings)
   - Scale factor: grams per ADC count (e.g., 0.01g per count)
   - Test mode: Print raw values for debugging
   - Return status: OK, OVERLOAD, DISCONNECTED, etc.

4. Performance considerations:
   - Read timing: ~10ms per read (must be fast!)
   - Noise filtering: Moving average of 5 readings
   - Blocking vs non-blocking: Use blocking (simpler for MVP)
   - Error codes: Define enum for different error states

ALGORITHM FOR READING:

SetPin CLK = LOW
Wait 1ms (power down time)

SetPin CLK = HIGH
Wait 50µs (settling time)

For i = 1 to 25:  // Read 24 bits + 1 gain select bit
    While DOUT = HIGH:  // Wait for data ready
        (timeout check - if >10ms, return error)
    SetPin CLK = HIGH
    Wait 1µs
    readBit = ReadPin(DOUT)  // Read data
    SetPin CLK = LOW
    Wait 1µs
    shiftResult left, add readBit

Return 24-bit result (ignore 25th bit)

CONSTRAINTS:
- Must be timing-sensitive (Arduino timing precise enough)
- Can't use delays >1ms (blocking main loop)
- Must handle GPIO operations directly (digitalWrite is slow)
- Stack space limited (no large arrays)

OUTPUT FORMAT:
1. Complete hx711.h header with class definition
2. Complete hx711.cpp with all functions implemented
3. Example usage code showing:
   - HX711 hx711(0, 1);  // DOUT on GPIO 0, CLK on GPIO 1
   - hx711.begin();
   - hx711.tare();  // Zero the scale
   - float weight = hx711.readWeight();  // Read weight
   - Serial output of weight value

4. Comments explaining:
   - Each function's purpose
   - Why timing matters
   - How calibration works
   - Typical values for 2kg load cell

TESTING (Simulation):
- Can be tested with Serial output (no hardware needed yet)
- Code should compile and run
- readWeight() should return reasonable values
- tare() should work without hardware (simulated)

═══════════════════════════════════════════════════════════════
```

**Expected Output:**
- ✅ hx711.h with class definition
- ✅ hx711.cpp with bit-reading algorithm
- ✅ Calibration factor handling
- ✅ Averaging filter implementation
- ✅ Example usage code
- ✅ Compiles successfully

---

### **PROMPT 1.3: Button Input System**

```
Copy this entire prompt into Claude Code:

═══════════════════════════════════════════════════════════════

COMPONENT: DIP4 Microswitch Buttons (×2)
PURPOSE: User input for start/grind and mode selection
PHASE: Week 1 - Core module

TASK: Create button input handler with debouncing

CONTEXT:
Two momentary pushbuttons:
- Button 1 (GPIO 3): START/GRIND - triggers grinding
- Button 2 (GPIO 4): MODE - switches between grinder/espresso modes

Both buttons are momentary contact (spring-return).
Must handle switch bounce (typical 20ms).

BUTTON BEHAVIOR:
- Momentary press (0.1-0.5 seconds)
- Spring returns to released state
- No held state (just detects press events)
- Need debounce to avoid false triggers

HARDWARE:
- Button connects from GPIO to GND
- Internal pull-up resistor enabled (GPIO set to INPUT_PULLUP)
- Button pressed = GPIO reads LOW
- Button released = GPIO reads HIGH

DEBOUNCE STRATEGY:
- Detect falling edge (transition from HIGH to LOW)
- Wait 20ms (bounce settle time)
- Confirm still LOW (true press)
- Set flag for main loop to handle
- Ignore further presses until released

REQUIREMENTS:
1. Create buttons.h header with:
   - Button class definition
   - Define for debounce delay (20ms)
   - Function: begin() - initialize pins
   - Function: update() - call every 10ms to update state
   - Function: wasPressed(button_id) - check if button pressed
   - Function: isPressed(button_id) - check current state
   - Enum for button IDs (START_BUTTON, MODE_BUTTON)

2. Create buttons.cpp implementation with:
   - State tracking (debounce counter for each button)
   - Edge detection (falling edge = press)
   - Debounce logic (wait 20ms, confirm)
   - Flag system (set flag when confirmed press)
   - wasPressed() clears flag after reading (one-time event)
   - isPressed() returns live state (current)

3. State machine per button:
   State 1: IDLE (waiting for press)
   State 2: PRESS_DETECTED (low pulse seen)
   State 3: DEBOUNCING (counting 20ms)
   State 4: CONFIRMED_PRESS (stable low)
   State 5: RELEASING (waiting for release)

4. Non-blocking operation:
   - update() takes <1ms to execute
   - Can be called in main loop
   - No delays or blocking code

ALGORITHM:

Each button tracks:
- current_state (HIGH/LOW)
- debounce_counter (0-20)
- press_flag (cleared after reading)

update() function:
  For each button:
    read current GPIO state
    
    if state changed from HIGH to LOW:
      debounce_counter = 0
      press_flag = FALSE
    
    if debounce_counter < 20:
      debounce_counter++
    
    if state is LOW and debounce_counter >= 20:
      if press_flag == FALSE:
        press_flag = TRUE  // Press confirmed!
    
    if state changed from LOW to HIGH:
      debounce_counter = 0
      // But don't clear press_flag yet
      // Wait for next button read

CONSTRAINTS:
- Must be non-blocking (no delay() calls)
- Must handle both buttons independently
- Debounce time must be configurable (20-50ms typical)
- Flag should clear after wasPressed() call

OUTPUT FORMAT:
1. Complete buttons.h header
2. Complete buttons.cpp with state machine
3. Example usage:
   - buttons.begin();
   - In loop: buttons.update();  // every 10ms
   - Check: if (buttons.wasPressed(START_BUTTON)) { ... }
4. Comments explaining debounce logic

TESTING:
- Can simulate by toggling state manually
- Check debounce: press shouldn't trigger 20 times per second
- Check non-blocking: update() completes in <1ms

═══════════════════════════════════════════════════════════════
```

**Expected Output:**
- ✅ buttons.h with class definition
- ✅ buttons.cpp with debounce state machine
- ✅ Non-blocking update() function
- ✅ Flag system for press detection
- ✅ Example usage code

---

## 📋 PHASE 2: STATE MACHINE & LOGIC

### **PROMPT 2.1: Main State Machine**

```
Copy this prompt into Claude Code (Week 2):

═══════════════════════════════════════════════════════════════

COMPONENT: Main State Machine
PURPOSE: Control system flow and mode switching
PHASE: Week 2 - Logic layer

TASK: Create main state machine for grinder/espresso modes

CONTEXT:
System has two major modes:
1. GRINDER MODE: Weight-based grinding
   - Set target weight (10-30g)
   - Press START to grind
   - Motor runs until target weight reached
   - Adaptive overshoot learning

2. ESPRESSO MODE: Pull shot tracking
   - Set input dose (shot pull weight)
   - Pull espresso shot
   - Track time and weight ratio
   - Recommend grind adjustment

STATE TRANSITIONS:

GRINDER MODE:
  IDLE → (press START) → SELECT_WEIGHT
  SELECT_WEIGHT → (encoder turn) → UPDATE_WEIGHT
  UPDATE_WEIGHT → (press START) → GRINDING
  GRINDING → (weight reached) → IDLE
  GRINDING → (press STOP) → IDLE [emergency stop]

ESPRESSO MODE:
  IDLE → (press MODE) → ESPRESSO_IDLE
  ESPRESSO_IDLE → (press START) → PULL_SHOT
  PULL_SHOT → (weight change detected) → PULLING
  PULLING → (weight stable for 3s) → SHOT_COMPLETE
  SHOT_COMPLETE → (recommend grind) → ESPRESSO_IDLE

EMERGENCY:
  ANY STATE → (press MODE + START together for 2s) → IDLE

REQUIREMENTS:
1. Create state_machine.h with:
   - Enum for all states
   - Enum for all modes
   - Enum for system events
   - StateMachine class definition
   - Function: init() - start in IDLE
   - Function: update() - call every 100ms
   - Function: onEvent(event) - handle events
   - Function: getCurrentState() - return current state

2. Create state_machine.cpp with:
   - State handling for each combination
   - Transition logic
   - Timer management (for timeouts)
   - Mode switching logic
   - Emergency stop handling

3. Event system:
   - BUTTON_START_PRESSED
   - BUTTON_MODE_PRESSED
   - WEIGHT_CHANGED
   - TIME_ELAPSED
   - TARGET_REACHED
   - ERROR_OCCURRED

4. Integration points:
   - Calls HX711 to get weight
   - Calls motor control (SSR relay)
   - Calls display (UI update)
   - Calls encoder (value input)

CONSTRAINTS:
- State changes only on defined transitions
- No state change without valid event
- Timeouts handled (motor runs >30s = error)
- Recovery from error states

OUTPUT FORMAT:
1. state_machine.h with complete definitions
2. state_machine.cpp with state machine logic
3. Transition table showing all valid transitions
4. Comments explaining state purposes
5. Example: state_machine.onEvent(BUTTON_START_PRESSED)

═══════════════════════════════════════════════════════════════
```

---

### **PROMPT 2.2: Motor Control (SSR Relay) Module**

```
Copy this prompt into Claude Code (Week 2):

═══════════════════════════════════════════════════════════════

COMPONENT: SSR-40 DA Relay Motor Control
PURPOSE: Control Mazzer Mini D motor via solid-state relay
PHASE: Week 2 - Motor control

TASK: Create motor control driver with safety features

CONTEXT:
SSR (Solid State Relay) switch at GPIO 2:
- HIGH (3.3V) = Motor ON
- LOW (0V) = Motor OFF
- Response time: <1ms (electronic)
- No mechanical wear

Motor specifications:
- Mazzer Mini D: AC 220V, ~110W, ~0.5A
- Startup inrush: ~2-3A (brief)
- Running current: ~0.5A
- No soft-start needed (SSR handles it)

SAFETY REQUIREMENTS:
1. Motor should never run >30 seconds (safety timeout)
2. Must stop immediately on emergency signal
3. No accidental restart (state verification)
4. Must log errors/faults

MOTOR OPERATIONS:
1. START: GPIO 2 = HIGH, wait 10ms (settling)
2. RUNNING: Monitor time elapsed
3. STOP: GPIO 2 = LOW, wait 50ms (settling time)
4. VERIFY: Check motor actually stopped

REQUIREMENTS:
1. Create motor.h with:
   - MotorControl class
   - Function: begin() - initialize GPIO
   - Function: start() - turn motor ON
   - Function: stop() - turn motor OFF
   - Function: update() - call every 100ms to check status
   - Function: isRunning() - return motor state
   - Function: getRunningTime() - milliseconds elapsed
   - Function: emergencyStop() - immediate shutdown
   - Enum for motor state (OFF, STARTING, RUNNING, STOPPING)

2. Create motor.cpp with:
   - GPIO control (digitalWrite for SSR pin)
   - State tracking
   - Runtime counter
   - Timeout detection (>30s = error)
   - Start/stop verification
   - Error flag system

3. Safety features:
   - Timeout: Motor stops automatically after 30s
   - Double-stop protection: Calling stop() twice is OK
   - Start verification: Read state after command
   - Graceful shutdown: Always stop motor on exit

4. Error handling:
   - ERR_TIMEOUT (motor running >30s)
   - ERR_START_FAILED (GPIO didn't go high)
   - ERR_STOP_FAILED (motor didn't respond)
   - Return status_t with error code

CONSTRAINTS:
- GPIO 2 must be OUTPUT mode
- SSR relay HIGH = motor ON (not inverted)
- Must be non-blocking
- update() must check timeout every 100ms

TIMING DIAGRAM:

start() called:
  t=0ms: GPIO 2 = HIGH
  t=10ms: Motor physically starts rotating
  t=50ms: Motor at full speed
  → Grind begins

stop() called:
  t=0ms: GPIO 2 = LOW
  t=1ms: SSR relay opens (electronic)
  t=100-200ms: Motor coasts to stop
  t=500ms: Motor fully stopped
  → Grind ends

ALGORITHM:

Motor state tracking:
  state = STOPPED
  running_time = 0
  last_update_time = 0

start():
  if state != STOPPED:
    return ERROR_ALREADY_RUNNING
  
  digitalWrite(SSR_PIN, HIGH)
  state = STARTING
  running_time = 0
  start_time = millis()

update():
  current_time = millis()
  elapsed = current_time - start_time
  
  if state == STARTING and elapsed > 50ms:
    state = RUNNING
  
  if state == RUNNING:
    if elapsed > 30000ms (30 seconds):
      emergencyStop()  // Safety timeout!
      error_flag = ERR_TIMEOUT

stop():
  if state == STOPPED:
    return OK
  
  digitalWrite(SSR_PIN, LOW)
  state = STOPPING
  stop_time = millis()
  running_time = millis() - start_time

OUTPUT FORMAT:
1. motor.h with complete class definition
2. motor.cpp with all functions
3. Timing diagrams for start/stop
4. Safety timeout explanation
5. Error handling examples

TESTING:
- Without hardware: Verify logic flow
- With hardware: Motor should run max 30s
- Emergency stop: Should stop immediately

═══════════════════════════════════════════════════════════════
```

---

## 📋 PHASE 3: INTEGRATION & TESTING

### **PROMPT 3.1: System Integration**

```
Copy this prompt into Claude Code (Week 3):

═══════════════════════════════════════════════════════════════

TASK: Integrate all modules together

CONTEXT:
Time to combine:
- HX711 load cell driver
- Button input system
- Motor control
- State machine
- Main loop

All components must work together.

REQUIREMENTS:
1. Create main.cpp that:
   - Initializes all modules in setup()
   - Calls all update() functions in loop()
   - Routes data between components
   - Handles errors from any module

2. Create control.h orchestrator:
   - GrinderController class
   - Connects state machine to motor
   - Connects buttons to state machine
   - Connects weight readings to state machine
   - Handles mode switching

3. Main loop structure:
   - Read sensors (HX711)
   - Update input (buttons)
   - Update state machine
   - Control motor (SSR)
   - Update display
   - Log data

FLOW DIAGRAM:

Button Press → State Machine → Motor Control → Motor ON
Weight Reading → State Machine → Check if target reached
If target reached → State Machine → Motor OFF

All synchronized in main loop.

OUTPUT:
- Integration code with all modules connected
- Timing diagram showing loop execution
- Error handling across modules

═══════════════════════════════════════════════════════════════
```

---

## 🎯 HOW TO USE THESE PROMPTS

### **Step-by-Step Process:**

```
WEEK 1 (Foundation):
┌─ Open VSCode + Claude Code extension
├─ Create PlatformIO project (arduino framework, ESP32-C3 board)
├─ Copy PROMPT 1.1 into Claude Code → Generate project structure
├─ Copy PROMPT 1.2 into Claude Code → Generate HX711 driver
├─ Copy PROMPT 1.3 into Claude Code → Generate button handler
├─ Test each module compiles
└─ Commit to git

WEEK 2 (Logic & Control):
┌─ Copy PROMPT 2.1 into Claude Code → Generate state machine
├─ Copy PROMPT 2.2 into Claude Code → Generate motor control
├─ Test components work together
├─ Run simulation (no hardware)
└─ Verify all compiles

WEEK 3 (Integration):
┌─ Copy PROMPT 3.1 into Claude Code → Full integration
├─ Test complete firmware compiles
├─ Verify logic flow
├─ Document all code
└─ Ready for ESP32 hardware!

WHEN ESP32 ARRIVES:
└─ Upload firmware → Should work immediately!
```

### **Tips for Best Results:**

```
1. Copy ENTIRE prompt into Claude Code (not just summary)
2. Read Claude's response carefully
3. Ask clarifying questions if needed:
   "Can you explain how the debounce logic works?"
   "Can you optimize this for faster execution?"
4. Test each module independently first
5. Then integrate with other modules
6. Keep code organized in separate files:
   - hx711.h / hx711.cpp
   - buttons.h / buttons.cpp
   - motor.h / motor.cpp
   - state_machine.h / state_machine.cpp
   - main.cpp
```

---

## 📊 ADDITIONAL PROMPTS YOU CAN USE

### **Optional: Encoder Input Module**

```
When ready, use similar structure for KY-040 encoder:
PROMPT: "Create rotary encoder driver for KY-040 with
 - CLK detection (GPIO 5)
 - DT direction (GPIO 6)
 - SW button (GPIO 7)
 - Quadrature decoding
 - Value range 10-30g (example)"
```

### **Optional: Display Driver**

```
When ST7789 Display arrives:
PROMPT: "Create ST7789 display driver using TFT_eSPI library
 - Initialize 240×240 IPS screen
 - Display functions: drawWeight(), drawMenu(), drawStatus()
 - SPI pins: CLK=GPIO14, MOSI=GPIO13, CS=GPIO15, DC=GPIO21
 - Refresh at 30Hz"
```

### **Optional: EEPROM Storage**

```
For persistent data (calibration, maintenance):
PROMPT: "Create EEPROM storage system
 - Store load cell calibration factor
 - Store grinding profiles
 - Store wear counter
 - CRC checksum for validation
 - Backup/restore functions"
```

---

## 🧪 TESTING WITHOUT HARDWARE

```
WHAT YOU CAN TEST NOW:
✅ Code compiles without errors
✅ Logic flow (state machine transitions)
✅ Button debouncing algorithm
✅ Weight reading simulation
✅ Motor control timing
✅ Error handling

WHAT YOU CAN'T TEST YET:
❌ Actual hardware responses
❌ Real sensor values
❌ Display rendering
❌ Motor running
❌ End-to-end timing

WHEN ESP32 ARRIVES:
→ All firmware ready!
→ Upload and test immediately
→ Should work first try (if development done well)
```

---

## 💡 VIBE CODING BEST PRACTICES

```
DO:
✅ Write detailed prompts (more context = better code)
✅ Ask Claude to explain concepts (learn as you go)
✅ Test each module separately
✅ Keep components isolated
✅ Use meaningful variable/function names
✅ Add comments explaining why (not just what)
✅ Ask for optimization if code seems slow

DON'T:
❌ Copy-paste code you don't understand
❌ Skip testing individual modules
❌ Mix too many requirements in one prompt
❌ Ignore compiler warnings
❌ Integrate before testing components
❌ Rush through documentation
❌ Change code without understanding it
```

---

## 📞 WHEN YOU GET STUCK

```
Ask Claude Code:
1. "This code won't compile. What's the error and fix?"
2. "Can you optimize this for speed/memory?"
3. "How does [function] work exactly?"
4. "What's the best way to handle this edge case?"
5. "Can you refactor this for clarity?"

Claude Code is VERY good at:
- Debugging compilation errors
- Explaining code concepts
- Optimizing for performance
- Refactoring for clarity
- Adding error handling
- Explaining edge cases

Use it liberally! Questions = learning.
```

---

## 🎯 FINAL TIMELINE

```
NOW (This week):
└─ Save these prompts
└─ Review development roadmap
└─ Prepare VSCode + PlatformIO

WEEK 1 (Sep 12-18):
└─ Use PROMPT 1.1, 1.2, 1.3
└─ Build foundation + core modules
└─ Test compilation

WEEK 2 (Sep 19-25):
└─ Use PROMPT 2.1, 2.2
└─ Build state machine + motor control
└─ Test integration

WEEK 3 (Sep 26 - Oct 2):
└─ Use PROMPT 3.1
└─ Full system integration
└─ Ready for hardware!

Oct 3+:
ESP32 arrives → Upload → Works immediately! 🚀
```

---

## ✅ SUCCESS CRITERIA

**By end of Week 3, you should have:**

```
✅ Project compiles without errors
✅ All modules tested independently
✅ State machine transitions work
✅ Motor control logic verified
✅ Button debouncing confirmed
✅ Complete documentation
✅ Ready for ESP32 hardware test

Result: Firmware READY before hardware arrives!
```

---

**THIS IS YOUR VIBE CODING ROADMAP!** 🚀

Save this file. Use the prompts. Build firmware ahead of time.

When ESP32 arrives → Upload → Test immediately!

**Agung, kamu bisa mulai SEKARANG tanpa tunggu hardware!** 💪

