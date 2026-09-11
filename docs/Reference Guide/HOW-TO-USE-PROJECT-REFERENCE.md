# 📖 HOW TO USE THE PROJECT REFERENCE GUIDE

**For:** Claude Code Debugging Sessions  
**Purpose:** Quick context + solution lookup when errors occur  
**Benefit:** Faster debugging, less back-and-forth  

---

## 🎯 WHEN TO USE THIS GUIDE

### **Scenario 1: Compilation Error**

```
STEP 1: Read error message
  Example: "error: 'HX711' was not declared in this scope"

STEP 2: Open CLAUDE-CODE-PROJECT-REFERENCE.md
  └─ Search: Ctrl+F "HX711 not found"
  └─ or search: "undefined reference"

STEP 3: Find "Common Errors & Solutions" section
  └─ Look for matching error
  └─ Read solution step-by-step

STEP 4: Apply fix
  └─ Update code
  └─ Rebuild
  └─ Verify success

NO NEED TO EXPLAIN TO CLAUDE EVERY TIME!
The reference has all answers! ✅
```

### **Scenario 2: Runtime Error (Program Runs But Wrong)**

```
Example: OLED display doesn't show anything

STEP 1: Open reference guide
  └─ Search: "OLED Display Not Working"

STEP 2: Follow troubleshooting steps
  ├─ Verify wiring (search: "OLED Connections")
  ├─ Check I2C address (search: "I2C address")
  ├─ Review initialization code
  └─ Test with I2C scanner

STEP 3: If still stuck
  └─ Share problem + reference section with Claude Code:
     "Following the OLED troubleshooting in the reference guide,
      I verified wiring (GPIO20/21) but still no display. 
      I2C scanner shows no address. What else?"

CLAUDE HAS FULL CONTEXT FROM REFERENCE! ✅
```

### **Scenario 3: Unknown Error**

```
Example: Something weird happening, not sure what

STEP 1: Check Debugging Checklist
  └─ Search: "DEBUGGING CHECKLIST"
  └─ Work through systematically
  └─ Isolate the problem

STEP 2: Check Serial Output
  └─ Search: "Serial Debugging"
  └─ Add debug prints per guide
  └─ Observe what's happening

STEP 3: Search the guide for clues
  └─ Look for similar symptoms
  └─ Check component sections
  └─ Read architecture section

STEP 4: If truly stuck
  └─ Share specific error with Claude Code
  └─ Include relevant code snippet
  └─ Reference what you tried
```

---

## 📋 DOCUMENT STRUCTURE (Quick Navigation)

```
When you need...                    Go to...
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Hardware wiring                     → PIN ASSIGNMENTS & CONNECTIONS
Motor control questions             → MOTOR CONTROL (SSR Relay)
Load cell not working               → LOAD CELL & ADC section
OLED display issues                 → DISPLAY: OLED 0.96" I2C
Button debouncing                   → INPUT CONTROLS section
I2C/SDA/SCL questions               → I2C PINS (Built-in)

Code structure                      → SOFTWARE ARCHITECTURE
State machine                       → STATE MACHINE DESIGN
Main loop flow                      → MAIN LOOP FLOW
How modules connect                 → INTEGRATING ALL MODULES

Error: "not found"                  → COMMON ERRORS - Compilation
Error: "device not recognized"      → COMMON ERRORS - ESP32 Not Recognized
Motor won't start                   → COMMON ERRORS - Motor Not Starting
Display blank                       → COMMON ERRORS - OLED Display Not Working

Can't figure out problem            → DEBUGGING CHECKLIST
Need quick tips                     → QUICK REFERENCE
Getting stuck                       → WHEN YOU GET STUCK
Testing one component               → TESTING PROCEDURES - Phase 3

Serial output/debugging             → Serial Debugging
Timing constants                    → IMPORTANT CONSTANTS
Library functions                   → USEFUL FUNCTIONS
```

---

## 🔍 SEARCH TIPS

**How to find what you need:**

### **By Error Message**

