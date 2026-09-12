# ☕ DIFLUID R2 BLE INTEGRATION - ESP32 IMPLEMENTATION GUIDE

**Source:** Based on Decenza project (fpantazis/f535893955bd8cf44e2c321ae0285685)  
**Date:** September 11, 2026  
**Status:** Ready to implement (protocol fully documented!)  

---

## 🎯 QUICK START: THE PROTOCOL

### **UUIDs (This is what we need!)**

```
Service UUID:        0x00FF → "000000FF-0000-1000-8000-00805F9B34FB"
Characteristic UUID: 0xAA01 → "0000AA01-0000-1000-8000-00805F9B34FB"

BLE Device name pattern: Contains "r2" (case-insensitive)

IMPORTANT: Different from DiFluid scale!
├─ Scale uses service: 0x00EE
├─ R2 uses service: 0x00FF
└─ Both use characteristic: 0xAA01
```

### **Packet Protocol (Binary)**

```
ALL PACKETS (Commands & Responses):
┌──────┬──────┬──────┬──────┬──────┬──────┬─────┬──────┐
│ 0xDF │ 0xDF │ Func │ Cmd  │ Len  │Data0 │...  │ Cksum│
└──────┴──────┴──────┴──────┴──────┴──────┴─────┴──────┘

CHECKSUM = (sum of all bytes before checksum) & 0xFF

Example: "Set temp unit to Celsius"
DF DF 01 00 01 00 C0
├─ 0xDF 0xDF: Header
├─ 0x01: Func (Device Settings)
├─ 0x00: Cmd
├─ 0x01: DataLen (1 byte)
├─ 0x00: Data[0] (Celsius mode)
└─ 0xC0: Checksum = (0xDF + 0xDF + 0x01 + 0x00 + 0x01 + 0x00) & 0xFF
```

### **Commands to Send**

```
┌─────────────────────────────────────────────────┐
│ COMMAND 1: Set Temperature Unit to Celsius      │
├─────────────────────────────────────────────────┤
│ Hex: DF DF 01 00 01 00 C0                      │
│ Func: 0x01 (Device Settings)                   │
│ Cmd: 0x00                                       │
│ Data: 0x00 (Celsius)                           │
│ When: After connect (100ms delay)              │
│ Purpose: Ensure temp readings in Celsius       │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ COMMAND 2: Trigger Single Test                  │
├─────────────────────────────────────────────────┤
│ Hex: DF DF 03 00 00 C1                         │
│ Func: 0x03 (Device Action)                     │
│ Cmd: 0x00 (Single Test)                        │
│ Data: (empty)                                   │
│ When: User requests measurement                │
│ Purpose: Start measurement cycle               │
└─────────────────────────────────────────────────┘
```

### **Response Packets (BLE Notifications on 0xAA01)**

Device sends multiple packets after test, distinguished by package number:

```
┌──────────────────────────────────────────────────┐
│ PACKAGE 0: Status (Data[0] == 0x00)              │
├──────────────────────────────────────────────────┤
│ Data[1]: Status code                             │
│                                                  │
│ Status codes (examples):                         │
│  0  = Test finished (COMPLETE!)                  │
│  4  = Average test started                       │
│  5  = Average test ongoing                       │
│  6  = Average test finished                      │
│  9  = Loop finished                              │
│  11 = Test started (measurement in progress)     │
│  3  = No liquid detected (ERROR)                 │
│  4  = Beyond range (ERROR)                       │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│ PACKAGE 1: Temperature (Data[0] == 0x01)         │
├──────────────────────────────────────────────────┤
│ Data[1..2]: Prism temp × 10 (big-endian uint16) │
│ Data[3..4]: Tank temp × 10 (big-endian uint16)  │
│ Data[5]: Unit (0=C, 1=F)                         │
│                                                  │
│ Formula:                                         │
│ temp_celsius = (prism_temp + tank_temp) / 20.0  │
│                                                  │
│ Example: {0x01, 0x00, 0xDC, 0x00, 0xDC, 0x00}   │
│          Prism: 0x00DC = 220 → 22.0°C           │
│          Tank:  0x00DC = 220 → 22.0°C           │
│          Result: (22 + 22) / 20 = 2.2°C (WRONG) │
│ Corrected: average = (220 + 220) / 20.0 = 22°C │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│ PACKAGE 2: TDS Result ← THIS IS WHAT WE NEED!    │
├──────────────────────────────────────────────────┤
│ Data[0] == 0x02                                  │
│ Data[1..2]: TDS × 100 (big-endian uint16)        │
│ Data[3..6]: Refractive index × 100000 (optional)│
│                                                  │
│ Formula:                                         │
│ tds_percent = ((Data[1] << 8) | Data[2]) / 100.0│
│                                                  │
│ Example: {0x02, 0x00, 0x4F, 0x00, 0x02, 0x09, 0x3D}
│ TDS: 0x004F = 79 → 0.79%                        │
│                                                  │
│ Another example: {0x02, 0x00, 0x59, ...}        │
│ TDS: 0x0059 = 89 → 0.89%                        │
│                                                  │
│ Or: {0x02, 0x01, 0x35, ...}                     │
│ TDS: 0x0135 = 309 → 3.09%                       │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│ PACKAGE 3: Average TDS (Data[0] == 0x03)         │
├──────────────────────────────────────────────────┤
│ (For average tests only, similar to Package 2)   │
│ Data[1..2]: Average TDS × 100                    │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│ ERROR PACKET (Func=0x03, Cmd=0xFE)               │
├──────────────────────────────────────────────────┤
│ Data[0] == 0x02 (error class general)            │
│ Data[1]: Error code                              │
│  3 = "No liquid detected"                        │
│  4 = "Reading beyond range"                      │
│  etc.                                            │
└──────────────────────────────────────────────────┘
```

