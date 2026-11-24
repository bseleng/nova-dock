# Changelog

## 0.2.0 — 2026-11-24

- Added separate editable docked and handheld profiles.
- Added Leave unchanged / 60 Hz / 120 Hz for each profile. Leave unchanged does not write refresh-rate settings.
- Added fan and performance preset selection, with the same allowed combinations as Retroid's quick settings.
- Added Xbox / Retro layout selection for the handheld profile.
- Saved profile edits apply immediately when that profile is active and automation is enabled.
- Recheck the selected fan after power-status and wake events. This fixes the stock firmware's late Smart override without reconnecting the controller or changing refresh settings.
- Opening the app resumes an enabled foreground service, including after developer instrumentation.
- On-device checks cover custom profiles, unchanged refresh rate and restoring the previous device/profile settings.

The owner confirmed one physical TV connection/disconnection cycle with 0.1.0. Custom presets and the charging-status regression were validated on the same device for 0.2.0. Actual reboot recovery and broader device/firmware coverage remain unverified.

## 0.1.0 — 2026-11-24

- Initial fixed profiles for Nova on Android 13.
- Stock Retroid bridge integration, foreground automation, transition logging and original-setting restoration.
- On-device profile checks and connection-state tests.
- Public source, bilingual documentation, MIT license and CI build.
