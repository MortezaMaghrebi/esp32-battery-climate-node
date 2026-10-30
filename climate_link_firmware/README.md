# ClimateLink firmware

ESP-IDF 5.5.6, target esp32, ESP32-WROOM-32UE. Run in an initialized ESP-IDF shell:

```powershell
idf.py set-target esp32
idf.py build
idf.py -p COM5 flash monitor
```

This v0.1 firmware generates dummy readings. GPIO26/27 sensor pins are documented but not driven. GPIO34 SW1 opens the setup window from deep sleep; cold boot/reset also opens it. The pairing PIN is printed in the UART monitor. Wi-Fi credentials and reporting configuration are applied by the Android app over authenticated BLE. See ../docs/ble-protocol.md and ../docs/hardware-v1.md.

The local environment used to write v0.1 had no ESP-IDF/toolchain; firmware compilation and real-board behavior have not yet been verified. There is no OTA partition or OTA implementation in this revision. Preserve NVS across normal flashes; erasing flash loses config, bonds and pairing PIN.
