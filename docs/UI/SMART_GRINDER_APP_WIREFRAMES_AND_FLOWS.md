# Smart Grinder App - Wireframes & Interaction Flows

## Screen-by-Screen Detailed Layouts

---

## 1. GRIND MODE - Complete Wireframe

```
┌──────────────────────────────────────┐
│ 9:41                    📶📡🔋       │  Status Bar
├──────────────────────────────────────┤
│                                      │
│  Smart Grinder                       │  Header: "Smart Grinder"
│  Mazzer Mini D #1                    │  Subtitle: device name (tap to change)
│                                      │
├──────────────────────────────────────┤
│                                      │
│ ┌────────────────────────────────┐   │
│ │ CURRENT MODE                   │   │  Card 1: Mode Display
│ │ Grind Mode                     │   │
│ ├────────────────────────────────┤   │
│ │                                │   │
│ │   Grind Level                  │   │
│ │          6                     │   │  Large number display
│ │   [━━━━━━●━━━━━━]    0-10      │   │  Range slider
│ │                                │   │
│ │  ┌─────────────┬─────────────┐ │   │
│ │  │ Total Grinds│Operating Hrs│ │   │
│ │  │   1,247     │    42h      │ │   │
│ │  └─────────────┴─────────────┘ │   │
│ │                                │   │
│ │  ┌────────────┬────────────┐   │   │
│ │  │ 🟢 Start   │ ⏹️ Stop    │   │   │
│ │  └────────────┴────────────┘   │   │
│ │                                │   │
│ └────────────────────────────────┘   │
│                                      │
│ ┌────────────────────────────────┐   │
│ │ MAINTENANCE STATUS             │   │  Card 2: Maintenance
│ │                                │   │
│ │ ▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░   │   │  Progress bar
│ │ 35% interval                   │   │
│ │ 3,250 grinds until service     │   │
│ │                                │   │
│ └────────────────────────────────┘   │
│                                      │
│ (Scroll for more stats if needed)    │
│                                      │
├──────────────────────────────────────┤
│ ⚙️ Grind │ ☕ Espresso │ ⚖️ Scale      │  Bottom Navigation
│ GRIND    │ ESPRESSO   │ SCALE (inactive)
│          │            │
└──────────────────────────────────────┘
```

### Grind Mode - Motor State Machine

```
STATE DIAGRAM:

┌─────────────┐
│   IDLE      │  Initial state (motor off)
│  🟢 START   │  Button green & active
│  ⏹️ STOP    │  Button red & disabled
└──────┬──────┘
       │ User taps "Start"
       ▼
┌─────────────────────────┐
│   RUNNING               │
│  Motor is active        │  Shows spinner animation
│  🟢 START (disabled)    │  Button gray & inactive
│  ⏹️ STOP (active)       │  Button red & active
│  Sound: grinding noise  │
└──────┬──────────────────┘
       │ User taps "Stop" OR motor auto-stop
       ▼
┌──────────────────────────┐
│   STOPPING               │  Transition state (100ms)
│  Decelerating...         │  Show brief animation
└──────┬───────────────────┘
       │ Motor fully stopped
       ▼
┌──────────────────────────┐
│   IDLE (post-grind)      │
│  Update stats (grinds++) │  Count updated
│  Button ready for next   │
│  Session logged          │
└──────────────────────────┘

ERROR STATE (if occurs):
       Any state → ERROR
       ┌─────────────────────────┐
       │   ERROR                 │
       │  ❌ Motor Failed        │  Both buttons disabled
       │  [Retry] [Support]      │  Show error details
       └─────────────────────────┘
       │ User taps "Retry"
       ▼
       Back to IDLE (if successful)
       OR stays in ERROR (if failure repeats)
```

### Tap Interactions

```
Element              Action              Result
─────────────────────────────────────────────────────
Device name          Tap                 Open grinder selection
                                         dropdown (if multiple)

Grind Level slider   Drag 0-10           Update level in real-time
                     Release             Send to hardware

Start button         Tap                 → RUNNING state
                                         Motor starts
                                         Button becomes Stop

Stop button          Tap                 → STOPPING state
                                         Motor decelerates
                                         Stats update

Maintenance card     Tap                 Open full maintenance
                                         history dialog

Maintenance card     Long press          Copy stats to clipboard
                                         Show "Copied" toast

Card (entire)        Swipe up            Scroll for more details
                                         (if available)
```

---

