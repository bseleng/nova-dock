# Nova Dock

[![Build](https://github.com/bseleng/nova-dock/actions/workflows/build.yml/badge.svg)](https://github.com/bseleng/nova-dock/actions/workflows/build.yml)
[![Android 13](https://img.shields.io/badge/Android-13-3DDC84?logo=android&logoColor=white)](https://github.com/bseleng/nova-dock#compatibility)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Download](https://img.shields.io/badge/Download-APK-74E3CC)](https://github.com/bseleng/nova-dock/releases)

Configurable docked and handheld profiles for **Retroid Pocket Nova**. Choose refresh rate, fan, performance and bypass charging for each mode, plus the built-in controller layout to restore when undocking. Change values directly from the tiles using touch or a gamepad.

**[Download APK](https://github.com/bseleng/nova-dock/releases/tag/v0.4.0)** · **[Русская документация](docs/README.ru.md)** · **[Report an issue](https://github.com/bseleng/nova-dock/issues/new?template=bug_report.yml)**

> **Early prototype.** A physical dock/undock cycle has been confirmed by the owner on a real Nova. Configurable presets and automatic startup after real handheld and docked reboots are tested on the device. Long-term behavior and other firmware versions remain unverified.

## Default profiles

| Setting | Docked | Handheld |
|---|---|---|
| Refresh rate | **60 Hz** | **120 Hz** |
| Built-in controller | Disabled | Xbox layout |
| Fan | Maximum | Quiet |
| Performance | High Performance | Standard |
| Bypass charging | **Enabled** | **Disabled** |

## Customize each profile

Tap a tile to cycle its value. Changes save immediately. In the active card, a change also applies immediately; in the other card, it is saved for the next mode switch. No settings editor or Save button is needed.

- **Refresh rate:** Leave unchanged → 60 Hz → 120 Hz.
- **Performance:** Standard → Performance → High Performance.
- **Fan:** cycles the presets supported by the saved performance setting and, for the active card, the current live performance setting.
- **Bypass charging:** On / Off. Defaults are **on in the dock, off in handheld mode**. Upgrades preserve saved choices.
- **Handheld controller:** Xbox / Retro. The dock profile disables the built-in controller.

| Performance preset | Available fan presets |
|---|---|
| Standard | Off, Quiet, Smart, Sport, Maximum |
| Performance | Quiet, Smart, Sport, Maximum |
| High Performance | Smart, Sport, Maximum |

Changing performance preserves a compatible current fan setting, including a custom speed. An incompatible fan falls back to Smart. **Leave unchanged** writes neither refresh-rate setting and does not restore an earlier rate. External display modes remain under firmware control.

### Temporary changes from the system quick panel

Manual changes in Retroid's quick panel remain in effect until the next dock/handheld transition or a fresh management session. Nova Dock does not repeatedly overwrite them after power or wake events. The active card shows live values; when they differ from the saved profile, a small **Сохранено:** / Saved label shows the future profile value.

An app tile saves its chosen value and applies that setting immediately, preserving other temporary choices except necessary fan/performance dependencies. The next mode switch applies the complete saved profile. Bypass charging uses Retroid's stock switch; firmware charging limits, sleep behavior and thermal control still apply.

### Gamepad controls

Use the **D-pad** to move the visible focus, **A** to activate the selected tile or action, and **B** to go back or close a dialog. Focused controls have a bright rounded outline, and the screen scrolls to the selected control.

The **Журнал** / Log button opens a wide event list with single-line timestamps, separated entries and colored status badges. **Применить снова** / Apply again appears only after an application error.

## Screenshots

Captured directly from the Retroid Pocket Nova running version 0.4.0 (1280 × 960). Open a caption to view the original screenshot. Profile values in the screenshots are the owner's saved choices.

| [Main screen](docs/images/overview.png) | [Profile tiles](docs/images/profiles.png) |
|---|---|
| <img src="docs/images/overview.png" width="420" alt="Nova Dock automatic switching and startup switches"> | <img src="docs/images/profiles.png" width="420" alt="Docked and handheld tiles with cropped background artwork and active badge"> |
| [Formatted log](docs/images/journal.png) | [Gamepad focus](docs/images/controller-focus.png) |
| <img src="docs/images/journal.png" width="420" alt="Wide log with single-line timestamps and colored event statuses"> | <img src="docs/images/controller-focus.png" width="420" alt="Visible gamepad focus on a profile tile"> |

[Manual mode selection](docs/images/manual-mode.png) appears in a third row when automatic switching is off.

Nova Dock leaves Retroid's built-in **turn off the internal screen when connected to a TV** option in control of the display backlight.

## Getting started

1. Download and install the APK from [Releases](https://github.com/bseleng/nova-dock/releases/tag/v0.4.0).
2. Open **Nova Dock**, choose your presets, then enable **Переключать режимы автоматически** / switch modes automatically, or select a mode manually.
3. Allow notifications so the current profile and stop action remain accessible.
4. Connect or disconnect your external display. Allow about **5 seconds** for the connection to settle and the profile to apply.

No root installation, Shizuku or ADB permission grant is required on the tested stock firmware. A computer is not needed for normal operation.

Turning automatic switching off keeps the current mode and values. Manual mode controls appear below the two switches. The app can also restore the settings saved before its first profile application. Restore those settings before uninstalling if you want the original device configuration back.

## Automatic and manual modes

The top panel has two matching switches:

- **Переключать режимы автоматически** / Switch modes automatically: watches the external display and applies the saved docked or handheld profile when the connection changes.
- **Запускать после загрузки** / Start after boot: resumes the last active management session after Android finishes booting.

With automatic switching off, **В доке** / Docked and **В руках** / Handheld buttons appear in a third row. Choosing a different mode applies its complete saved profile, independently of the display connection. Display and power events do not change the manual mode. Selecting the already active mode leaves temporary changes intact.

Tiles in the active manual card apply changes immediately, just as in automatic mode. The active badge identifies the currently selected mode. Normal parameter summaries are omitted from the top panel; errors, waiting and recovery appear there only when needed.

## Startup after reboot

**Start after boot** is enabled by default and works with either automatic or manual management. Turning it off leaves the current session running but prevents startup after the next reboot. Opening the app manually resumes a session that has not been explicitly stopped.

The notification's Stop action stops management and leaves current device values in place. Restoring original settings also stops management. After either action, choose a manual mode or enable automatic switching to begin a new session. An APK update resumes an active session independently of the boot switch.

Real automatic-mode reboots with and without an external display were verified on 0.3.0 without opening Nova Dock. The startup option was also checked on 0.4.0 with real on/off reboots. See compatibility notes for the tested firmware and limits.

## How it works

- Reads the physical DisplayPort connection, with Retroid properties and Android presentation displays as fallbacks.
- Uses display callbacks and firmware events, plus a lightweight periodic check.
- Waits for a stable connection and ignores unknown sensor states.
- Applies the same system settings used by Retroid's quick settings tiles.
- Settles Retroid performance/charging changes before restoring a compatible fan during an explicit application.
- Applies app tile edits through one serialized worker, coalescing repeated changes and acknowledging the latest request.
- Leaves subsequent manual quick-panel values alone until the next mode switch.
- Extends the saved original-settings snapshot only for newly managed settings, preserving older original values.
- Runs a foreground service with a notification and supports startup after boot when enabled.
- Has no internet permission, analytics, game-file access or emulator configuration changes.

Retroid's stock **`PServerBinder`** service executes the settings commands with its own privileged identity. Nova Dock does not install root access. This is a vendor-specific interface, not a public Android API; an OTA can change its availability. The command adapter accepts only a fixed list of nine settings and numeric values, and exposes no external command-execution endpoint.

<details>
<summary>System settings used</summary>

```text
min_refresh_rate
peak_refresh_rate
performance_mode
fan_mode
fan_speed
temp_abxy_layout_mode
no_create_gamepad_button_layout
flip_button_layout
is_charging_separation
```

On the tested firmware, Standard is `performance_mode=0`, High Performance is `2`, Quiet is `fan_mode=1`, and maximum custom fan speed is `fan_mode=6` with `fan_speed=35000`. Bypass charging is `is_charging_separation=1`; normal charging is `0`. Thermal protection is not modified.

</details>

## Compatibility

| Device | Android | Firmware | Status |
|---|---|---|---|
| Retroid Pocket Nova | 13 / API 33 | `RPN_V1.0.0.436` | Physical dock/undock cycle confirmed; configurable profile checks passed |

Measured on the device:

- **Dock profile:** `60.000004 Hz`, fan PWM `35000`, no gamepad exposed through `InputDevice`.
- **Handheld profile:** `120.00001 Hz`, Quiet PWM `12000`, `Xbox Wireless Controller` restored.
- All eight setting values matched the intended profiles; the self-test restored the previous values afterward.
- Foreground automation started and applied the handheld profile without an external display.

The owner completed a real TV connection/disconnection cycle with the initial prototype and reported expected operation. The transition log confirmed both modes, with no repeated profile application during the dock session.

In 0.2.0, on-device tests also verified Smart/Performance while leaving frequency unchanged, and Sport/Performance/Retro at 60 Hz. A simulated charging-status change reproduced the stock Smart override; the saved Quiet preset was restored in about 1.7 seconds without changing the controller device ID or refresh settings.

In 0.3.0, the stock bypass switch and the charging driver were verified in both directions: enabled produced `usb_charge_now=0`, disabled produced `1`. Tests covered the opposite user choices, saving from the real UI, upgrading an older original-settings snapshot and restoring all nine previous setting values. Existing profile choices were preserved.

A real handheld reboot on 0.3.0 also passed: `BOOT_COMPLETED` started the foreground service without opening the activity; the saved 120 Hz / Xbox / Quiet / Standard / normal-charging profile was applied about three seconds after service startup. The charging driver reported `usb_charge_now=1`. Saved profile choices and original-setting snapshots were unchanged.

The owner then rebooted with the external display connected and returned the Nova to the computer. The new boot and foreground-service state confirmed `BOOT_COMPLETED` startup. The transition log recorded the saved 60 Hz / Sport dock profile about two seconds after service startup, then the handheld profile when the display was disconnected. No profile errors were recorded; saved choices and original-setting snapshots were preserved.

In 0.4.0, device checks passed for live manual overrides, applying one app tile without resetting other temporary values, rapid coalesced changes, compatible custom fan speed, and the fan/performance dependency order. Both complete profiles and custom presets passed again, including charging-driver checks and exact restoration of all nine settings. Android gamepad D-pad/A/B events were tested on the Nova for navigation, value changes and dialog handling; external controller models have not been individually certified.

Two further real reboots verified the startup option: off kept the foreground service stopped, while on started it through `BOOT_COMPLETED` and applied the handheld profile without opening the app. Manual opening resumed a previously enabled mode. Existing profiles and original-setting snapshots were preserved.

Manual-mode checks on 0.4.0 verified that turning automatic switching off preserves runtime overrides, manual Docked works without an external display, selecting the same mode does not reset temporary values, and active tiles apply immediately. Rapid mode changes settled at the last selection. Restoring original settings stopped management; reopening left those values unchanged. The complete profile self-test also restored the entire manual-session preference snapshot.

Two real reboots then checked manual Docked mode: startup off kept the service stopped until the app was opened; startup on resumed the manual dock profile through `BOOT_COMPLETED`, without opening Nova Dock or attaching an external display. Bypass charging was enabled in the driver. The user's saved profiles, startup choice and original-setting snapshot were preserved.

Long-term background survival, objective TV frame-pacing measurements and each emulator's controller/session handling remain unverified. The app currently accepts only Nova on Android 13.

## Build

Requires **Python 3.9+**, **JDK 17**, **Android SDK Platform 35** and **Build Tools 35.0.0**. macOS and Linux are supported. No Gradle or third-party libraries are used.

```sh
git clone https://github.com/bseleng/nova-dock.git
cd nova-dock
python3 build.py
```

Output: `dist/Nova-Dock-0.4.0.apk`. The build verifies the APK signature and runs the connection-state, setting-queue and control-state tests.

Optional variables: `ANDROID_SDK_ROOT` / `ANDROID_HOME`, `NOVA_JAVA_HOME` / `JAVA_HOME`, `NOVA_BUILD_DIR`, `NOVA_APK_PATH`.

A local development signing key is created under `keys/` and excluded from Git. Keep it to sign future compatible updates. Builds signed with a different key cannot update an existing installation. Published APKs use the maintainer's retained local key; CI only checks that the project builds.

This pre-release is a debuggable development build with on-device instrumentation. It is intended for testing rather than store distribution.

## On-device checks

Read-only diagnostic:

```sh
adb shell am instrument -w local.nova.dock/.DeviceCheck
```

Test both profiles **with the external display disconnected**. This temporarily changes the nine settings and restores the previous values afterward:

```sh
adb shell am instrument -w -e profiles true local.nova.dock/.DeviceCheck
```

To also test non-default profiles and **Leave unchanged**:

```sh
adb shell am instrument -w -e profiles true -e customProfiles true local.nova.dock/.DeviceCheck
```

Instrumentation restarts the application process. Reopen Nova Dock afterward to resume an active automatic or manual session:

```sh
adb shell am start -n local.nova.dock/.MainActivity
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Reports with firmware version, dock/TV model and transition logs are useful while compatibility is being validated.

The vendor interface was investigated on the owner's device; [Hugminn](https://github.com/Ninte86hl/Hugminn) also documents the same stock Retroid service. No vendor APKs, decompiled vendor sources, private device logs or signing keys are included in this repository.

## License

[MIT](LICENSE) — Copyright © 2026 Bogdan Selenginskiy.