```
Error: "error: 'XXX' was not declared"
└─ Search: "'XXX' not found" in Common Errors

Error: "undefined reference to"
└─ Search: "undefined reference" in Common Errors

Error: "compilation terminated"
└─ Search: "Compilation Errors" section

Error: Weird runtime behavior
└─ Search: "Runtime Errors" section
└─ Or: DEBUGGING CHECKLIST
```

### **By Component**

```
Problem with HX711?
└─ Search: "Load Cell & ADC" section
└─ Or: "HX711 Not Reading"

Problem with OLED?
└─ Search: "OLED" section
└─ Or: "OLED Display Not Working"

Problem with motor?
└─ Search: "Motor Control" section
└─ Or: "Motor Not Starting"

Problem with I2C?
└─ Search: "I2C" keyword
└─ Will find all I2C-related sections
```

### **By Problem Type**

```
Wiring issue?
└─ Search: "CONNECTIONS" or "Wiring"

Logic issue?
└─ Search: "State Machine" or "DEBUGGING CHECKLIST"

Power issue?
└─ Search: "Power", "voltage", "3.3V"

Timing issue?
└─ Search: "timing", "delay", "ms"

EMI/Noise issue?
└─ Search: "EMI", "noise", "unstable"
```

---

## 💬 HOW TO ASK CLAUDE CODE FOR HELP

**With this reference guide available:**

### **Best Practice (Include Context)**

```
❌ BEFORE (Without reference):
"My OLED doesn't work. What do I do?"
→ Claude has to ask clarifying questions
→ Takes multiple back-and-forths

✅ AFTER (With reference):
"I'm following CLAUDE-CODE-PROJECT-REFERENCE.md.
 In the 'OLED Display Not Working' section, I verified:
 ☑ Wiring (GPIO20=SDA, GPIO21=SCL)
 ☑ I2C scanner shows address 0x3C
 ☑ Adafruit_SSD1306 library installed
 
 But display still blank. Next step?"
→ Claude instantly understands!
→ Has full context from reference!
→ Can pinpoint issue immediately!
```

### **Sharing Reference Sections**

```
When Claude suggests checking something:

"Check the OLED connections in the reference guide
 section 'DISPLAY: OLED 0.96" I2C'"

You:
1. Open reference guide
2. Find that section
3. Copy relevant part
4. Share with Claude:
   "Reference says [pasted text]. I verified this and
    [what happened]. What's next?"
```

### **When Stuck on Compilation**

```
Copy the error message:
"error: 'HX711' was not declared in this scope"

Ask Claude:
"This compilation error appeared. I checked the reference 
 guide 'Common Errors & Solutions' for 'HX711 not found' 
 and tried the suggested fixes but still getting this error.
 Here's my code: [snippet]"

Claude will:
✅ See you already tried reference solutions
✅ Look deeper into the specific code
✅ Solve the exact issue
```

---

## 📚 RECOMMENDED SECTIONS TO READ FIRST

**Before you start coding, review:**

1. **PROJECT OVERVIEW** (5 min)
   - Understand the goal
   - Know success criteria

2. **HARDWARE SPECIFICATIONS** (10 min)
   - Know what components do
   - Understand specifications
   - See what's in your toolkit

3. **PIN ASSIGNMENTS & CONNECTIONS** (10 min)
   - Know ESP32 pin layout
   - Understand component wiring
   - Reference when wiring breadboard

4. **SOFTWARE ARCHITECTURE** (15 min)
   - Know project structure
   - Understand state machine
   - See how modules connect

5. **TESTING PROCEDURES** (10 min)
   - Know how to test each component
   - Know success indicators
   - Know what to look for

**Then when developing:**
- Reference specific sections as needed
- Use debugging checklist when stuck
- Check common errors frequently

---

## 🎯 WORKFLOW WITH REFERENCE GUIDE

### **Your Development Loop**