## 2. ESPRESSO MODE - Complete Wireframe

```
┌──────────────────────────────────────┐
│ 9:41                    📶📡🔋       │  Status Bar
├──────────────────────────────────────┤
│                                      │
│  Espresso Mode                       │  Header
│  Track & optimize shots              │
│                                      │
├──────────────────────────────────────┤
│                                      │
│ ┌────────────────────────────────┐   │
│ │ Coffee Dose (g)                │   │  Input Card
│ │ [18_____________________]      │   │
│ │                                │   │
│ │ ┌────────────┬────────────┐    │   │
│ │ │ Time (s)   │ Yield (g)  │    │   │
│ │ │ [25_____]  │ [36_____]  │    │   │
│ │ └────────────┴────────────┘    │   │
│ │                                │   │
│ │ [Save Shot]                    │   │
│ │                                │   │
│ └────────────────────────────────┘   │
│                                      │
│ ┌────────────────────────────────┐   │
│ │ ✓ Perfect Extraction           │   │  Recommendation
│ │                                │   │  (green bg)
│ │ TDS 1.4% • 25s • 1:2 ratio     │   │
│ │ Keep this grind setting!       │   │
│ │                                │   │
│ └────────────────────────────────┘   │
│                                      │
│ RECENT SHOTS                         │
│ ┌────────────────────────────────┐   │
│ │ Yesterday                      │   │
│ │ 18g → 36g (25s)         [✓Perfect]│
│ │                                │   │
│ │ 2 days ago                     │   │
│ │ 18g → 35g (24s)         [⚠Adjust]│
│ │                                │   │
│ └────────────────────────────────┘   │
│                                      │
├──────────────────────────────────────┤
│ ⚙️ Grind │ ☕ ESPRESSO │ ⚖️ Scale     │  Bottom Nav
│ (inactive) ACTIVE     (inactive)
└──────────────────────────────────────┘
```

### Espresso Mode - Save Shot Flow

```
FLOW DIAGRAM:

User fills inputs (Coffee Dose, Time, Yield)
       │
       ▼
Taps [Save Shot] button
       │
       ▼
Validation:
├─ Dose > 0g? ─NO─→ Show error "Enter dose"
├─ Time > 0s? ─NO─→ Show error "Enter time"
└─ Yield > dose? ─NO─→ Show error "Yield must be > dose"
       │ YES (all valid)
       ▼
Calculate metrics:
├─ Ratio = yield / dose
├─ Extraction rate = (yield - dose) / time
└─ TDS % (from R2, Phase 2)
       │
       ▼
Generate recommendation:
├─ IF tds < 1.2% → "Grind finer"
├─ IF tds > 1.6% → "Grind coarser"
├─ IF time < 20s → "Extract too fast, grind finer"
├─ IF time > 30s → "Extract too slow, grind coarser"
└─ ELSE → "Perfect extraction"
       │
       ▼
Store in SQLite:
├─ Session record
├─ Metrics
├─ Recommendation
└─ Timestamp
       │
       ▼
Display success:
├─ Show recommendation card (animated slide-in)
├─ Clear input fields
├─ Add to Recent Shots list
└─ Toast: "Shot saved"
       │
       ▼
User can:
├─ Tap recommendation → View full analysis
├─ Tap recent shot → View shot details
└─ Adjust inputs → Save another shot
```

### Input Validation & Error States

```
Input Field         Validation Rule        Error Message
────────────────────────────────────────────────────────
Coffee Dose (g)     > 0 AND < 50           "Dose must be 0-50g"
                    Must be number          "Enter a number"

Extraction Time (s) > 0 AND < 120          "Time must be 0-120s"
                    Must be integer         "Enter seconds"

Yield (g)           > dose AND < 100       "Yield must be > dose"
                    Must be number          "Enter a number"

Real-time behavior:
- Show error (red text) below field when invalid
- Clear error as soon as user edits field (valid state)
- Save button DISABLED if any field invalid
- Save button ENABLED if all fields valid (green highlight)
```

### Recommendation Cards - Color Coding

