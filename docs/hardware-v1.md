# Hardware revision IOT V1.5

Source: Altium documents provided in `climate_link_hardware`. Record data was extracted from the binary SchDoc files, including hierarchical sheet entries in Main.SchDoc. This is a schematic interpretation, not an electrical bench verification or PCB connectivity sign-off.

The component in ESP32-S3.SchDoc is U2, ESP32_WROOM_32UE. Build for `esp32`, not `esp32s3`.

- GPIO26: `1W_1`, TRM2 data, 4.7 kΩ pull-up to 3.3 V.
- GPIO27: `1W_2`, TRM1 data, 4.7 kΩ pull-up to 3.3 V.
- TRM1 and TRM2: GND/data/+5V. There is no identified temperature/humidity sensor IC in this sheet. Dummy values are used at the user's request; no sensor bus is driven.
- GPIO34: SW1 to GND, R43 external 10 kΩ pull-up to 3.3 V. GPIO34 is input-only and has no internal pull-up. EXT0 low-level wake uses this button.
- GPIO19: relay 1 control.
- GPIO25: relay 2 / RS485_2 direction shared net.
- GPIO18: first RS485 direction control.
- GPIO17 / GPIO16: first RS485 TX / RX.
- GPIO23 / GPIO5: second RS485 TX / RX.
- GPIO21 / GPIO22: EEPROM SDA / SCL.
- GPIO32 / GPIO33: RTC SDA / SCL.
- GPIO2: LED signal on display connector; unused in firmware.
- GPIO4 / 15 / 13 / 14 / 12: display reset / CS / MOSI / CLK / MISO.
- GPIO0 and EN: boot/programming controls, corrected with R40/R41 pull-ups as described in the supplied Persian DOCX note.

Firmware initializes GPIO19, GPIO25 and GPIO18 low and holds them during Deep Sleep. A physical check must confirm the assembled relay and transceiver states. Other peripherals are not initialized. Firmware cannot remove their hardware quiescent consumption.

Power.SchDoc contains a 24 V input, URB_S-6WRS conversion to 5 V and LD1117-33 regulation to 3.3 V. The MCU sheet also shows a continuously powered LED and CH340 USB circuitry. RTC.SchDoc has backup supply components for the RTC; this is not a main-device battery circuit. No confirmed main battery voltage divider or fuel gauge was found; telemetry reports battery_mv=null.

A later battery board needs a main battery input, charging/protection if rechargeable, a regulator with low quiescent current and adequate Wi-Fi pulse current, removal or isolation of always-on loads, and a verified sensor power arrangement. The 400 mAh lifetime target requires measurements of the complete assembled board under the actual reporting schedule and RF conditions.
