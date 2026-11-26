#!/usr/bin/env python3
"""Check real Android tile layouts in the installed development APK.

Exercises both languages, 100/115% font sizes, every built-in label and live
hint, and short/long transitions. Isolated configurations leave the device's
language and font size unchanged. Instrumentation restarts the app process;
the activity is reopened afterward to resume its previous management session.
"""
import argparse
import re
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', help='ADB device serial when more than one device is connected')
    args = parser.parse_args()
    adb = ['adb'] + (['-s', args.serial] if args.serial else [])
    try:
        result = subprocess.run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e',
                                     'tileSizing', 'true', 'local.nova.dock/.DeviceCheck'],
                                capture_output=True, text=True, timeout=120)
        print(result.stdout, end='')
        if result.stderr:
            print(result.stderr, end='')
        codes = re.findall(r'INSTRUMENTATION_CODE:\s*(-?\d+)', result.stdout)
        return 0 if (result.returncode == 0 and codes == ['-1']
                     and 'TileSizing=PASS' in result.stdout) else 1
    finally:
        subprocess.run(adb + ['shell', 'am', 'start', '-n', 'local.nova.dock/.MainActivity'],
                       check=False, timeout=30)


if __name__ == '__main__':
    raise SystemExit(main())