```
PERFECT EXTRACTION (green background #E8F5E9):
┌─────────────────────────────────────┐
│ ✓ Perfect extraction                │  12px bold green
│                                     │
│ TDS 1.4% • 25s pull • 1:2 ratio    │  13px regular green
│ Keep this grind setting!            │
└─────────────────────────────────────┘

NEEDS ADJUSTMENT (amber background #FFF3E0):
┌─────────────────────────────────────┐
│ ⚠ Extraction too fast               │  12px bold amber
│                                     │
│ Time 22s (target 24-26s)            │  13px regular amber
│ Grind finer for next shot           │
└─────────────────────────────────────┘

CRITICAL ISSUE (red background #FFEBEE):
┌─────────────────────────────────────┐
│ ❌ Major extraction problem          │  12px bold red
│                                     │
│ TDS too low (0.8%) + time too fast  │  13px regular red
│ Reset grinder and try again         │
└─────────────────────────────────────┘
```

---

## 3. SCALE MODE - Complete Wireframe

```
┌──────────────────────────────────────┐
│ 9:41                    📶📡🔋       │  Status Bar
├──────────────────────────────────────┤
│                                      │
│  Scale Mode                          │  Header
│  Precision weighing                  │
│                                      │
├──────────────────────────────────────┤
│                                      │
│ ┌────────────────────────────────┐   │
│ │                                │   │
│ │  Current Weight                │   │  Display Card
│ │                                │   │
│ │        24.5g                   │   │  Large monospace
│ │                                │   │
│ │  ±0.05g precision              │   │  Caption
│ │                                │   │
│ └────────────────────────────────┘   │
│                                      │
│ [↺ Tare (Reset to 0)]               │  Tare button (full-width)
│                                      │
│ QUICK PORTIONS                       │
│ ┌────────────┬────────────┬────────┐│
│ │    17g     │    18g     │  19g  ││
│ └────────────┴────────────┴────────┘│
│ ┌────────────┬────────────┬────────┐│
│ │    20g     │    25g     │  30g  ││
│ └────────────┴────────────┴────────┘│
│                                      │
│ [⚙️ Customize Portions]              │  Settings (optional)
│                                      │
├──────────────────────────────────────┤
│ ⚙️ Grind │ ☕ Espresso │ ⚖️ SCALE     │  Bottom Nav
│ (inactive) (inactive)  ACTIVE
└──────────────────────────────────────┘
```

### Scale Mode - Real-time Updates

```
Update Mechanism:

BLE Notification (10Hz):
┌──────────────────────────────────────┐
│ Receive weight packet from ESP32     │
│ every 100ms (10 times per second)    │
└──────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────┐
│ Parse weight float32 value           │
│ Apply calibration offset (if stored) │
└──────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────┐
│ Round to 0.1g precision              │
│ Example: 24.5123g → 24.5g            │
└──────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────┐
│ Update UI display with animation:    │
│ - If no change: no animation         │
│ - If changed: brief pulse/highlight  │
│ - Color: #D4A574 (coffee brown)      │
└──────────────────────────────────────┘
           │
           ▼
Repeat every 100ms

TARE Action:
User taps [↺ Tare]
       │
       ▼
Send TARE command to ESP32
       │
       ▼
ESP32 records current raw reading as offset
       │
       ▼
All future readings: raw - offset
       │
       ▼
Display immediately shows 0.0g
       │
       ▼
Show brief toast: "Scale tared"
```

### Quick Portions Button Behavior

```
Tap Button (e.g., "18g"):
       │
       ▼
Set target weight = button value
       │
       ▼
Visual feedback:
├─ Button highlight (scale 1.05)
├─ Show target indicator on display:
│  "Current: 24.5g → Target: 18.0g"
└─ Toast: "Target set to 18g"
       │
       ▼
Options for user:
├─ Remove from scale (auto-stop at 18g if scale beeping supported)
├─ Continue monitoring
└─ Tap another button for new target
```

---

## 4. TIMER MODE - Complete Wireframe

```
┌──────────────────────────────────────┐
│ 9:41                    📶📡🔋       │  Status Bar
├──────────────────────────────────────┤
│                                      │
│  Timer Mode                          │  Header
│  Brew timing & weight tracking       │
│                                      │
├──────────────────────────────────────┤
│                                      │
│ ┌────────────────────────────────┐   │
│ │                                │   │
│ │  ┌──────────────┬──────────────┐│   │
│ │  │ Time         │ Weight       ││   │  Display grid
│ │  │              │              ││   │
│ │  │   2:34       │    125g      ││   │  Monospace, 32px
│ │  └──────────────┴──────────────┘│   │
│ │                                │   │
│ │  ┌────────────┬────────────────┐│   │
│ │  │ ▶ Start    │  ⏸ Pause      ││   │  Control buttons
│ │  └────────────┴────────────────┘│   │  Green + Amber
│ │                                │   │
│ └────────────────────────────────┘   │
│                                      │
│ TIMER TYPE                           │
│ ┌──────────┬──────────┬──────────┐   │
│ │ Manual   │Auto-detect│ Hybrid  │   │
│ └──────────┴──────────┴──────────┘   │
│                                      │
├──────────────────────────────────────┤
│ ⚙️ Grind │ ☕ Espresso │ ⚖️ Scale     │  Bottom Nav
│ (inactive) (inactive)  (inactive)
└──────────────────────────────────────┘
```

