// Standalone LCD swap-test - zero application code on purpose.
// Only built by `pio run -e display_test`, see platformio.ini.
//
// Goal: settle whether the physical GMT130-V1.0 (ST7789 240x240) module is
// DOA. Wiring/pins already verified correct in the original investigation
// (docs/DEVELOPMENT-LOG.md) - this just drives the panel directly with no
// other firmware logic in the way.
//
// If this shows nothing: module is dead, don't re-open the pin/driver
// investigation. If this DOES show something: the module is fine and the
// old conclusion was wrong - worth re-checking what differed.

#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>
#include <SPI.h>

#define TFT_SCLK 8
#define TFT_MOSI 10
#define TFT_DC   21
#define TFT_RST  20
#define TFT_CS   -1  // module has no CS pin

Adafruit_ST7789 tft(TFT_CS, TFT_DC, TFT_RST);

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("display_test: SPI.begin + tft.init");

  SPI.begin(TFT_SCLK, -1 /* MISO unused */, TFT_MOSI, -1 /* SS unused, no CS wired */);
  tft.init(240, 240);
  tft.setRotation(0);

  Serial.println("display_test: init done, cycling colors");
}

void loop() {
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
  tft.setCursor(20, 100);
  tft.setTextColor(ST77XX_WHITE);
  tft.setTextSize(3);
  tft.println("HELLO");
  Serial.println("TEXT");
  delay(2000);
}