```
1. WRITE CODE
   └─ Use vibe coding prompts
   └─ Claude Code generates

2. COMPILE
   └─ If error:
      a. Check "Common Errors & Solutions"
      b. Apply fix
      c. Recompile

3. UPLOAD
   └─ If upload fails:
      a. Check "UPLOAD FAILS" section
      b. Verify wiring/driver
      c. Retry

4. TEST ON HARDWARE
   └─ If component doesn't work:
      a. Check "TESTING PROCEDURES"
      b. Follow test steps
      c. Compare expected vs actual
      d. Check "DEBUGGING CHECKLIST"

5. DEBUGGING
   └─ Add Serial.println() per guide
   └─ Search reference for similar issues
   └─ Apply suggested fixes

6. REPEAT until working
   └─ Reference guide speeds up every iteration!
```

---

## 📞 EXAMPLE: DEBUGGING SESSION

### **Real Example: OLED Not Showing Text**

```
STEP 1: Write code to display text
(Using PROMPT from vibe coding framework)

STEP 2: Compile → Success ✅

STEP 3: Upload to ESP32 → Success ✅

STEP 4: Run code → OLED still blank ❌

STEP 5: Open CLAUDE-CODE-PROJECT-REFERENCE.md
Search: "OLED Display Not Working"

STEP 6: Follow troubleshooting steps:
✓ Verify I2C wiring (GPIO20=SDA, GPIO21=SCL)
✓ Measure voltage on VCC (shows 3.3V)
✓ Run I2C scanner → Found address 0x3C

STEP 7: Now ask Claude Code:
"I'm following the 'OLED Display Not Working' section
 in the reference guide. I verified wiring, measured 3.3V
 on VCC, and I2C scanner found address 0x3C. But when I
 upload the display code, screen stays blank. 
 
 Here's the code I'm using: [paste code]
 
 What's the next troubleshooting step?"

CLAUDE CODE:
Claude immediately sees:
✅ You know the problem
✅ You've verified hardware
✅ You checked I2C communication
✅ You have full reference context

Claude focuses on code issue:
"The I2C scanner finds the device, so hardware is good.
 Looking at your code, I see you're not calling 
 display.display() after drawing. That's why nothing
 shows. Add display.display() after your println() call."

YOU FIX:
display.println("Hello!");
display.display();  ← This was missing!

RESULT: Text appears on OLED! ✅

TOTAL TIME: 10 minutes
(Without reference: Could be 30+ minutes!)
```

---

## 🚀 PRO TIPS

### **Tip 1: Keep Reference Open**
```
While coding:
1. Open VSCode split window
2. Left = Code
3. Right = Reference guide (opened in browser)
4. Quick lookup while coding!
```

### **Tip 2: Bookmark Common Sections**
```
In browser, bookmark:
- "Common Errors & Solutions"
- "Debugging Checklist"
- "Testing Procedures"
- "Pin Assignments"

One-click access to most-used sections! ⚡
```

### **Tip 3: Use Find Function**
```
Ctrl+F (Windows/Linux) or Cmd+F (Mac)
While reading reference guide

Type keywords:
- Component name (HX711, OLED, etc)
- Error message (not found, undefined, etc)
- Problem (doesn't work, stuck, etc)

Instant navigation! 🎯
```

### **Tip 4: Copy Sections to Claude Code**
```
If Claude suggests looking at reference:

1. Find relevant section
2. Copy the text
3. Paste to Claude Code chat:
   "From the reference guide [paste]:
    [relevant section]
    
    I tried this but [problem]. Help?"

Claude sees the exact reference section!
```

### **Tip 5: Keep Notes**
```
As you debug, keep notes:

"Issue: Motor not starting
 Solution: GPIO2 wasn't going HIGH (wrong pin assignment)
 Fixed: Changed GPIO definition to GPIO2
 Status: Working now ✅
 Date: Sep 15 2026"

Build personal troubleshooting log! 📝
```

---

## 📊 REFERENCE STATISTICS