### Timer Mode - Three Variants

```
1. MANUAL MODE (Default)
   ┌─────────────────────────────┐
   │ User clicks Start button     │
   │ Timer begins counting        │
   │ User monitors pouring       │
   │ User clicks Pause to stop   │
   └─────────────────────────────┘
   
   Behavior:
   - Complete control
   - Pause/resume available
   - Manual stop
   - Best for: Hand control, variable pace

2. AUTO-DETECT MODE
   ┌─────────────────────────────┐
   │ Show placeholder: "Ready..." │
   │ User places cup on scale     │
   │ Weight > 0.5g (threshold)    │
   │ → Timer auto-starts          │
   │ Pouring continues            │
   │ Weight drops below 0.2g      │
   │ → Timer auto-stops           │
   └─────────────────────────────┘
   
   Behavior:
   - Hands-free operation
   - No manual start/stop needed
   - Hysteresis: 0.5g start, 0.2g stop
   - Best for: French Press, immersion brews

3. HYBRID MODE
   ┌─────────────────────────────┐
   │ Timer ready (manual)         │
   │ + Auto-detection enabled     │
   │ Whichever triggers first:    │
   │ - Manual Start button        │
   │ OR                           │
   │ - Weight > 0.5g (auto)       │
   │                             │
   │ Stop: Pause button or auto   │
   │ (weight drop below 0.2g)     │
   └─────────────────────────────┘
   
   Behavior:
   - Safety net approach
   - Manual + auto backup
   - Best for: Flexibility, backup
```

### Timer Display Format

```
Time Display:
- Format: M:SS (minutes:seconds)
- Range: 0:00 to 59:59
- Font: monospace, 32px, coffee brown
- Update: every 1 second

Weight Display:
- Format: XXXg (with 0.1 decimal)
- Font: monospace, 32px, coffee brown
- Update: every 100ms (10Hz)
- Precision: ±0.05g

Example combinations:
0:05 / 50.0g    (5 seconds, 50 grams)
1:23 / 250.5g   (1:23, 250.5 grams)
59:59 / 500.0g  (max time, max weight)
```

---

## 5. MANUAL BREW MODE - Complete Wireframe

```
┌──────────────────────────────────────┐
│ 9:41                    📶📡🔋       │  Status Bar
├──────────────────────────────────────┤
│                                      │
│  Manual Brew Mode                    │  Header
│  Professional brew guidance          │
│                                      │
├──────────────────────────────────────┤
│                                      │
│ [Brew Method Selection             ▼]│
│ - V60                              │
│ - French Press                     │
│ - AeroPress                        │
│                                      │
│ ┌────────────────────────────────┐   │
│ │ Phase 1 of 3                   │   │  Phase Card
│ │ Bloom                          │   │  (active: 2px border)
│ │                                │   │
│ │ Pour slowly to saturate        │   │
│ │ coffee grounds evenly          │   │
│ │                                │   │
│ │ ┌──────────────┬──────────────┐│   │
│ │ │ Target: 60g  │ Duration: 45s││   │
│ │ └──────────────┴──────────────┘│   │
│ │                                │   │
│ │ ⚠ Pour rate too fast           │   │
│ │                                │   │
│ └────────────────────────────────┘   │
│                                      │
│ POUR RATE GRAPH                      │
│ [Line graph showing flow]            │
│ 0s ────────────────────── 45s        │
│                                      │
│ [Next Phase] button (or auto-adv)   │
│                                      │
├──────────────────────────────────────┤
│ ⚙️ Grind │ ☕ Espresso │ ⚖️ Scale     │  Bottom Nav
│ (inactive) (inactive)  (inactive)
└──────────────────────────────────────┘
```

### Manual Brew - Phase Progression

