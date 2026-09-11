# CLAUDE CODE QUICK START - VIBE CODING GUIDE

**Purpose:** Setup & use Claude Code extension for smart grinder firmware development  
**Status:** Ready to go right now!  
**Platform:** VSCode (Windows, Mac, Linux)  

---

## 🚀 INSTALLATION (5 minutes)

### **Step 1: Install Claude Code Extension**

```
1. Open VSCode
2. Go to Extensions (Ctrl+Shift+X or Cmd+Shift+X)
3. Search for "Claude Code"
4. Click "Install" (by Anthropic)
5. Wait for installation (1-2 minutes)
6. Restart VSCode
```

### **Step 2: Authenticate**

```
1. Click Claude Code icon in left sidebar (looks like //)
2. Click "Sign In"
3. Browser opens → Sign in with your Claude account
4. Confirm access permissions
5. Back in VSCode → Ready to use!
```

### **Step 3: Create PlatformIO Project**

```
1. VSCode terminal (Ctrl+`)
2. Type: pip install platformio --break-system-packages
   (or: pip install platformio, might work on your system)
3. Then: platformio init -b esp32-c3-devkitc-02

This creates:
- platformio.ini (board settings)
- src/ folder (your code goes here)
- include/ folder (header files)
- lib/ folder (libraries)
```

---

## 💬 HOW TO USE CLAUDE CODE

### **Basic Workflow:**

```
1. OPEN Claude Code sidepanel (button in left sidebar)
2. PASTE prompt from framework document
3. READ Claude's response carefully
4. REVIEW generated code
5. MODIFY if needed (Claude's code is suggestion, not law!)
6. SAVE to your project
7. TEST compilation (PlatformIO toolbar at bottom)
```

### **Visual Guide:**

```
VSCode Interface:

┌─────────────────────────────────────────────────┐
│ File  Edit  View  Terminal  Help                 │
├─────┬───────────────────────────────┬───────────┤
│     │                               │           │
│ Exp │  main.cpp                     │ Claude ◄──← Claude Code Panel (Right side)
│ lor │  (Your code here)             │ Code  │   │ Copy prompt here ↓
│     │  ← Type or paste code here    │       │   │ Read response ↑
│     │                               │       │   │
│ Git │                               │       │   │
│ Ser │                               │       │   │
│ ver │                               │       │   │
│     │                               │       │   │
└─────┴───────────────────────────────┴───────────┘
```

---

## 📋 STEP-BY-STEP: YOUR FIRST PROMPT

### **Example: Generate HX711 Driver**

**Step 1: Open Claude Code**
```
1. In VSCode, click Claude Code icon (left sidebar)
2. Panel opens on right side
3. You see text input field at bottom that says "Message Claude"
```

**Step 2: Copy Prompt**
```
1. Open VIBE-CODING-PROMPT-FRAMEWORK.md (you have this file)
2. Find "PROMPT 1.2: HX711 ADC Driver Module"
3. Highlight entire prompt (starts with "═══" line)
4. Copy (Ctrl+C)
```

**Step 3: Paste into Claude Code**
```
1. Click in Claude Code message input field (bottom)
2. Paste (Ctrl+V)
3. Press Enter or click Send button
4. Wait 30-60 seconds for response
```

**Step 4: Read Response**
```
Claude will generate:
✅ hx711.h (header file)
✅ hx711.cpp (implementation)
✅ Example code
✅ Comments explaining

Read through carefully!
```

**Step 5: Create Files**
```
1. Right-click in VSCode explorer (left side)
2. Create new files:
   - include/hx711.h
   - src/hx711.cpp
3. Copy Claude's code into each file
4. Save (Ctrl+S)
```

**Step 6: Test Compilation**
```
1. Bottom taskbar in VSCode
2. Click PlatformIO icon
3. Click "Build" button
4. Wait for compilation
5. Should see: "✓ Succeeded" (green)
6. If error: Read error, ask Claude to fix it
```

---

## 🔄 COMMON WORKFLOW (For Each Component)

```
STEP 1 - PLANNING (5 min)
  └─ Read prompt in framework
  └─ Understand what component does
  └─ Note hardware connections

STEP 2 - GENERATION (2 min)
  └─ Copy prompt
  └─ Paste into Claude Code
  └─ Wait for response

STEP 3 - REVIEW (5 min)
  └─ Read generated code
  └─ Check it makes sense
  └─ Ask Claude questions if confused

STEP 4 - CREATION (3 min)
  └─ Create .h and .cpp files
  └─ Copy Claude's code
  └─ Save in correct folders

STEP 5 - COMPILATION (2 min)
  └─ Build project
  └─ Fix any errors
  └─ Verify compilation success

STEP 6 - DOCUMENTATION (2 min)
  └─ Review comments
  └─ Add your own notes if needed
  └─ Commit to git (if using version control)

TOTAL PER COMPONENT: ~20 minutes!
```

---

## ❓ WHEN SOMETHING GOES WRONG

### **Compilation Error?**

```
ERROR: "error: 'HX711' was not declared in this scope"

SOLUTION:
1. In Claude Code, ask:
   "Why is HX711 not declared? How do I fix?"
2. Usually missing #include in main.cpp
3. Claude will tell you what to add
```

### **Code Won't Compile?**

```
TROUBLESHOOTING:
1. Check error message (bottom of VSCode)
2. Copy exact error into Claude Code:
   "This code won't compile. Error: [exact error here]"
3. Claude will identify and fix
4. Apply fix, recompile
```

### **Code Compiles But Doesn't Make Sense?**

```
UNDERSTANDING:
1. Ask Claude: "Explain this function: [function name]"
2. Ask Claude: "Why do we need this timing?"
3. Ask Claude: "What would happen if we removed this line?"
4. Learning through questions = better understanding
```

---

## 💡 PRO TIPS

### **Tip 1: Ask Follow-Up Questions**

```
First message:
"Create HX711 driver with [requirements]"

Claude generates code...

Follow-up message:
"Can you optimize this for speed?"
"Can you add error handling?"
"Can you explain the timing diagram?"

Claude LOVES follow-ups! Use them!
```

### **Tip 2: Test One Component at a Time**

```
DON'T:
❌ Generate all code at once
❌ Integrate everything
❌ Test last

DO:
✅ Generate HX711 → Test it compiles
✅ Generate buttons → Test it compiles
✅ Generate motor → Test it compiles
✅ THEN integrate

This catches errors early!
```

### **Tip 3: Keep Track of Your Code**

```
Organize files:
src/
├─ main.cpp (main loop)
├─ hx711.cpp (load cell driver)
├─ buttons.cpp (button handler)
├─ motor.cpp (SSR relay control)
└─ state_machine.cpp (main logic)

include/
├─ hx711.h
├─ buttons.h
├─ motor.h
└─ state_machine.h

This makes it easy to find things!
```

### **Tip 4: Use Version Control (Git)**

```
Optional but recommended:

git init
git add .
git commit -m "HX711 driver working"
git add .
git commit -m "Button handler working"
...etc...

This saves progress. If something breaks,
you can go back to last working version!
```

---

## 📊 REALISTIC TIMELINE

```
With Claude Code and vibe coding:

WITHOUT Claude Code (manual coding):
- HX711 driver: 2-3 hours
- Buttons: 1-2 hours
- Motor control: 2-3 hours
- State machine: 4-5 hours
- Integration: 2-3 hours
TOTAL: 11-16 hours 😫

WITH Claude Code (vibe coding):
- HX711 driver: 20 min (Claude generates 80%, you review 20%)
- Buttons: 20 min
- Motor control: 20 min
- State machine: 30 min
- Integration: 30 min
TOTAL: ~2 hours 🚀

TIME SAVED: ~10-14 hours! ⏱️
```

---

## 🎯 YOUR DEVELOPMENT PLAN

### **This Week (Before ESP32 Arrives)**

```
TUESDAY:
├─ Install Claude Code
├─ Create PlatformIO project
└─ Test it builds empty project

WEDNESDAY-THURSDAY (Week 1 Foundation):
├─ Generate project structure (PROMPT 1.1)
├─ Generate HX711 driver (PROMPT 1.2)
├─ Generate button handler (PROMPT 1.3)
└─ Test each compiles

FRIDAY-SATURDAY (Week 2 Logic):
├─ Generate state machine (PROMPT 2.1)
├─ Generate motor control (PROMPT 2.2)
└─ Test integration

SUNDAY (Week 3 Final):
├─ Full system integration (PROMPT 3.1)
├─ Final compilation test
└─ Documentation complete

RESULT: Firmware READY!
```

---

## ✅ CHECKLIST: Before Starting

```
☐ VSCode installed
☐ Claude Code extension installed
☐ Signed in to Claude
☐ PlatformIO project created
☐ Tested empty project builds
☐ VIBE-CODING-PROMPT-FRAMEWORK.md saved
☐ Coffee ready (optional but recommended!) ☕
```

---

## 🚀 YOU'RE READY!

**Kamu sekarang punya semua yang butuh untuk start coding!**

Timeline:
- ✅ 3 weeks development = firmware ready
- ✅ ESP32 arrives = upload immediately
- ✅ Works first try (if done well!)

**Jangan tunggu hardware! Start coding SEKARANG!** 💪

---

## 📞 IF YOU GET STUCK

```
1. Check this guide (quick reference)
2. Check compilation error message
3. Ask Claude Code (in sidebar)
   - "Why won't this compile?"
   - "How do I fix this error?"
   - "What does this function do?"
4. Read the response
5. Apply fix
6. Try again

Claude Code is VERY helpful for debugging!
Don't be shy - ask questions!
```

---

**Ready to start vibe coding?** 🚀

Questions before you start? Chat with me anytime!

Next step: Install Claude Code + create project!

