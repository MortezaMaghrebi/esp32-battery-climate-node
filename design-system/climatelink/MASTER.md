# ClimateLink Android design system

Updated 2026-10-30 for the requested luxurious, futuristic redesign using [Taste](https://github.com/Leonxlnx/taste-skill), installed as `design-taste-frontend`. The app-screen route in `references/app-screens.md` was read and used; the marketing-page route does not apply to this native app. Previous UI/UX Pro Max interaction improvements are retained.

## Direction

Dealt style: **True-black flat**, seed **639804365**, no redraws. Reproducible sheet: [direction.json](../../.taste-skill/direction.json). The `markets` kind admits monitoring products; True-black flat was chosen over Data terminal because this companion is opened for short configuration and status sessions.

The face comes from a precision environmental instrument: black ground, platinum typography, a restrained champagne hue on temperature and the simulated-data qualifier. This is the sheet's allowed product-specific data-hue substitution for lime. The white primary button is inverted neutral; navigation has no accent fill. Resting panels have neither outlines nor shadows. No gradients, glass, neon or fabricated gauges are used.

The signature is the **wake–sample–send–sleep cycle**. Its static native diagram explains the low-power workflow; it never indicates the board's current activity or elapsed time. Uploads follow the configured batch size. The open-orbit/crescent mark appears in the app header and adaptive, monochrome and API 24/25 launcher icons.

## Product and data inventory

1. ClimateLink configures one ESP32 climate node over BLE; it is not a cloud-history dashboard.
2. `get_status` supplies temperature in °C and relative humidity in percent, both displayed to one decimal.
3. Current firmware sensor readings are simulated, as requested; the qualifier follows the `simulated` flag.
4. Before a response, readings are small grey dashes; a valid zero remains a numeric zero. Missing or non-finite readings cannot print `NaN` or infinity.
5. Queue, failure and dropped-sample counts come from the device. There is no invented battery percentage, runtime estimate, trend or history.
6. The operator uses a phone for a short glance or to change Wi-Fi, server and cadence; the interface is Persian RTL.
7. A sleeping node must be awakened before discovery; first pairing uses the existing six-digit PIN flow.
8. Device identity, firmware and signal information belong to the selected/discovered device, never a seeded device fixture.
9. Wi-Fi credentials and server tokens retain their existing explicit reset semantics and are never written to saved instance state.
10. Cadence accepts 60–86400 seconds and 1–32 samples per upload. Form defaults are 900 seconds and one sample; presets are 60, 300, 900 and 3600 seconds.

There is no new UI sample dataset to replace. Actual BLE JSON remains the source of truth. The existing simulated firmware sensor implementation must eventually be replaced by the selected sensor driver.

## The screens' questions

- **Overview:** What was the last environmental sample, and how do I connect to obtain it? The reading panel leads; the disconnected finding is explicit beside the connection action. Device details and diagnostics appear when available.
- **Devices:** Which nearby node can I connect to? Wake instructions and discovery precede the result list; the empty list explains the next action without pretending that a scan has already found zero devices.
- **Settings:** Which network, server and sampling cadence will this node use? Labels sit above native inputs, followed by advanced disclosures and the existing save/run model.

## Native tokens and layout

Ground `#000000`, panels `#111111`, raised controls `#2A2A2A`; text `#F5F5F5`, secondary `#B4B4B4`, faint `#949494`; data accent `#D7BC82`. The identity is deliberately dark in both system day and night modes. Android system bars use light icons.

Vazirmatn is bundled for Persian text, with semibold used sparingly. IDs alone use a system monospace. Figures enable the font's `tnum` feature; all ten Persian digits resolve to equal 1349-unit advances after substitution in the embedded regular face. Titles are 24 sp, body 14 sp, secondary text 12 sp; available temperature leads at 40 sp and humidity at 30 sp. Unknown values remain 22 sp grey dashes.

Panels use 20 dp corners; fields use 10 dp corners and primary controls use pill corners. The main panel gap is 14 dp. Phone gutters are 20 dp; wider layouts retain the existing adaptive gutters. Native buttons are at least 48 dp (primary buttons 52 dp), adapting the sheet's web control sizes to the phone rules.

Width arithmetic: phone content = window minus two page gutters. The readings' inner width = content minus 40 dp panel padding; two equal metric cells plus a 1 dp separator and two 14 dp separator margins fill it. Labels, figures and units share their RTL edge. Above 1.3 font scale, the two cells stack and the divider rotates into a horizontal rule. The diagram computes its four equally spaced nodes from its actual native width so circles do not stretch.

## Interaction and accessibility

Three labeled bottom destinations remain available. Native press, checked, focus and disabled feedback replaces page entrance animation. Visible field labels use `labelFor`; decorative marks and diagram strokes are excluded from accessibility traversal. Existing live connection/count announcements, input validation, retained invalid values, unsaved-edit guards, refresh confirmation, credential handling and IME/system-bar insets remain.

The simulated flag is a data qualifier, not a live status. A stale sample remains explicitly labeled after disconnect. The static cycle and displayed queue counts do not imply live streaming.

## Visual critique

Three build/look passes were used; the direction itself was not redrawn.

The first pass exposed mismatched RTL edges between labels and Latin units, excessive native font padding and a cycle extending below the phone fold. The second aligned values/units to `viewStart`, removed excess font padding and stacked metrics at large text sizes. The final pass compacted the header and replaced a stretching vector trace with the native `ClimateCycleView` so the complete labeled cycle appears on a standard phone.

1. The largest available figure answers the overview question; unknown data stays visually quiet beside the connection action.
2. Panels share their page edges and do not stretch vertically to fill unused tablet height. Narrow landscape uses native scrolling; no values or controls are dropped.
3. Champagne appears only on data/its qualifier, not navigation or buttons. The custom mark and labeled power cycle identify ClimateLink.
4. Readings and counts come from each BLE reply. The time caption is the local read time, not an invented sensor timestamp; schedule summary and selected preset derive from the same fields.
5. Missing and zero are different states. The discovery count is unknown before a scan and reflects discovered devices afterward.
6. No chart or legend is invented. The static cycle's labels align with its equally spaced nodes.
7. Phone navigation remains a labeled bottom bar. At 200% font size the readings stack and content scrolls without clipping the navigation.

## Verification

`assembleDebug`, `lintDebug` and `testDebugUnitTest` succeed. Seven JVM tests pass. Android Lint reports zero errors; non-blocking warnings remain, including unused legacy resources and dependency/tooling suggestions. These warnings are not represented as a clean warning-free report.

Emulator interaction checks cover the connection CTA, all three destinations, opening/closing Wi-Fi advanced options, retaining an invalid URL with an inline HTTPS error, and selecting a cadence preset. Screenshots were checked at 450 dp and 375 dp phone widths, 375 dp with 200% text and animation disabled, an 800 dp tablet, actual 1600×900 landscape, and system night mode. A transient portrait capture was rejected and the landscape artifact was recaptured after verifying Android's `land / ROTATION_90` configuration. Original emulator preferences were restored.

Source checks parse every resource XML, reject gradient drawables, verify embedded Persian tabular digits, and calculate contrast. Primary/secondary/faint words meet 4.5:1 on their intended surfaces; field boundaries are 4.22:1. Exact ratios and QA metadata are in `.taste-skill/native-style-checks.txt` and `.taste-skill/native-qa.json`.

Taste's source linter was attempted and reports no supported web source files: it does not parse Java/XML. Its browser inspector likewise cannot inspect an APK. Native Android Lint, source checks and actual emulator images substitute for those web-only gates; no browser PASS is claimed. Physical BLE pairing, device save/run and TalkBack interaction are not verified by this redesign's emulator checks.

## Previews

[Overview](../../docs/screenshots/taste/overview.png), [Devices](../../docs/screenshots/taste/devices.png), [Settings](../../docs/screenshots/taste/settings.png), [validation](../../docs/screenshots/taste/validation.png), [large text](../../docs/screenshots/taste/large-text.png), [landscape](../../docs/screenshots/taste/landscape.png), [tablet](../../docs/screenshots/taste/tablet.png).