```
USER FLOW:

1. SELECT BREW METHOD
   Dropdown: [V60 selected]
       │
       ▼
2. PHASE 1 GUIDANCE
   Display: Bloom (45s, 60g target)
   User pours slowly, watch graph
   Three progression options:
   
   Option A: AUTO-ADVANCE
   └─ Weight reaches 60g → Auto-move to Phase 2
      (if enabled in settings)
   
   Option B: MANUAL BUTTON
   └─ Show [Next Phase] button
      User taps when ready → Phase 2
   
   Option C: TIMEOUT
   └─ 45s elapsed → Show "Time's up"
      User taps [Continue] → Phase 2
       │
       ▼
3. PHASE 2 GUIDANCE
   Display: Main Pour (165s, target 300g)
   Instructions update
   Graph resets, tracks this phase
   Feedback: "Pour rate too fast"
       │
       ▼
4. PHASE 3 GUIDANCE
   Display: Finish (45s, target 500g)
   Final phase
   Similar guidance
       │
       ▼
5. BREW COMPLETE
   Show completion screen:
   - Total time: 5:32
   - Technique score: 87/100
   - Feedback: "Great bloom, main pour slightly fast"
   - [Save Brew] [Brew Again] [View Details]
```

### Pour Rate Graph - Real-time Updates

```
Data Collection (every 100ms):

Previous weight: 48.3g
Current weight: 48.7g
Time delta: 100ms

Pour rate = (48.7 - 48.3) / 0.1s = 4.0 mL/s

Moving Average (last 10 readings):
[3.2, 3.8, 4.0, 4.2, 3.9, 4.1, 4.3, 4.0, 3.8, 4.0]
Average = 4.03 mL/s

Display:
- Current point: 4.0 mL/s (color-coded)
- Trend line: smooth curve of 10-reading average
- X-axis: time (0s to phase_target_time)
- Y-axis: pour rate (0-6 mL/s)

Color zones (example V60):
- Green zone: 3.0-5.0 mL/s (optimal)
- Yellow zones: 2.5-3.0 & 5.0-5.5 mL/s (caution)
- Red zones: <2.5 & >5.5 mL/s (adjust needed)
```

### Pour Rate Feedback Logic

```
REAL-TIME FEEDBACK (updated every 100ms):

IF current_rate < green_min:
  → Show: "🔴 Pour faster"
  → Color: red
  → Duration: 2-5 seconds

IF current_rate in green_zone:
  → Show: "🟢 Perfect pour rate"
  → Color: green
  → Duration: all the time

IF current_rate > green_max:
  → Show: "🔴 Pour slower"
  → Color: red
  → Duration: 2-5 seconds

IF current_rate in yellow:
  → Show: "🟡 Adjust pour speed"
  → Color: amber
  → Duration: 2-5 seconds

Feedback location: Above phase card
Font: 13px, semantic color, bold
Animation: fade in/out smoothly
```

### Manual Brew - Session Completion

```
AFTER LAST PHASE COMPLETED:

Screen shows:
┌────────────────────────────────────┐
│ ✓ Brew Complete!                   │
│                                    │
│ Total Time: 5:32                   │
│ Target Time: 5:25                  │
│ Coffee: 30g → Water: 500g          │
│                                    │
│ Technique Score: 87/100            │
│ 🟢 Excellent bloom                 │
│ 🟡 Main pour slightly fast         │
│ 🟢 Finish technique perfect        │
│                                    │
│ [Save Brew] [Brew Again]           │
│ [View Detailed Report]             │
│                                    │
└────────────────────────────────────┘

Data saved to SQLite:
- All phase data
- Pour rates
- Technique score
- Feedback array
- User can rate brew 1-5 stars (optional)
```

---

## 6. SETTINGS SCREEN - Complete Wireframe

