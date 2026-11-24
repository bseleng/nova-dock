# Changelog

## 0.4.0 — 2026-11-25

- Replaced profile editors with icon tiles that cycle values on tap and save immediately.
- Preserved compatible fan choices when cycling performance; fall back to Smart if required.
- Enlarged and faded profile artwork behind the card contents, clipped at the rounded edges.
- Added line drawings for both modes, including a standalone 4:3 handheld console.
- Replaced the plain history message with a wide, scrollable event list, larger timestamps and colored status badges.
- Show reapplication only as an error-recovery action; removed the permanent retry button.
- Added a separate opt-out for startup after boot, without stopping the current automation session.
- Separated automatic mode switching from startup using matching switches; added manual Docked / Handheld selection when automatic switching is off.
- Preserve the active mode when automatic switching is disabled; resume the chosen automatic or manual session after reboot when startup is enabled.
- Removed duplicate parameter summaries and instructional text from the main screen; brightened the active badge and improved gamepad focus padding.
- Keep system quick-panel edits until a mode change; app tiles apply only the selected setting and necessary fan/performance coupling.
- Show temporary live values alongside saved values, and support D-pad navigation with visible focus plus gamepad A/B.
- Coalesced rapid changes with revision acknowledgements and ordered fan/performance dependencies.
- Serialized manual mode transitions and ignored stale control requests so rapid mode changes settle at the last selection.
- Documented verified automatic startup after real handheld and docked reboots on 0.3.0.

## 0.3.0 — 2026-11-24

- Added a bypass-charging switch to each profile: enabled by default in the dock, disabled in handheld mode.
- Preserved existing profile choices when adding the new setting.
- Integrated the stock `is_charging_separation` setting into application, verification and original-setting restoration.
- Extended original-setting snapshots only for missing keys, preserving earlier original values.
- Settled charging changes before restoring the fan, avoiding a late Smart override when normal charging resumes.
- Added device checks for the charging driver, inverse user choices, snapshot migration and exact restoration of all nine settings.

## 0.2.0 — 2026-11-24

- Added separate editable docked and handheld profiles.
- Added Leave unchanged / 60 Hz / 120 Hz for each profile. Leave unchanged does not write refresh-rate settings.
- Added fan and performance preset selection, with the same allowed combinations as Retroid's quick settings.
- Added Xbox / Retro layout selection for the handheld profile.
- Saved profile edits apply immediately when that profile is active and automation is enabled.
- Recheck the selected fan after power-status and wake events. This fixes the stock firmware's late Smart override without reconnecting the controller or changing refresh settings.
- Opening the app resumes an enabled foreground service, including after developer instrumentation.
- On-device checks cover custom profiles, unchanged refresh rate and restoring the previous device/profile settings.

The owner confirmed one physical TV connection/disconnection cycle with 0.1.0. Custom presets and the charging-status regression were validated on the same device for 0.2.0. At the time of that release, actual reboot recovery and broader device/firmware coverage had not been checked.

## 0.1.0 — 2026-11-24

- Initial fixed profiles for Nova on Android 13.
- Stock Retroid bridge integration, foreground automation, transition logging and original-setting restoration.
- On-device profile checks and connection-state tests.
- Public source, bilingual documentation, MIT license and CI build.