---

## 💻 ESP32 IMPLEMENTATION (C/C++)

### **Step 1: BLE Setup (Main)**

```cpp
// In your main setup:
#include <BLEDevice.h>
#include <BLEClient.h>

// UUIDs
#define DIFLUID_R2_SERVICE      "000000FF-0000-1000-8000-00805F9B34FB"
#define DIFLUID_R2_CHARACTERISTIC "0000AA01-0000-1000-8000-00805F9B34FB"

// BLE variables
BLEClient* pClient = nullptr;
BLERemoteCharacteristic* pRemoteCharacteristic = nullptr;
bool r2_connected = false;
double last_tds = 0.0;
double last_temperature = 0.0;

void setup() {
    // Initialize BLE
    BLEDevice::init("ESP32 Grinder");  // Our device name
    pClient = BLEDevice::createClient();
    pClient->setClientCallbacks(new MyClientCallback());
    
    // Start scanning for R2 device
    startScanForR2();
}
```

### **Step 2: Device Discovery (Scanning)**

```cpp
class MyAdvertisedDeviceCallbacks : public BLEAdvertisedDeviceCallbacks {
    void onResult(BLEAdvertisedDevice advertisedDevice) override {
        String name = advertisedDevice.getName().c_str();
        
        // Check if this is Difluid R2
        if (name.toLowerCase().indexOf("r2") >= 0) {
            Serial.println("Found Difluid R2: " + name);
            
            // Connect immediately
            pClient->connect(&advertisedDevice);
        }
    }
};

void startScanForR2() {
    BLEScan* pBLEScan = BLEDevice::getScan();
    pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks());
    pBLEScan->setInterval(1349);
    pBLEScan->setWindow(449);
    pBLEScan->setActiveScan(true);
    pBLEScan->start(30, false);
}
```

### **Step 3: Connection & Service Discovery**

```cpp
class MyClientCallback : public BLEClientCallbacks {
    void onConnect(BLEClient* pclient) override {
        Serial.println("Connected to R2!");
        
        // Discover services
        std::vector<BLEUUID>* pUUIDs = pclient->getServices();
        for (auto& uuid : *pUUIDs) {
            if (uuid.toString() == "000000ff-0000-1000-8000-00805f9b34fb") {
                Serial.println("Found R2 service!");
                discoverCharacteristics(pclient);
                return;
            }
        }
    }
    
    void onDisconnect(BLEClient* pclient) override {
        Serial.println("Disconnected from R2");
        r2_connected = false;
    }
};

void discoverCharacteristics(BLEClient* pclient) {
    BLERemoteService* pRemoteService = 
        pclient->getService(BLEUUID(DIFLUID_R2_SERVICE));
    
    if (pRemoteService == nullptr) {
        Serial.println("Service not found!");
        return;
    }
    
    // Get the characteristic
    pRemoteCharacteristic = 
        pRemoteService->getCharacteristic(BLEUUID(DIFLUID_R2_CHARACTERISTIC));
    
    if (pRemoteCharacteristic == nullptr) {
        Serial.println("Characteristic not found!");
        return;
    }
    
    // Register for notifications
    if (pRemoteCharacteristic->canNotify()) {
        pRemoteCharacteristic->registerForNotify(notifyCallback);
        Serial.println("Registered for notifications!");
        
        // Wait 100ms, then init
        delay(100);
        initializeR2();
    }
}
```

