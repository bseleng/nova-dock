# Contributing

Nova Dock is currently a device-specific prototype for Retroid Pocket Nova on Android 13.

## Bug reports

Use the [bug report form](https://github.com/bseleng/nova-dock/issues/new?template=bug_report.yml). Include:

- Android version and full firmware build number.
- Dock/adapter and TV/monitor model, resolution and refresh rate.
- What happened on connection and disconnection, including the app's transition log.
- Relevant game/emulator and external controller, if the issue depends on them.

Remove personal information from logs and screenshots before sharing. Vendor APKs and full system dumps are not needed in public issues.

## Development

Run `python3 build.py` to compile, verify the APK signature and run connection-state tests. GitHub Actions performs the same build on Linux. Keep device checks separate from logic tests: CI has no physical handheld.

Keep the settings command allowlist narrow, preserve the unknown-connection state, and verify that any temporary profile test restores its original settings. Any additional device support needs its own firmware evidence and a physical test report.

Private signing keys belong in the ignored `keys/` directory and must not be committed. Local or CI builds signed with a new key cannot update the maintainer's published APK.
