# ClimateLink BLE protocol v1

Service UUID: `a2c10001-7d4b-4cb8-9b31-57f53cb1a001`

RPC characteristic UUID: `a2c10002-7d4b-4cb8-9b31-57f53cb1a001`

NimBLE GATT, one connection. Authenticated encrypted reads and writes with a 16-byte key are required. Secure Connections is enabled, legacy pairing disabled, bonding persisted in NVS. Peripheral IO capability is Display Only; its dedicated randomly generated six-digit passkey is printed on UART during the physical setup window. Android uses system bonding UI. The PIN survives firmware updates that preserve NVS. If firmware NVS is erased, forget the old bond in Android Bluetooth settings before pairing again. Repeat pairing for an existing bond is rejected rather than silently deleting that bond.

The service UUID is advertised and the local name `ClimateLink-xxxxxx` is in scan response. BLE is available for 180 seconds on cold/reset/button wake. It is not active on scheduled timer wakes. The setup deadline applies even during an established connection.

Each request is a UTF-8 JSON object followed by one LF byte. Send successive acknowledged writes, no Write Without Response. Android negotiates MTU 247 and sends at most 180 bytes per fragment, falling back to 20 bytes for MTU 23. Maximum whole frame including LF is 1535 bytes. Raw NULs and multiple frames in one write are rejected. A disconnect resets partial input and the cached response. There is one request in flight; after its final write acknowledgment, read the characteristic. Responses fit in 511 bytes and use GATT long reads if necessary. The next command replaces the cached response. Do not pipeline commands.

## Commands

```json
{"cmd":"get_status"}
{"cmd":"get_config"}
{"cmd":"set_config","ssid":"my-network","password":"example-pass","server_url":"https://example.com/api/v1/measurements","token":"replace-with-token","sample_s":900,"upload_every":1,"allow_http":false}
{"cmd":"run"}
```

`get_status` returns `ok`, `device_id`, `firmware`, `simulated`, `temperature_c`, `humidity_pct`, `battery_mv`, `queued`, `failures`, `dropped`. The measurement is the current wake's cached sample, not an immediate new sensor conversion.

`get_config` returns `ok`, `ssid`, `server_url`, `sample_s`, `upload_every`, `allow_http`, `has_password`, `has_token`. It never returns password or token.

`set_config` validates and commits a single config blob to NVS. Fields omitted are preserved. An explicit empty password selects an open Wi-Fi network. An explicit empty token clears authorization. Limits are byte lengths: SSID 32, WPA2 passphrase 8–63 (or empty), URL 192, token 128. Sample interval 60–86400 seconds; upload cadence 1–32 samples. HTTPS is the default; HTTP requires explicit allow_http=true for local development. Wi-Fi connectivity is checked during the subsequent run, not while saving.

`run` returns `{"will_sleep":true,"ok":true}`. Reading that response signals the main task to end BLE after a 500 ms acknowledgment grace, then continue the current cycle and sleep. Read the acknowledgment to trigger the command. If the upload cadence is not yet reached, this cycle will only sleep. The setup timeout also ends the session without a run command.

An error response is `{"ok":false,"error":"invalid_config"}` or another short machine-readable reason. The app shows the error and retains the connection for correcting the form. Transport errors disconnect to discard any incomplete request.

Secrets remain in ordinary prototype NVS. Production flash encryption, NVS encryption and secure boot need a separate provisioning and recovery design.

References: [ESP-IDF NimBLE example](https://github.com/espressif/esp-idf/tree/v5.5.2/examples/bluetooth/nimble/bleprph), [Android BLE permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).