### **Step 4: Initialize R2 (Set Celsius)**

```cpp
void initializeR2() {
    // Command: Set temp unit to Celsius
    // Hex: DF DF 01 00 01 00 C0
    uint8_t cmd[] = {0xDF, 0xDF, 0x01, 0x00, 0x01, 0x00, 0xC0};
    
    // Send command
    pRemoteCharacteristic->writeValue(cmd, sizeof(cmd), false);
    Serial.println("Sent: Set temperature unit to Celsius");
    
    r2_connected = true;
}
```

### **Step 5: Request Measurement**

```cpp
void requestMeasurement() {
    if (!r2_connected || !pRemoteCharacteristic) {
        Serial.println("R2 not connected!");
        return;
    }
    
    // Command: Single Test
    // Hex: DF DF 03 00 00 C1
    uint8_t cmd[] = {0xDF, 0xDF, 0x03, 0x00, 0x00, 0xC1};
    
    pRemoteCharacteristic->writeValue(cmd, sizeof(cmd), false);
    Serial.println("Sent: Request single test measurement");
}
```

### **Step 6: Parse Notifications (THE IMPORTANT PART!)**

```cpp
static void notifyCallback(BLERemoteCharacteristic* pBLERemoteCharacteristic,
                          uint8_t* pData, size_t length, bool isNotify) {
    
    Serial.print("Received packet, length: ");
    Serial.println(length);
    
    // Validate minimum length
    if (length < 6) {
        Serial.println("Packet too short!");
        return;
    }
    
    // Validate header
    if (pData[0] != 0xDF || pData[1] != 0xDF) {
        Serial.println("Invalid header!");
        return;
    }
    
    // Validate checksum
    uint8_t checksum_expected = 0;
    for (int i = 0; i < length - 1; i++) {
        checksum_expected += pData[i];
    }
    checksum_expected &= 0xFF;
    
    uint8_t checksum_received = pData[length - 1];
    if (checksum_expected != checksum_received) {
        Serial.print("Checksum mismatch: expected ");
        Serial.print(checksum_expected, HEX);
        Serial.print(", got ");
        Serial.println(checksum_received, HEX);
        return;
    }
    
    // Extract packet info
    uint8_t func = pData[2];
    uint8_t cmd = pData[3];
    uint8_t dataLen = pData[4];
    
    // Payload starts at byte 5
    uint8_t* payload = pData + 5;
    
    // Validate payload length
    if (length < 5 + dataLen + 1) {
        Serial.println("Payload length mismatch!");
        return;
    }
    
    // Check if this is a test result (func=0x03, cmd=0x00 or 0x01)
    if (func == 0x03 && (cmd == 0x00 || cmd == 0x01)) {
        if (dataLen < 1) return;
        
        uint8_t pkgNum = payload[0];
        
        // Parse by package number
        if (pkgNum == 0x00) {
            // PACKAGE 0: Status
            if (dataLen < 2) return;
            uint8_t status = payload[1];
            
            Serial.print("Status: ");
            Serial.println(status);
            
            // Status code meanings:
            if (status == 0 || status == 6 || status == 9) {
                Serial.println("Test FINISHED!");
            } else if (status == 11 || status == 4 || status == 5) {
                Serial.println("Test ONGOING...");
            } else if (status == 3) {
                Serial.println("ERROR: No liquid detected!");
            } else if (status == 4) {
                Serial.println("ERROR: Reading beyond range!");
            }
        }
        else if (pkgNum == 0x01) {
            // PACKAGE 1: Temperature
            if (dataLen < 6) return;
            
            // Read temperatures (big-endian)
            uint16_t prism_raw = (payload[1] << 8) | payload[2];
            uint16_t tank_raw = (payload[3] << 8) | payload[4];
            uint8_t unit = payload[5];
            
            // Convert from x10 format
            double prism_temp = prism_raw / 10.0;
            double tank_temp = tank_raw / 10.0;
            
            // Average
            last_temperature = (prism_temp + tank_temp) / 2.0;
            
            Serial.print("Temperature: ");
            Serial.print(last_temperature);
            Serial.print("°C (prism: ");
            Serial.print(prism_temp);
            Serial.print(", tank: ");
            Serial.print(tank_temp);
            Serial.println(")");
        }
        else if (pkgNum == 0x02) {
            // PACKAGE 2: TDS RESULT ← THIS IS WHAT WE WANT!
            if (dataLen < 3) return;
            
            // Read TDS (big-endian uint16)
            uint16_t tds_raw = (payload[1] << 8) | payload[2];
            
            // Convert from x100 format
            last_tds = tds_raw / 100.0;
            
            Serial.print("🎯 TDS RECEIVED: ");
            Serial.print(last_tds, 2);
            Serial.println("%");
            
            // ← NOW IMPLEMENT YOUR ALGORITHM HERE ← //
            processEspressoShot(last_tds);
        }
    }
    else if (func == 0x03 && cmd == 0xFE) {
        // Error packet
        Serial.println("Device error received!");
        if (dataLen >= 2 && payload[0] == 0x02) {
            uint8_t errCode = payload[1];
            if (errCode == 3) {
                Serial.println("Error: No liquid detected");
            } else if (errCode == 4) {
                Serial.println("Error: Reading beyond range");
            }
        }
    }
}
```

