# ClimateLink Android design system

Based on UI/UX Pro Max, installed from https://github.com/nextlevelbuilder/ui-ux-pro-max-skill.

Verified search identities: product Smart Home/IoT Dashboard; style material-you-md3-mobile (explicitly compatible with Android Views); UX Error Placement / Error Messages and Reduced Motion.

The product recommendation includes web landing-page copy, real-time controls, glass effects and a Latin typeface. These are not applied to this battery-powered BLE companion: the app retains native Material 3, opaque surfaces, the existing green identity and embedded Vazirmatn for Persian. Telemetry is identified as sampled, simulated or stale; there are no invented live readings or battery estimates.

The progressive-disclosure search returned an unrelated heading result on both attempts. The disclosure behavior uses the skill's explicit quick-reference section 8 fallback, not a verified database match.

## Screen hierarchy

Overview presents an immediately actionable connection card, last sample metrics and device details. The former oversized promotional hero is removed. The Devices page explains wake, scan and PIN pairing in order. Settings groups Wi-Fi, server and cadence, with destructive credential resets and local HTTP behind explicit disclosures.

## Native interaction

Use 48 dp minimum targets, 4/8 dp spacing increments, native ripple and checkable states. Validate beside each input, retain all invalid fields, and focus a linked error summary for multiple errors. Preserve typed values on failed save. Run is disabled until edits are saved. Confirm before refreshing over an edited form. Do not store password or token in saved state.

## Visual tokens

Keep opaque background/surface/container layers, green primary, amber simulated status, blue humidity. Light and dark palettes are separately defined in Android resources. Normal text pairs target at least 4.5:1. Field boundaries target at least 3:1. Body labels are at least 12 sp, primary copy 14–16 sp, headings 21–26 sp. Gutters adapt at 600 and 840 dp.

## Motion and accessibility

Respect the Android animator duration preference. Cancel previous transitions and set final alpha explicitly on rapid navigation. Mark headings and live connection/count messages using Android accessibility semantics; decorative vector icons stay out of the accessibility tree. Keep system bars and IME outside the usable content area. Validate compact phone, large font, landscape, tablet, light and dark configurations on the emulator.

## Recorded verification

Android Debug APK built successfully; Android Lint reports zero errors (non-blocking warnings remain). Six new JVM form tests pass, covering Persian numerals, collecting all field errors, UTF-8 byte limits, explicit HTTP opt-in, URL credentials/fragments and conflicting credential reset. The existing template unit test also passes.

Emulator checks covered the connection CTA, native page navigation, opening/closing advanced settings and an invalid URL showing an inline error while retaining the typed value. Captures were inspected in light/dark, 375 dp at 200% font size with animation disabled, native landscape rotation and an 800 dp tablet viewport. System emulator preferences were restored after checks. TalkBack interaction and the save/run flow against physical BLE hardware were not verified in this UI pass.
