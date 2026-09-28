// Standalone LCD swap-test - zero application code on purpose.
// Only built by `pio run -e display_test`, see platformio.ini.
//
// Goal: settle whether the physical GMT130-V1.0 (ST7789 240x240) module is
// DOA. Wiring/pins already verified correct in the original investigation
// (docs/DEVELOPMENT-LOG.md) - this just drives the panel directly with no
// other firmware logic in the way.
//
// 2026-09-28: both earlier runs used Adafruit's default SPI_MODE0. CS-less
// 240x240 ST7789 modules like this one commonly only latch data in
// SPI_MODE3 (symptom when wrong: backlight on, screen stays black - exactly
// what was seen). So this now cycles through SPI_MODE3, MODE2, MODE0 in one
// flash, re-initialising the panel for each and printing the active mode,
// at a conservative 10MHz for jumper wires. Watch which mode (if any)
// shows the color sequence + "MODE n" label.
//
// Then a raw bit-bang phase (no Adafruit lib, no SPI peripheral): the
// ST7789 init sequence clocked out by hand on the same pins, slow, once
// with clock idle HIGH (mode-3-like) and once idle LOW (mode-0-like), each
// filling the whole panel a single solid color (idle HIGH = magenta,
// idle LOW = yellow). If even this stays black, software is ruled out.

#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>
#include <SPI.h>

#define TFT_SCLK 8
#define TFT_MOSI 10
#define TFT_DC   21
#define TFT_RST  20
#define TFT_CS   -1  // module has no CS pin

#define TFT_SPI_HZ 10000000

Adafruit_ST7789 tft(TFT_CS, TFT_DC, TFT_RST);

static const uint8_t SPI_MODES[] = {SPI_MODE3, SPI_MODE2, SPI_MODE0};
static const uint8_t SPI_MODE_LABELS[] = {3, 2, 0};

// ---- raw bit-bang phase ----

static bool g_idle_high = true;

static void bbByte(uint8_t b) {
  for (int8_t bit = 7; bit >= 0; bit--) {
    // ST7789 samples SDA on the SCK rising edge in both cases; only the
    // idle level between bytes differs.
    digitalWrite(TFT_SCLK, LOW);
    digitalWrite(TFT_MOSI, (b >> bit) & 1);
    delayMicroseconds(1);
    digitalWrite(TFT_SCLK, HIGH);
    delayMicroseconds(1);
  }
  digitalWrite(TFT_SCLK, g_idle_high ? HIGH : LOW);
}

static void bbCmd(uint8_t c) {
  digitalWrite(TFT_DC, LOW);
  bbByte(c);
  digitalWrite(TFT_DC, HIGH);
}

static void bbData(uint8_t d) {
  bbByte(d);
}

static void bitBangTest(bool idle_high, uint16_t color565, const char *color_name) {
  g_idle_high = idle_high;
  Serial.printf("=== BITBANG idle %s: init, fill %s ===\n", idle_high ? "HIGH" : "LOW", color_name);

  pinMode(TFT_SCLK, OUTPUT);
  pinMode(TFT_MOSI, OUTPUT);
  pinMode(TFT_DC, OUTPUT);
  pinMode(TFT_RST, OUTPUT);
  digitalWrite(TFT_SCLK, idle_high ? HIGH : LOW);
  digitalWrite(TFT_DC, HIGH);

  digitalWrite(TFT_RST, HIGH); delay(20);
  digitalWrite(TFT_RST, LOW);  delay(20);
  digitalWrite(TFT_RST, HIGH); delay(150);

  bbCmd(0x01); delay(150);                  // SWRESET
  bbCmd(0x11); delay(150);                  // SLPOUT
  bbCmd(0x3A); bbData(0x55); delay(10);     // COLMOD: 16-bit
  bbCmd(0x36); bbData(0x00);                // MADCTL
  bbCmd(0x21);                              // INVON (typical for these IPS panels)
  bbCmd(0x13); delay(10);                   // NORON
  bbCmd(0x29); delay(50);                   // DISPON

  bbCmd(0x2A); bbData(0); bbData(0); bbData(0); bbData(239); // CASET 0..239
  bbCmd(0x2B); bbData(0); bbData(0); bbData(0); bbData(239); // RASET 0..239
  bbCmd(0x2C);                                               // RAMWR
  for (uint32_t i = 0; i < 240UL * 240UL; i++) {
    bbData(color565 >> 8);
    bbData(color565 & 0xFF);
  }
  Serial.println("fill done");
  delay(4000);
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("display_test: start");
}

void loop() {
  SPI.begin(TFT_SCLK, -1 /* MISO unused */, TFT_MOSI, -1 /* SS unused, no CS wired */);
  for (uint8_t i = 0; i < sizeof(SPI_MODES); i++) {
    Serial.printf("=== SPI_MODE%d: init ===\n", SPI_MODE_LABELS[i]);
    tft.init(240, 240, SPI_MODES[i]); // also pulses RST, so each mode starts from a clean panel state
    tft.setSPISpeed(TFT_SPI_HZ);
    tft.setRotation(0);

    tft.fillScreen(ST77XX_RED);
    Serial.println("RED");
    delay(1000);

    tft.fillScreen(ST77XX_GREEN);
    Serial.println("GREEN");
    delay(1000);

    tft.fillScreen(ST77XX_BLUE);
    Serial.println("BLUE");
    delay(1000);

    tft.fillScreen(ST77XX_BLACK);
    tft.setCursor(30, 100);
    tft.setTextColor(ST77XX_WHITE);
    tft.setTextSize(4);
    tft.printf("MODE %d", SPI_MODE_LABELS[i]);
    Serial.printf("TEXT: MODE %d\n", SPI_MODE_LABELS[i]);
    delay(3000);
  }

  SPI.end(); // release the pins back to plain GPIO for the bit-bang phase
  bitBangTest(true, 0xF81F, "MAGENTA");
  bitBangTest(false, 0xFFE0, "YELLOW");
}
