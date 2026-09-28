// Standalone I2C display test - zero application code on purpose.
// Only built by `pio run -e i2c_display_test`, see platformio.ini.
//
// 1. Scans the I2C bus and prints every address that ACKs:
//    0x3C/0x3D = OLED (SSD1306 / SH1106), 0x27/0x3F = PCF8574 backpack on
//    a 16x2/20x4 character LCD.
// 2. If an OLED address answered, draws a frame + label with the SSD1306
//    driver, then the SH1106 driver - whichever one shows a clean image is
//    the module's real controller (the wrong one usually shows garbage or
//    a 2-pixel-shifted image, not nothing).

#include <Arduino.h>
#include <Wire.h>
#include <U8g2lib.h>

#define I2C_SCL 8
#define I2C_SDA 10

U8G2_SSD1306_128X64_NONAME_F_HW_I2C oled_ssd1306(U8G2_R0, U8X8_PIN_NONE);
U8G2_SH1106_128X64_NONAME_F_HW_I2C oled_sh1106(U8G2_R0, U8X8_PIN_NONE);

static uint8_t g_oled_addr = 0;

static void scanBus() {
  Serial.println("=== I2C scan ===");
  uint8_t found = 0;
  g_oled_addr = 0;
  for (uint8_t addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    if (Wire.endTransmission() == 0) {
      found++;
      const char *hint = "";
      if (addr == 0x3C || addr == 0x3D) { hint = " (OLED SSD1306/SH1106)"; g_oled_addr = addr; }
      else if (addr == 0x27 || addr == 0x3F || (addr >= 0x20 && addr <= 0x27) || (addr >= 0x38 && addr <= 0x3F)) hint = " (PCF8574 char LCD backpack?)";
      Serial.printf("found 0x%02X%s\n", addr, hint);
    }
  }
  if (found == 0) Serial.println("nothing answered - check SCL/SDA swap, VCC, GND");
}

static void drawWith(U8G2 &oled, const char *label) {
  Serial.printf("=== drawing with %s at 0x%02X ===\n", label, g_oled_addr);
  oled.setI2CAddress(g_oled_addr * 2); // U8g2 wants the 8-bit (shifted) address
  oled.begin();
  oled.clearBuffer();
  oled.drawFrame(0, 0, 128, 64);
  oled.setFont(u8g2_font_ncenB14_tr);
  oled.drawStr(8, 28, label);
  oled.setFont(u8g2_font_6x10_tr);
  oled.drawStr(8, 50, "smart grinder");
  oled.sendBuffer();
  delay(4000);
}

// Both an ST7789 and this I2C module showed nothing on SCL/SCK=8,
// SDA=10 - the common factor is the pins/wires, not the panels. So at
// boot, try every SDA/SCL pair among the free GPIOs and report any pair
// where something ACKs; that reveals a jumper sitting on a different
// header pin than assumed. 9 (strapping) and 12-17 (flash) excluded.
static const uint8_t CANDIDATE_PINS[] = {3, 4, 5, 6, 7, 8, 10, 20, 21};

static void sweepPinPairs() {
  Serial.println("=== pin-pair sweep ===");
  uint8_t hits = 0;
  for (uint8_t a = 0; a < sizeof(CANDIDATE_PINS); a++) {
    for (uint8_t b = 0; b < sizeof(CANDIDATE_PINS); b++) {
      if (a == b) continue;
      uint8_t sda = CANDIDATE_PINS[a], scl = CANDIDATE_PINS[b];
      Wire.begin(sda, scl, 100000);
      for (uint8_t addr = 1; addr < 127; addr++) {
        Wire.beginTransmission(addr);
        if (Wire.endTransmission() == 0) {
          Serial.printf("HIT: SDA=GPIO%d SCL=GPIO%d addr=0x%02X\n", sda, scl, addr);
          hits++;
        }
      }
      Wire.end();
    }
  }
  Serial.printf("sweep done, %d hit(s)\n", hits);
}

void setup() {
  Serial.begin(115200);
  delay(1500);
  Serial.println("i2c_display_test: start");
  sweepPinPairs();
  Wire.begin(I2C_SDA, I2C_SCL);
}

void loop() {
  scanBus();
  if (g_oled_addr != 0) {
    drawWith(oled_ssd1306, "SSD1306");
    drawWith(oled_sh1106, "SH1106");
  } else {
    delay(3000);
  }
}