```
┌──────────────────────────────────────┐
│ 9:41                    📶📡🔋       │  Status Bar
├──────────────────────────────────────┤
│                                      │
│  Settings                            │  Header
│  Configuration & preferences         │
│                                      │
├──────────────────────────────────────┤
│                                      │
│ CONNECTED DEVICE                     │  Section 1
│ ┌────────────────────────────────┐   │
│ │ La Mardjono Smart Scale        │   │
│ │ Bluetooth • Connected       ✓  │   │
│ │ RSSI: -45 dBm (strong)         │   │
│ │ [Disconnect] [Re-pair]         │   │
│ └────────────────────────────────┘   │
│                                      │
│ UNITS                                │  Section 2
│ [Grams (g)                        ▼]│
│                                      │
│ ACTIVE GRINDER                       │  Section 3
│ [Mazzer Mini D #1                 ▼]│
│ [+ Add New Grinder]                 │
│                                      │
│ BREW METHOD PRESETS                  │  Section 4
│ [V60 defaults]                      │
│ [French Press defaults]             │
│ [AeroPress defaults]                │
│ [+ Customize]                       │
│                                      │
│ DATA                                 │  Section 5
│ Total Shots Logged: 847             │
│ [Export Data] [Clear History]       │
│ Backup: Last 2 hours ago            │
│ [Backup Now]                        │
│                                      │
│ ABOUT                                │  Section 6
│ La Mardjono Instruments             │
│ Version 1.0.0                       │
│ © 2026 Dapur Mardjono               │
│                                      │
├──────────────────────────────────────┤
│ (Settings accessed via gesture)      │  Note
└──────────────────────────────────────┘
```

### Settings - Bluetooth Pairing Flow

```
FLOW DIAGRAM:

Tap [Disconnect]
       │
       ▼
Confirm dialog:
"Disconnect from La Mardjono Smart Scale?"
[Cancel]  [Disconnect]
       │ User taps Disconnect
       ▼
Bluetooth disconnects
       │
       ▼
Screen updates:
"Bluetooth • Disconnected ✗"
       │
       ▼
Tap [Re-pair]
       │
       ▼
App requests Bluetooth permission (if needed)
       │
       ▼
Show scanning spinner:
"Scanning for devices..."
       │
       ▼
Wait 3-5 seconds
       │
       ▼
Show list of discovered devices:
┌────────────────────────────────────┐
│ La Mardjono_ABC123    RSSI: -35 dBm│  Strongest signal first
│ La Mardjono_DEF456    RSSI: -55 dBm│
│ Other_Device          RSSI: -85 dBm│
└────────────────────────────────────┘
       │ User taps "La Mardjono_ABC123"
       ▼
Connecting...
       │
       ▼
Success or timeout after 10 seconds
       │
       ├─ SUCCESS: "Connected ✓"
       │           Device name saved as preferred
       │
       └─ FAILURE: "Connection failed"
                   [Retry] [Manual ID] [Support]
```

### Export Data Flow

```
Tap [Export Data]
       │
       ▼
Dialog: Choose format
  [CSV]  [JSON]  [PDF Report]
       │ User selects CSV
       ▼
Dialog: Choose time range
  [Last week]
  [Last month]
  [All time]
  [Custom date range]
       │ User selects All time
       ▼
App generates CSV file:
  - Headers: timestamp, mode, dose, yield, time, tds, score, etc
  - One row per session
  - All 847 shots in this example
       │
       ▼
Share dialog:
  [Save to files] [Email] [Share to app...]
       │ User selects Save to files
       ▼
File saved: /Downloads/smart_grinder_export_20260912.csv
       │
       ▼
Toast: "Export complete • smart_grinder_export_20260912.csv"
```

---

## Navigation & Gesture Map

```
GESTURE INTERACTIONS:

┌─────────────────────────────┐
│      SWIPE RIGHT            │  Exit any screen, navigate to next
│   (across screen)           │  OR open Settings from anywhere
│                             │
└─────────────────────────────┘

┌─────────────────────────────┐
│      SWIPE LEFT             │  Go back to previous screen
│   (across screen)           │
│                             │
└─────────────────────────────┘

┌─────────────────────────────┐
│      SWIPE UP               │  Scroll content up (if scrollable)
│    (bottom to top)          │  Show more stats/history
│                             │
└─────────────────────────────┘

┌─────────────────────────────┐
│      SWIPE DOWN             │  Scroll content down
│    (top to bottom)          │  Pull-to-refresh device status
│                             │
└─────────────────────────────┘

┌─────────────────────────────┐
│      LONG PRESS             │  Context menu or quick action
│   (hold 500-1000ms)         │  (device name, maintenance card, etc)
│                             │
└─────────────────────────────┘

┌─────────────────────────────┐
│      TAP                    │  Standard button/input interaction
│                             │
└─────────────────────────────┘
```

---

## Page Transition Diagrams