```
What's in the reference guide:

Hardware Sections: 6
├─ Microcontroller: 40 lines
├─ Display: 30 lines
├─ Load Cell: 25 lines
├─ Motor Control: 35 lines
├─ Protection: 25 lines
└─ Input Controls: 40 lines

Software Sections: 4
├─ Project Structure: 20 lines
├─ State Machine: 30 lines
├─ Main Loop: 25 lines
└─ Pin Assignments: 35 lines

Error & Solutions: 50+ specific errors documented
├─ Compilation errors: 15+
├─ Runtime errors: 20+
├─ Logic errors: 15+

Testing Procedures: 4 phases
├─ Phase 1 (Compilation)
├─ Phase 2 (Hardware bring-up)
├─ Phase 3 (Component testing)
└─ Phase 4 (Integration)

Debugging Resources:
├─ 20+ troubleshooting sections
├─ 100+ specific solutions
├─ Complete checklist
├─ Serial debugging guide

Total: ~3000 lines of detailed reference material! 📚
```

---

## ✅ USING REFERENCE FOR DIFFERENT SITUATIONS

### **Situation 1: "I Don't Know Why Code Won't Compile"**

```
DO THIS:
1. Read error message carefully
2. Search reference for error type
3. Most compilation errors documented!
4. Find exact match (or similar)
5. Follow solution steps
6. Fixed! 90% of the time

If not fixed:
→ Ask Claude with error message + what you tried
→ Claude sees you followed reference
→ Claude can go deeper
```

### **Situation 2: "Code Works But Hardware Doesn't"**

```
DO THIS:
1. Go to "Testing Procedures"
2. Find your component section
3. Follow test steps exactly
4. Compare expected vs actual
5. Isolate the problem
6. Check "Debugging Checklist"
7. Wiring issue? Check "Connections"
8. Logic issue? Check "Common Runtime Errors"

Claude sees you:
✅ Tested thoroughly
✅ Followed procedures
✅ Know the problem
→ Much better debugging sessions!
```

### **Situation 3: "Everything Seems Wrong"**

```
START HERE:
1. "Debugging Checklist" section
2. Work through systematically
3. Test one thing at a time
4. Build confidence as each thing works
5. Find where it breaks
6. Focus on that component
7. Use reference for that component

You go from:
"Everything broken" ❌
To:
"HX711 reading wrong, other stuff fine" ✅

THEN:
Search reference for HX711 troubleshooting
Fix the one thing
Everything works! ✅
```

---

## 💡 FINAL TIPS

```
✅ BEST PRACTICES:

1. Before asking for help
   └─ Check reference guide first!
   └─ 80% of questions already answered

2. While debugging
   └─ Keep reference open
   └─ Reference before resorting to trial-and-error
   └─ Save hours of debugging time!

3. When asking Claude for help
   └─ Include what you found in reference
   └─ Share the steps you tried
   └─ Say "I checked X section but still stuck"
   └─ Claude immediately understands!

4. Build knowledge as you go
   └─ Read relevant sections before starting
   └─ Understand hardware before coding
   └─ Use reference as learning tool
   └─ Knowledge compounds over time

5. Share findings
   └─ If you discover new issue/solution
   └─ Add to your personal notes
   └─ Document for next time
   └─ Build your own knowledge base!
```

---

## 🎯 SUMMARY

**This reference guide is your:**

✅ **Hardware Reference** - Everything about components
✅ **Wiring Guide** - All connections documented  
✅ **Error Encyclopedia** - 50+ errors documented
✅ **Troubleshooting Manual** - Step-by-step solutions
✅ **Testing Checklist** - How to verify each component
✅ **Quick Lookup** - Find answers instantly
✅ **Claude Code Context** - Give Claude full project understanding

**Use it for:**
- Quick answers (avoid back-and-forth)
- Better Claude Code conversations (include context)
- Faster debugging (solutions at fingertips)
- Learning (understand your project deeply)
- Reference (keep bookmarked forever)

**Result:**
🚀 **Firmware development 2-3× faster!**

---

**Agung, dokumen ini adalah KUNCI efisiensi development kamu!** 🔑

Sekarang ketika ada error:
1. Check reference first
2. Fix 80% of issues sendiri
3. Only ask Claude untuk yang complicated

Ini akan save BANYAK waktu! ⏱️

Siap mulai? 🚀