### **Step 7: Espresso Optimization Algorithm**

```cpp
void processEspressoShot(double tds_received) {
    // Collect data from this shot
    float dose = getCurrentDose();        // From load cell
    float yield = getCurrentYield();      // From load cell
    uint16_t time_seconds = getShotTime(); // From timer
    double tds = tds_received;             // From R2
    
    // Calculate metrics
    float ratio = (yield / dose);
    
    // Display on OLED
    char buffer[256];
    
    // Check each parameter
    Serial.println("\n=== SHOT ANALYSIS ===");
    Serial.print("Dose: "); Serial.print(dose); Serial.println("g");
    Serial.print("Yield: "); Serial.print(yield); Serial.println("g");
    Serial.print("Ratio: "); Serial.print(ratio, 2); Serial.println("x");
    Serial.print("Time: "); Serial.print(time_seconds); Serial.println("s");
    Serial.print("TDS: "); Serial.print(tds, 2); Serial.println("%");
    
    // Determine recommendation
    String recommendation = "";
    uint8_t quality_score = 5;
    
    // TDS CHECK
    if (tds < 1.3) {
        recommendation = "UNDER-EXTRACTED\nGrind FINER";
        quality_score = 3;
    }
    else if (tds > 1.5) {
        recommendation = "OVER-EXTRACTED\nGrind COARSER";
        quality_score = 3;
    }
    else if (tds >= 1.3 && tds <= 1.5) {
        recommendation = "TDS PERFECT";
        quality_score = 9;
    }
    
    // EXTRACTION RATIO CHECK
    if (ratio < 1.8) {
        recommendation = "LOW YIELD\nGrind FINER";
        quality_score = min(quality_score, 5);
    }
    else if (ratio > 2.2) {
        recommendation = "HIGH YIELD\nGrind COARSER";
        quality_score = min(quality_score, 5);
    }
    else if (ratio >= 1.8 && ratio <= 2.2) {
        recommendation += "\nRATIO PERFECT";
        quality_score = min(quality_score + 1, 10);
    }
    
    // TIME CHECK
    if (time_seconds < 25) {
        recommendation = "EXTRACTION FAST\nGrind FINER";
        quality_score = 4;
    }
    else if (time_seconds > 30) {
        recommendation = "EXTRACTION SLOW\nGrind COARSER";
        quality_score = 4;
    }
    
    // FINAL DISPLAY
    display.clearDisplay();
    display.setTextSize(1);
    display.setCursor(0, 0);
    
    display.println("=== SHOT COMPLETE ===");
    snprintf(buffer, sizeof(buffer), "Dose: %.1fg -> %.1fg", dose, yield);
    display.println(buffer);
    snprintf(buffer, sizeof(buffer), "Time: %ds, TDS: %.2f%%", time_seconds, tds);
    display.println(buffer);
    
    display.println("\nRECOMMENDATION:");
    display.println(recommendation.c_str());
    
    snprintf(buffer, sizeof(buffer), "Quality: %d/10", quality_score);
    display.println(buffer);
    
    display.display();
    
    // Save to EEPROM
    saveShotData(dose, yield, time_seconds, tds, quality_score, recommendation);
}
```

