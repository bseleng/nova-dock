# Nova Dock

[![Build](https://github.com/bseleng/nova-dock/actions/workflows/build.yml/badge.svg)](https://github.com/bseleng/nova-dock/actions/workflows/build.yml)
[![Android 13](https://img.shields.io/badge/Android-13-3DDC84?logo=android&logoColor=white)](https://github.com/bseleng/nova-dock#compatibility)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Download](https://img.shields.io/badge/Download-APK-74E3CC)](https://github.com/bseleng/nova-dock/releases)

Automatic docked and handheld profiles for **Retroid Pocket Nova**. Connect a TV to switch to 60 Hz and maximum performance; disconnect it to return to 120 Hz, quiet cooling and the built-in Xbox controller.

**[Download APK](https://github.com/bseleng/nova-dock/releases/tag/v0.1.0)** · **[Русская документация](docs/README.ru.md)** · **[Report an issue](https://github.com/bseleng/nova-dock/issues/new?template=bug_report.yml)**

> **Early prototype.** Profile application has been tested on a real Nova. Physical dock transitions, TV frame pacing and reboot recovery are still being validated. Other handhelds and firmware versions have not been verified.

## Profiles

| Setting | Docked | Handheld |
|---|---|---|
| Refresh rate | **60 Hz** | **120 Hz** |
| Built-in controller | Disabled | Xbox layout |
| Fan | Maximum | Quiet |
| Performance | High Performance | Standard |

The 60 Hz setting reproduces Retroid's own refresh-rate tile. It is intended for setups where keeping the internal display at 120 Hz causes uneven motion on a 60 Hz external display; improvement on a particular TV still needs to be confirmed.

Nova Dock leaves Retroid's built-in **turn off the internal screen when connected to a TV** option in control of the display backlight.

## Getting started

1. Download and install the APK from [Releases](https://github.com/bseleng/nova-dock/releases/tag/v0.1.0).
2. Open **Nova Dock** and enable **Автоматический режим** / automatic mode.
3. Allow notifications so the current profile and stop action remain accessible.
4. Connect or disconnect your external display. Allow about **5 seconds** for the connection to settle and the profile to apply.

No root installation, Shizuku or ADB permission grant is required on the tested stock firmware. A computer is not needed for normal operation.

Turning automation off restores the handheld profile. The app can also restore the settings saved before its first profile application. Stop automation or restore those settings before uninstalling.

## How it works

- Reads the physical DisplayPort connection, with Retroid properties and Android presentation displays as fallbacks.
- Uses display callbacks and firmware events, plus a lightweight periodic check.
- Waits for a stable connection and ignores unknown sensor states.
- Applies the same system settings used by Retroid's quick settings tiles.
- Reapplies and verifies the fan setting after firmware performance/fan coupling settles.
- Runs a foreground service with a notification and supports startup after boot when enabled.
- Has no internet permission, analytics, game-file access or emulator configuration changes.

Retroid's stock **`PServerBinder`** service executes the settings commands with its own privileged identity. Nova Dock does not install root access. This is a vendor-specific interface, not a public Android API; an OTA can change its availability. The command adapter accepts only a fixed list of eight settings and numeric values, and exposes no external command-execution endpoint.

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
```

On the tested firmware, Standard is `performance_mode=0`, High Performance is `2`, Quiet is `fan_mode=1`, and maximum custom fan speed is `fan_mode=6` with `fan_speed=35000`. Thermal protection is not modified.

</details>

## Compatibility

| Device | Android | Firmware | Status |
|---|---|---|---|
| Retroid Pocket Nova | 13 / API 33 | `RPN_V1.0.0.436` | Profile self-test passed; real dock validation in progress |

Measured on the device:

- **Dock profile:** `60.000004 Hz`, fan PWM `35000`, no gamepad exposed through `InputDevice`.
- **Handheld profile:** `120.00001 Hz`, Quiet PWM `12000`, `Xbox Wireless Controller` restored.
- All eight setting values matched the intended profiles; the self-test restored the previous values afterward.
- Foreground automation started and applied the handheld profile without an external display.

Physical TV smoothness, actual reboot recovery, long-term background survival and each emulator's controller/session handling remain to be tested. The app currently accepts only Nova on Android 13.

## Build

Requires **Python 3.9+**, **JDK 17**, **Android SDK Platform 35** and **Build Tools 35.0.0**. macOS and Linux are supported. No Gradle or third-party libraries are used.

```sh
git clone https://github.com/bseleng/nova-dock.git
cd nova-dock
python3 build.py
```

Output: `dist/Nova-Dock-0.1.0.apk`. The build verifies the APK signature and runs the connection-state tests.

Optional variables: `ANDROID_SDK_ROOT` / `ANDROID_HOME`, `NOVA_JAVA_HOME` / `JAVA_HOME`, `NOVA_BUILD_DIR`, `NOVA_APK_PATH`.

A local development signing key is created under `keys/` and excluded from Git. Keep it to sign future compatible updates. Builds signed with a different key cannot update an existing installation. Published APKs use the maintainer's retained local key; CI only checks that the project builds.

This pre-release is a debuggable development build with on-device instrumentation. It is intended for testing rather than store distribution.

## On-device checks

Read-only diagnostic:

```sh
adb shell am instrument -w local.nova.dock/.DeviceCheck
```

Test both profiles **with the external display disconnected**. This temporarily changes the eight settings and restores the previous values afterward:

```sh
adb shell am instrument -w -e profiles true local.nova.dock/.DeviceCheck
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Reports with firmware version, dock/TV model and transition logs are useful while compatibility is being validated.

The vendor interface was investigated on the owner's device; [Hugminn](https://github.com/Ninte86hl/Hugminn) also documents the same stock Retroid service. No vendor APKs, decompiled vendor sources, private device logs or signing keys are included in this repository.

## License

[MIT](LICENSE) — Copyright © 2026 Bogdan Selenginskiy.
