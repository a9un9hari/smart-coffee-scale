# Smart Grinder App - Complete UI/UX Design Documentation
## Android App for La Mardjono Instruments

**Version:** 1.0  
**Platform:** Android (API 24+)  
**Tech Stack:** Kotlin + Android Studio, Material Design 3, Jetpack Compose  
**Design System:** Royal Blue (#1E56DB) + Coffee Brown (#D4A574)

---

## Table of Contents
1. [Design System](#design-system)
2. [Navigation Structure](#navigation-structure)
3. [Screen Specifications](#screen-specifications)
4. [Component Library](#component-library)
5. [Interaction Patterns](#interaction-patterns)
6. [Data Structures](#data-structures)

---

## Design System

### Color Palette

| Element | Color | Hex | Usage |
|---------|-------|-----|-------|
| Primary | Royal Blue | #1E56DB | Buttons, headers, active states |
| Accent | Coffee Brown | #D4A574 | Numbers, highlights, status |
| Success | Green | #4CAF50 | Positive feedback, perfect extraction |
| Warning | Amber | #FF9800 | Cautions, attention needed |
| Danger | Red | #e74c3c | Critical alerts, stop buttons |
| Background | Light Gray | #fafafa | Page background |
| Surface | White | #ffffff | Cards, containers |
| Border | Light Gray | #e0e0e0 | Dividers, borders |
| Text Primary | Dark Gray | #333333 | Body text |
| Text Secondary | Medium Gray | #666666 | Supporting text |
| Text Muted | Light Gray | #999999 | Disabled, hints |

### Typography

```
Headlines:
- H1: 24px, weight 600, color #1E56DB (page titles)
- H2: 18px, weight 600, color #333333 (section headers)
- H3: 16px, weight 600, color #333333 (card titles)

Body:
- Regular: 14px, weight 400, line-height 1.5
- Small: 13px, weight 400, color #666666 (supporting)
- Micro: 12px, weight 400, color #999999 (captions)
- Mono: "Monaco" or "Courier New" (weight values, timestamps)

Button: 14px, weight 600, sentence case
```

### Spacing Scale
```
Padding/Margins:
- XS: 4px (internal component spacing)
- SM: 8px (small gaps)
- MD: 12px (default gaps)
- LG: 16px (section spacing)
- XL: 20px (container padding)
- 2XL: 24px (large gaps)
```

### Border Radius
```
- Small components (buttons, inputs): 6px
- Cards: 12px
- Modal/overlay: 16px
```

---

## Navigation Structure

### Bottom Navigation (5 Tabs)

```
┌─────────────────────────────────────┐
│ ⚙️  ☕  ⚖️  ⏱️  📊                    │
│ GRIND ESPRESSO SCALE TIMER BREW    │
└─────────────────────────────────────┘

Active tab: Blue icon + label + top border #1E56DB
Inactive tab: Gray icon + label
```

### Hierarchy
1. **Grind Mode** (PRIMARY) - Hardware control
2. **Espresso Mode** - Shot tracking & AI recommendations
3. **Scale Mode** - Basic weighing
4. **Timer Mode** - Brew timing with 3 variants
5. **Manual Brew** - Professional brew guidance
6. **Settings** (via swipe or settings icon)

---

## Screen Specifications

### 1. GRIND MODE SCREEN

**Purpose:** Control smart grinder hardware, track performance

**Key Components:**

#### Header Section
```
┌─────────────────────────┐
│ Smart Grinder           │
│ Mazzer Mini D #1        │
└─────────────────────────┘
- Title: 24px, #1E56DB
- Subtitle: 12px, #888888 (gray)
- Indicates active device
```

#### Current Mode Card (Primary)
```
┌────────────────────────────────┐
│ CURRENT MODE                   │
│ Grind Mode                     │
│ ─────────────────────────────  │
│                                │
│ Grind Level                    │
│         6                      │  (48px, #D4A574)
│ [━━━━━━●━━━━━━]  0-10 slider   │
│                                │
│ ┌──────────┬──────────┐        │
│ │Total     │Operating │        │
│ │Grinds    │Hours     │        │
│ │1,247     │42h       │        │
│ └──────────┴──────────┘        │
│                                │
│ ┌────────────┬─────────────┐   │
│ │  🟢 Start  │  ⏹️ Stop     │   │
│ └────────────┴─────────────┘   │
└────────────────────────────────┘

- Card: white bg, 1px #e0e0e0 border, 12px radius, 20px padding
- Slider: custom range input, 100% width
- Buttons: full-width grid, height 44px minimum
```

#### Maintenance Status Section
```
┌─────────────────────────┐
│ MAINTENANCE STATUS      │
│ ▓▓▓░░░░░░░░░░░░░░░░░░  │  35%
│ 35% interval            │
│ 3,250 grinds until      │
│ service needed          │
└─────────────────────────┘

- Progress bar: linear gradient green→amber
- Color coding: 0-50% green, 50-75% amber, 75-95% orange, >95% red
- Spans full progress before showing red alert
```

#### Motor Control Logic
```
Button State Machine:
- Connected (idle) → GREEN/ACTIVE: "🟢 Start" clickable
- Running → ACTIVE: "⏹️ Stop" clickable, "Start" disabled
- Error → RED: Both disabled, show error message

Visual Feedback:
- On press: Button scales to 0.98 (active state)
- Motor start: Pulse animation (2s), spinner shows
- Motor stop: Immediate button return to normal
```

**Data Displayed:**
- Total grinds (accumulated)
- Operating hours (uptime)
- Current grind level (0-10)
- Maintenance percentage + timeline
- Last grind timestamp (optional)

**Interactions:**
- Slider: adjust grind level 0-10
- Start/Stop: hardware control via Bluetooth
- Long press maintenance card: open maintenance history
- Tap device name: change active grinder

---

### 2. ESPRESSO MODE SCREEN

**Purpose:** Track espresso shots, receive AI grind recommendations

**Key Components:**

#### Input Card
```
┌─────────────────────────────────┐
│ Coffee Dose (g)                 │
│ [18_________]                   │
│                                 │
│ ┌──────────────┬──────────────┐ │
│ │ Time (s)     │ Yield (g)    │ │
│ │ [25______]   │ [36______]   │ │
│ └──────────────┴──────────────┘ │
│                                 │
│ [Save Shot]                     │
└─────────────────────────────────┘

- Input fields: 44px height, 1px #ddd border, 10px padding
- Save button: full-width, #1E56DB, white text, 44px
```

#### AI Recommendation Card
```
Recommendation Types:

✓ PERFECT (green bg #E8F5E9):
  "Perfect extraction • TDS 1.4% • 25s pull • 1:2 ratio
   Keep this grind setting!"

⚠ ADJUST (amber bg #FFF3E0):
  "Too fast • TDS 1.1% • 22s pull
   Grind finer for next shot"

❌ REDO (red bg #FFEBEE):
  "Major extraction issue
   Reset grind and retry"

- Background: semantic color, 4px left border
- Icon + label (12px, bold)
- Message (13px, regular)
- Padding: 16px all sides
```

#### Recent Shots List
```
Card Style (each shot):
┌─────────────────────────────────┐
│ Yesterday                       │
│ 18g → 36g (25s)       [Perfect]│
│                                 │
│ 2 days ago                      │
│ 18g → 35g (24s)       [Adjust] │
└─────────────────────────────────┘

- Timestamp: 12px, #666
- Dose→yield (time): 13px bold, #333
- Status badge: 11px, semantic color, 4px padding
- Divider between shots: 1px #f0f0f0
- Tap to view: full shot details
```

**Data Stored:**
```
{
  id: UUID,
  timestamp: ISO 8601,
  machine: String (selected),
  grinder_setting: 1-10,
  dose_grams: Float,
  yield_grams: Float,
  extraction_time_s: Int,
  tds_percent: Float (from R2 BLE, Phase 2),
  ratio: Float (1:1.8 to 1:2.2),
  quality_score: 0-100,
  recommendation: String,
  user_rating: 1-5 (optional)
}
```

**Recommendation Algorithm (Phase 2 with R2):**
```
IF tds < 1.2%:
  → "TDS too low, grind finer"
ELSE IF tds > 1.6%:
  → "TDS too high, grind coarser"
ELSE IF extraction_time < 20s:
  → "Extraction too fast, grind finer"
ELSE IF extraction_time > 30s:
  → "Extraction too slow, grind coarser"
ELSE IF tds 1.2-1.6% AND extraction 20-30s:
  → "Perfect extraction, keep same"
ELSE:
  → "Within acceptable range"
```

---

### 3. SCALE MODE SCREEN

**Purpose:** Standalone weighing for portioning, ingredients

**Key Components:**

#### Weight Display
```
┌─────────────────────────────────┐
│ Current Weight                  │
│                                 │
│        24.5g                    │  (56px, #D4A574, monospace)
│                                 │
│ ±0.05g precision                │  (12px, #999)
└─────────────────────────────────┘

- Monospace font to prevent jitter
- Decimal precision: 0.1g display
- Update rate: 10Hz (100ms)
```

#### Tare Button
```
[↺ Tare (Reset to 0)]

- Full-width, 44px height
- #1E56DB background
- Resets internal weight to 0
- Visual feedback: button press animation
```

#### Quick Portions Grid
```
┌──────────┬──────────┬──────────┐
│   17g    │   18g    │   19g    │
└──────────┴──────────┴──────────┘
┌──────────┬──────────┬──────────┐
│   20g    │   25g    │   30g    │
└──────────┴──────────┴──────────┘

- 3-column grid
- Tap to set target weight
- Button styling: gray bg, 1px #ddd border
- Height: 44px each
- User can customize preset values in settings
```

**Interactions:**
- Real-time weight display updates
- Tare clears accumulated weight
- Quick portions preset buttons
- Long-press for custom weight input
- Shake to cancel weight display (optional iOS feature)

---

### 4. TIMER MODE SCREEN

**Purpose:** Brew timing with flexible start methods

**Key Components:**

#### Display Grid
```
┌─────────────────────────┐
│ Time          Weight    │
│ 2:34          125g      │  (32px, monospace)
└─────────────────────────┘

- Two-column layout
- Time format: M:SS (1:30 to 59:59)
- Weight: grams with .1 precision
- Update rate: 1Hz for time, 10Hz for weight
```

#### Control Buttons
```
┌──────────────┬──────────────┐
│ ▶ Start      │ ⏸ Pause      │
└──────────────┴──────────────┘

- Green for start (#4CAF50)
- Amber for pause (#FF9800)
- 50/50 grid split
- 44px height
```

#### Timer Type Selector
```
┌──────────────┬──────────────┬──────────────┐
│   Manual     │ Auto-detect  │   Hybrid     │
└──────────────┴──────────────┴──────────────┘

Three modes:

1. MANUAL: User taps Start/Pause, complete control
   - Use case: Pourovers where you control pace
   - Constraints: None

2. AUTO-DETECT: Timer starts when weight > 0.5g
   - Use case: Hands-free brewing (French Press)
   - Constraints: Weight sensor required
   - Hysteresis: Must drop below 0.2g to reset

3. HYBRID: Both button + auto-detect active
   - Use case: Safety net + backup triggering
   - Logic: Whichever triggers first
   - Example: Button tap OR weight detection
```

**Data Recording:**
```
{
  mode: "timer",
  timer_type: "manual" | "auto" | "hybrid",
  coffee_weight: Float,
  water_weight: Float,
  total_time: Int (seconds),
  pour_rate_avg: Float (mL/s),
  ratio: Float (water/coffee),
  phases: [] (for brew-specific data)
}
```

---

### 5. MANUAL BREW MODE SCREEN

**Purpose:** Step-by-step brewing guidance with real-time feedback

**Key Components:**

#### Brew Method Selection
```
┌────────────────────────────────┐
│ [Select brew method...       ▼]│
│ - V60                          │
│ - French Press                 │
│ - AeroPress                    │
│ - Chemex                       │
│ - Turkish                      │
│ - Moka Pot                     │
└────────────────────────────────┘

- Dropdown/select: 44px height
- Default: "Select brew method..."
- Custom methods: user-defined in settings (Phase 2)
```

#### Current Phase Card
```
┌────────────────────────────────┐
│ Phase 1 of 3                   │  (12px, #999)
│ Bloom                          │  (18px, #333 bold)
│                                │
│ Pour slowly to saturate        │  (13px, #666)
│ coffee grounds evenly          │
│                                │
│ ┌────────────┬────────────┐   │
│ │Target Wt   │ Duration   │   │
│ │   60g      │    45s     │   │
│ └────────────┴────────────┘   │
│                                │
│ ⚠ Pour rate too fast          │  (amber background)
└────────────────────────────────┘

- Card: white bg, 2px #1E56DB border (active phase)
- Phase progression: manual "Next Phase" button or auto-advance on target reached
```

#### Flow Rate Graph
```
Real-time pour rate visualization:

    mL/s
    5.5  ┌────────────────────────┐ RED (too fast)
    5.0  │ ╱╲     ╱╲              │ GREEN (perfect)
    3.0  │╱  ╲   ╱  ╲╱╲           │
    2.5  └────────────────────────┘ YELLOW (suboptimal)
    0.0  └────────────────────────┘
         0s  10s  20s  30s  40s  45s

- Calculated: (weight_delta_g / time_delta_s)
- Smoothed: moving average (last 10 readings)
- Updated every 100ms
- Color zones adjustable per brew method
- Cumulative weight graph shown below (separate axis)
```

#### Brew Phase Presets
```
V60:
- Phase 1 (Bloom): 45s, target 60g
- Phase 2 (Main Pour): 165s, target 300g (cumulative)
- Phase 3 (Finish): 45s, target 500g (cumulative)
- Target pour rate: 3.0-5.0 mL/s

French Press:
- Phase 1 (Bloom): 30s, target 100g
- Phase 2 (Steep): 240s, target 500g
- Target pour rate: 0.5-2.0 mL/s (very slow)

AeroPress:
- Phase 1 (Pour): 60s, target 200g
- Phase 2 (Press): 30s, manual completion
- Target pour rate: 2.0-4.0 mL/s

Chemex:
- Phase 1 (Bloom): 45s, target 100g
- Phase 2 (Main): 180s, target 400g
- Phase 3 (Finish): 30s, target 650g
- Target pour rate: 2.0-4.0 mL/s

Turkish:
- Single phase: 120s, target 200g (watch for foam)

Moka Pot:
- Single phase: 600s (watch heat), manual monitoring
```

**Real-time Feedback:**
```
Performance zones (color-coded):
- 🟢 GREEN: Within optimal range → "Perfect pour rate"
- 🟡 YELLOW: Slightly off → "Adjust pour speed slightly"
- 🔴 RED (too slow): Below optimal → "Pour faster"
- 🔴 RED (too fast): Above optimal → "Pour slower"

Phase completion triggers:
- AUTO: Target weight reached → auto-advance or show "Next Phase"
- MANUAL: User taps "Next Phase" button
- Can skip phases if brewing technique differs

Feedback text: appears above phase card, max 20 chars
```

**Data Recording (per phase):**
```
{
  brew_mode: "manual",
  brew_method: String ("V60", etc),
  phases: [
    {
      name: String,
      duration_target: Int (s),
      duration_actual: Int (s),
      weight_target: Float (g),
      weight_actual: Float (g),
      pour_rate_avg: Float (mL/s),
      pour_rate_max: Float,
      pour_rate_min: Float,
      flow_stability: Float (0-100, σ of last 10 readings),
      feedback: String[]
    },
    ...
  ],
  total_time: Int (s),
  technique_score: 0-100,
  technique_feedback: String[]
}

Technique Score Calculation:
- Phase timing accuracy: 40% (how close to target time)
- Pour rate consistency: 40% (σ of pour rate in green zone)
- Weight accuracy: 20% (how close to target weight)
Result: Numeric 0-100 + categorical "Excellent/Good/Fair/Poor"
```

---

### 6. SETTINGS SCREEN

**Purpose:** Configuration, device pairing, preferences

**Key Components:**

#### Connected Device Section
```
┌────────────────────────────────┐
│ CONNECTED DEVICE               │
│                                │
│ La Mardjono Smart Scale        │
│ Bluetooth • Connected      ✓   │
│                                │
│ [Disconnect / Re-pair]         │
└────────────────────────────────┘

- Status badge: green checkmark if connected
- Tap to access Bluetooth pairing dialog
- Shows Bluetooth signal strength (visual bars)
- Last connected time
```

#### Units Selection
```
┌────────────────────────────────┐
│ UNITS                          │
│ [Grams (g)                  ▼] │
│ - Grams (g)                    │
│ - Ounces (oz)                  │
│ - Pounds (lb)                  │
└────────────────────────────────┘

- Applies to all weight displays
- Default: Grams
- Conversion automatic across all screens
```

#### Active Grinder
```
┌────────────────────────────────┐
│ ACTIVE GRINDER                 │
│ [Mazzer Mini D #1          ▼]  │
│ - Mazzer Mini D #1             │
│ - Baratza Sette 270            │
│ - Wilfa Svart                  │
│ [+ Add New Grinder]            │
└────────────────────────────────┘

- Select from saved grinders
- Tap "Add New" to register new device
- Each grinder has own maintenance counter
- Switching grinder switches to that grinder's learning profile
```

#### Brew Method Presets
```
┌────────────────────────────────┐
│ BREW METHOD PRESETS            │
│ [V60 defaults]                 │
│ [French Press defaults]        │
│ [AeroPress defaults]           │
│ [Chemex defaults]              │
│ [+ Customize]                  │
└────────────────────────────────┘

- Tap to view/edit phase targets
- Customize pour rates per method
- Save custom brewing profiles
```

#### Data Management
```
┌────────────────────────────────┐
│ DATA                           │
│ Total Shots Logged: 847        │
│ [Export Data] [Clear History]  │
│                                │
│ Backup Status: Last sync       │
│ 2 hours ago                    │
│ [Backup Now]                   │
└────────────────────────────────┘

- Export: CSV or JSON format
- Clear: confirm dialog, irreversible
- Cloud backup: future phase
```

#### About
```
┌────────────────────────────────┐
│ ABOUT                          │
│ La Mardjono Instruments        │
│ Version 1.0.0                  │
│                                │
│ © 2026 Dapur Mardjono          │
│ [Privacy] [Terms] [Support]    │
└────────────────────────────────┘

- Links to external URLs
- Version info
- Update check button
```

---

## Component Library

### Buttons

#### Primary Button
```
Height: 44px (minimum touch target)
Padding: 0 16px
Border-radius: 8px
Background: #1E56DB
Text: white, 14px bold, sentence case
State:
  - Default: #1E56DB
  - Hover: darken 10%
  - Active: scale(0.98)
  - Disabled: opacity 0.5
```

#### Secondary Button
```
Height: 44px
Padding: 0 16px
Border-radius: 8px
Background: white
Border: 1px #ddd
Text: #333, 14px bold
State:
  - Default: white + #ddd border
  - Hover: background #f5f5f5
  - Active: scale(0.98)
```

#### Danger Button
```
Same as primary, but:
Background: #e74c3c
```

### Form Inputs

#### Text Input / Number Input
```
Height: 44px
Padding: 10px 12px
Border-radius: 6px
Border: 1px #ddd
Font: 14px
Placeholder: 12px, #999
Focus: border #1E56DB, shadow 0 0 0 3px rgba(30,86,219,0.1)
```

#### Select Dropdown
```
Height: 44px
Padding: 10px 12px
Border-radius: 6px
Border: 1px #ddd
Font: 14px
Arrow: right-aligned
Focus: same as text input
```

#### Range Slider
```
Height: 4px track
Thumb: 18px diameter
Color: #D4A574 (fill), #ddd (background)
Focus: outline with accent color
Step: 1 (grind level, portions), 0.1 (weights)
```

### Cards

#### Standard Card
```
Background: white
Border: 1px #e0e0e0
Border-radius: 12px
Padding: 16px
Shadow: 0 1px 3px rgba(0,0,0,0.1)
```

#### Highlighted Card
```
Same as standard + 
Border-left: 4px semantic color (#4CAF50, #FF9800, #e74c3c)
```

#### Active Phase Card
```
Same as standard +
Border: 2px #1E56DB (thicker for emphasis)
```

### Badges & Labels

#### Status Badge
```
Display: inline-block
Padding: 4px 8px
Border-radius: 4px
Font-size: 11px
Font-weight: 600
Colors:
  - Success (green): bg #4CAF50, text white
  - Warning (amber): bg #FF9800, text white
  - Error (red): bg #e74c3c, text white
  - Info (blue): bg #1E56DB, text white
```

### Lists

#### Horizontal Divider
```
Height: 1px
Color: #e0e0e0
Margin: 12px 0
```

#### List Item
```
Padding: 12px 16px
Border-bottom: 1px #f0f0f0 (except last item)
Font: 14px, #333
Tap area: full width
State:
  - Active: background #f5f5f5
```

---

## Interaction Patterns

### Bluetooth Connection Flow

```
1. App Startup
   ├─ Check if saved device exists
   ├─ If yes: Auto-connect with spinner "Connecting..."
   └─ If no: Show pairing screen

2. Manual Pairing (Settings > Connect Device)
   ├─ Request Bluetooth permission (Android 6+)
   ├─ Scan for "La Mardjono_*" devices
   ├─ Show list with RSSI signal strength
   ├─ Tap device to connect
   ├─ Show connecting spinner...
   └─ Success: "Connected" with checkmark

3. Connection Loss
   ├─ Show alert: "Device disconnected"
   ├─ Auto-retry with exponential backoff (1s, 2s, 4s, 8s)
   ├─ After 3 failures: show reconnect button
   └─ Offline mode: use cached last reading

4. Multiple Devices (Future)
   ├─ Select device from dropdown
   ├─ Switch connection (1-2 sec reconnect)
   └─ Save preference
```

### Real-time Data Updates

```
Update Rate by Screen:

Grind Mode:
- Grind level: immediate (user slider)
- Motor status: 100ms
- Total counts: 1s (after motor stop)

Espresso Mode:
- Input fields: immediate (user input)
- Recommendation: on save button
- Shot history: on save

Scale Mode:
- Weight display: 100ms (10Hz)
- Precision: ±0.05g after 200ms settling

Timer Mode:
- Time display: 1Hz (updates every 1s)
- Weight display: 100ms (10Hz)
- Pour rate graph: 100ms (10Hz)

Manual Brew:
- Phase progress: 100ms (10Hz)
- Flow rate graph: 100ms (10Hz)
- Pour rate feedback: 100ms
```

### Error Handling

```
Connection Error:
┌─────────────────────────────┐
│ ❌ Connection Failed        │
│ Unable to connect to device │
│                             │
│ [Retry]         [Settings]  │
└─────────────────────────────┘

Sensor Error:
┌─────────────────────────────┐
│ ⚠️ Sensor Issue             │
│ Load cell not calibrated    │
│                             │
│ [Calibrate]  [Contact Help] │
└─────────────────────────────┘

Motor Error:
┌─────────────────────────────┐
│ ⚠️ Motor Issue              │
│ Motor won't stop            │
│                             │
│ [Emergency Stop]  [Retry]   │
└─────────────────────────────┘

All errors:
- Dismiss: tap outside or [OK]
- Log error with timestamp for debugging
- Retry logic: 3 attempts then escalate
```

### Gesture Interactions

```
Swipe Right: Go to Settings (from any tab)
Swipe Left: Return to previous tab
Long-press device name: Change active grinder
Long-press maintenance card: View full history
Double-tap weight in scale mode: Enter custom value
Shake device: Cancel current operation (iOS only, optional)
Pull-to-refresh: Refresh device connection status
```

---

## Data Structures

### Session Data (stored locally in SQLite)

```json
{
  "sessions": [
    {
      "id": "UUID",
      "timestamp": "2026-09-12T15:30:00Z",
      "mode": "grind|espresso|scale|timer|brew",
      "duration_seconds": 45,
      "device": "Mazzer Mini D #1",
      
      // Grind mode
      "grind_level": 6,
      "total_grinds_count": 2500,
      "grind_consistency_sigma": 0.15,
      
      // Espresso mode
      "espresso": {
        "coffee_weight": 18.0,
        "yield_weight": 36.0,
        "extraction_time": 25,
        "tds_percent": 1.4,
        "ratio": 2.0,
        "recommendation": "Perfect extraction",
        "quality_score": 95
      },
      
      // Timer mode
      "timer": {
        "coffee_weight": 30.0,
        "water_weight": 500.0,
        "total_time": 240,
        "timer_type": "manual|auto|hybrid",
        "pour_rate_avg": 2.08
      },
      
      // Brew mode
      "brew": {
        "method": "V60|French Press|AeroPress|Chemex|Turkish|Moka",
        "phases": [
          {
            "name": "Bloom",
            "target_time": 45,
            "actual_time": 47,
            "target_weight": 60,
            "actual_weight": 61,
            "pour_rate_avg": 1.3,
            "feedback": ["Perfect bloom", "Even saturation"]
          }
        ],
        "technique_score": 87,
        "technique_feedback": ["Great bloom", "Main pour slightly fast"]
      },
      
      // Maintenance
      "maintenance": {
        "total_operating_hours": 42.5,
        "total_grinds": 1247,
        "sigma_trend": [0.1, 0.12, 0.15, ...],
        "time_per_grind": [8.2, 8.3, 8.5, ...],
        "offset_variance": 0.08,
        "wear_score_percent": 35
      }
    }
  ]
}
```

### Bluetooth Protocol (HX711 Load Cell + ESP32)

```
Device: La Mardjono Smart Scale
Service UUID: TBD (custom)
Characteristics:
  - Weight: notify (Float32, little-endian, g)
  - Motor: write (1 byte: 0=stop, 1=start)
  - GrindLevel: write (1 byte: 0-10)
  - Status: notify (8 bytes: flags + counters)

Message Format:
Weight Update (10Hz):
  [0x01] [weight_bytes_4] [checksum_1] = 6 bytes

Motor Command:
  [0x02] [0x00|0x01] [checksum_1] = 3 bytes

Status Response:
  [0x03] [counters_4] [hours_2] [flags_1] = 8 bytes

Checksum: XOR of all data bytes
```

---

## Implementation Roadmap

### Phase 1 (Weeks 1-2): MVP
- ✅ UI mockups (this document)
- ✅ Navigation structure (5 tabs)
- [ ] Bluetooth connectivity (basic weight read)
- [ ] Grind Mode (hardware control)
- [ ] Scale Mode (basic weighing)
- [ ] Settings (device pairing)

### Phase 2 (Weeks 3-4): Core Features
- [ ] Espresso Mode (shot tracking without R2)
- [ ] Timer Mode (all 3 variants)
- [ ] Manual Brew Mode (basic phase guidance, no flow graphs)
- [ ] Local data persistence (SQLite)

### Phase 3 (Weeks 5-8): AI + R2 Integration
- [ ] Difluid R2 Bluetooth integration (BLE)
- [ ] AI grind recommendations (TDS-based)
- [ ] Flow rate graphs (pour rate visualization)
- [ ] Brew technique scoring
- [ ] Advanced maintenance tracking

### Phase 4 (Weeks 9+): Polish + Expansion
- [ ] Cloud sync & backup
- [ ] Multi-language support (English + Bahasa Indonesia)
- [ ] Custom brew method profiles
- [ ] Advanced analytics dashboard
- [ ] Export data (CSV, JSON)

---

## Design Tokens Summary

```css
/* Colors */
--primary: #1E56DB (royal blue)
--accent: #D4A574 (coffee brown)
--success: #4CAF50 (green)
--warning: #FF9800 (amber)
--danger: #e74c3c (red)
--surface: #ffffff (white)
--background: #fafafa (light gray)
--border: #e0e0e0 (border gray)
--text-primary: #333333
--text-secondary: #666666
--text-muted: #999999

/* Typography */
--font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif
--font-mono: 'Monaco', 'Courier New', monospace
--font-size-h1: 24px / 600
--font-size-h2: 18px / 600
--font-size-h3: 16px / 600
--font-size-body: 14px / 400
--font-size-small: 13px / 400
--font-size-micro: 12px / 400

/* Spacing */
--radius-sm: 6px
--radius-md: 12px
--radius-lg: 16px
--padding-xs: 4px
--padding-sm: 8px
--padding-md: 12px
--padding-lg: 16px
--padding-xl: 20px

/* Shadows */
--shadow-sm: 0 1px 3px rgba(0,0,0,0.1)
--shadow-md: 0 4px 12px rgba(0,0,0,0.1)
--shadow-lg: 0 8px 24px rgba(0,0,0,0.15)
```

---

## Notes for Developers

1. **Bluetooth Stability:** Implement exponential backoff for reconnection attempts
2. **Data Persistence:** Use SQLite for offline capability, sync to cloud later (Phase 4)
3. **Performance:** Real-time graph updates (100ms) may need optimization for older devices
4. **Accessibility:** All buttons minimum 44px tap target, WCAG 2.1 AA contrast
5. **Testing:** Test all Bluetooth edge cases (connection loss, reconnect, data corruption)
6. **Localization:** Prepare string resources for Bahasa Indonesia (Phase 4)
7. **Battery:** Optimize Bluetooth polling to reduce battery drain on device

---

**Document Version:** 1.0  
**Last Updated:** September 12, 2026  
**Author:** La Mardjono Instruments Design Team