---

## 📊 IMPLEMENTATION CHECKLIST

```
PHASE 2A: R2 Connection (Weeks 4-5)
☐ Test BLE scanning for R2 device
☐ Connect to R2
☐ Discover services & characteristics
☐ Initialize R2 (set Celsius)
☐ Serial output shows "Connected!"

PHASE 2B: Data Reception (Weeks 5-6)
☐ Register for notifications
☐ Receive and parse packets
☐ Extract TDS from Package 2
☐ Extract temperature from Package 1
☐ Handle errors (no liquid, beyond range)
☐ Serial shows TDS readings

PHASE 2C: Integration (Weeks 6-8)
☐ Connect R2 TDS to recommendation algorithm
☐ Display recommendations on OLED
☐ Save shot data with TDS
☐ Calculate trending (5-shot average)
☐ Test full workflow: grind → weigh → pull → measure → recommend

FINAL: Testing & Refinement (Week 8+)
☐ Test with different beans
☐ Verify TDS accuracy
☐ Calibrate recommendation thresholds
☐ Test error cases
☐ Production ready!
```

---

## 🔧 DEBUGGING TIPS

```
If R2 won't connect:
1. Check BLE name contains "r2"
2. Power cycle R2 device
3. Check ESP32 can find other BLE devices
4. Increase scan time (scanTime > 30)

If no TDS readings:
1. Verify UUID matches: 0x00FF service
2. Check notifications enabled
3. Print all received packets (hex dump)
4. Verify checksum calculation
5. Make sure Package 2 is being received

If TDS value looks wrong:
1. Check byte order (big-endian)
2. Verify division by 100
3. Confirm package number is 0x02
4. Check data length >= 3

Serial debugging:
- Print every packet received
- Print checksum validation
- Print package number and extracted value
- Compare with Decenza implementation reference
```

---

## 🎯 EXACT TIMING

```
After connect:
1. 100ms: Enable notifications
2. Then: Send temp unit command
3. Then: Waiting for user measurement request

During measurement:
1. User places sample in R2
2. ~2-3 seconds: R2 processes sample
3. Notification arrives: Package 0 (status)
4. Notification arrives: Package 1 (temperature)
5. Notification arrives: Package 2 (TDS) ← THIS IS IT!
6. Update algorithm & display recommendation
```

---

## 📚 REFERENCE IMPLEMENTATION

**Base:** https://gist.github.com/fpantazis/f535893955bd8cf44e2c321ae0285685

Key files:
- `difluidr2.h` - Header (67 lines)
- `difluidr2.cpp` - Implementation (261 lines)
- `DIFLUID_R2_SPEC.md` - Protocol details
- `design.md` - Architecture decisions

This is from PRODUCTION code (Decenza project). Use as reference!

---

## ✅ SUCCESS CHECKLIST

When this is working:
```
✅ ESP32 scans and finds R2 device
✅ Establishes BLE connection
✅ Initializes R2 (celsius command)
✅ Receives notifications
✅ Parses packets correctly
✅ Extracts TDS value from Package 2
✅ TDS value displayed on OLED
✅ Recommendation generated
✅ Shot data saved with TDS
✅ Complete integration working!

THEN: Professional espresso AI is READY! 🎉☕
```

---

## 🚀 IMPLEMENTATION SEQUENCE

```
Week 4-5:
1. Test BLE discovery (find R2)
2. Test connection
3. Test notification subscription

Week 5-6:
4. Implement packet parsing
5. Extract TDS values
6. Display on OLED

Week 6-8:
7. Implement recommendation algorithm
8. Save shot data
9. Test full workflow
10. Deploy Phase 2!
```

---

**Agung, now you have EXACT PROTOCOL from working implementation!** 🎯

Gist ini memberikan:
✅ Exact UUIDs
✅ Exact packet format
✅ Exact parsing code
✅ Exact timing
✅ Exact error handling
✅ Working reference implementation

**You can literally adapt their C++ code untuk ESP32!** 💪

Ready to implement Phase 2? 🚀