```
NAVIGATION TREE:

                    ┌─ Grind (default)
                    │
        App Start ──┼─ Espresso
                    │
                    ├─ Scale
                    │
                    ├─ Timer
                    │
                    ├─ Manual Brew
                    │
                    └─ Settings
                       ├─ Device pairing
                       ├─ Units selection
                       ├─ Grinder management
                       └─ Data export

TRANSITIONS:

Grind ←→ Espresso (tap nav buttons, smooth slide)
Espresso ←→ Scale
Scale ←→ Timer
Timer ←→ Manual Brew
Any ← Settings (swipe right / settings access)
Any → Settings (swipe left or tap settings)

Modal dialogs (overlay):
- Grinder selection (from Grind header)
- Maintenance history (from maintenance card)
- Bluetooth pairing (from Settings)
- Error alerts (from any screen)

Can dismiss modals by:
- Tap outside modal
- Tap close/cancel button
- Complete action (confirm button)
```

---

## Error States & Recovery

```
GENERAL ERROR PATTERN:

┌───────────────────────────────────┐
│ ❌ Error Title                    │  Icon + title (red)
│                                   │
│ Detailed explanation of what      │  Message (body text)
│ went wrong and what user can do   │
│                                   │
│ [Primary Action] [Secondary]      │  Action buttons
└───────────────────────────────────┘

Examples:

BLUETOOTH DISCONNECTION:
┌───────────────────────────────────┐
│ ❌ Device Disconnected            │
│                                   │
│ Connection lost. Tap to reconnect│  Auto-retry in 5 seconds...
│                                   │
│ [Retry] [Settings]                │
└───────────────────────────────────┘

SENSOR CALIBRATION NEEDED:
┌───────────────────────────────────┐
│ ⚠️ Calibration Needed             │
│                                   │
│ Load cell not calibrated.         │  One-time calibration
│ Tap to run calibration wizard.    │
│                                   │
│ [Calibrate] [Learn More]          │
└───────────────────────────────────┘

INVALID INPUT:
┌───────────────────────────────────┐
│ ⚠️ Invalid Dose                   │
│                                   │
│ Dose must be between 0-50g.       │  Inline, below input
│ You entered: -5g                  │
│                                   │
│ [OK]                              │
└───────────────────────────────────┘
```

---

## Accessibility Considerations

```
WCAG 2.1 AA Compliance:

Touch Targets:
- All buttons: minimum 44×44px (Android spec)
- All interactive elements: 44×44px effective area
- Spacing between targets: minimum 8px

Color Contrast:
- Text on white background: #333 (AAA, 12.63:1)
- Text on blue background: white (AA, 7.5:1)
- Text on amber background: dark text (AA, 5.5:1)

Font Sizing:
- Minimum body text: 14px
- No text smaller than 12px (except captions/hints)
- All font sizes scale with device settings

Labels & Descriptions:
- All buttons have text labels (not just icons)
- Form fields have visible labels
- Icons have contentDescription (Android)
- Graph has alt text description

Dynamic Text:
- Support text size adjustment (up to 200%)
- Long text wraps appropriately
- Numbers use monospace for clarity (no ambiguity)

Screen Reader Support:
- Semantic HTML structure
- Labels for all inputs
- Announce state changes (modal opening, etc)
- Skip links for complex sections (future)

Color Independence:
- Don't rely on color alone to convey meaning
- Use icons + text (e.g., ✓ + green + text "Perfect")
- Patterns (stripes, dots) for graph zones
```

---

## Animation & Timing

```
ANIMATIONS:

Micro-interactions:
- Button press: scale(0.98) + 100ms duration
- Slide in recommendation: translateY(20px) + fade, 300ms
- Toast notification: slideUp + fade, 300ms duration, stays 3s
- Loading spinner: 360° rotation, 1s loop

Transitions:
- Screen change (bottom nav): slide left/right, 300ms
- Modal open/close: fade + scale, 200ms
- List item addition: slideIn + fade, 200ms

Real-time Updates:
- Weight display: no animation (just update)
- Pour rate graph: draw new point, 100ms (not delayed)
- Grind level slider: instant feedback

Page Load:
- Initial load (after Bluetooth connect): 300ms fade-in
- Data population: progressive reveal, top-to-bottom
- Graph rendering: 500ms draw animation for initial load

CSS Properties:
- ease-out for enter animations (snappy)
- ease-in-out for page transitions (smooth)
- cubic-bezier(0.34, 1.56, 0.64, 1) for bouncy effect
```

---

**Document Version:** 1.0  
**Last Updated:** September 12, 2026  
**For:** Kotlin Android Development Team
