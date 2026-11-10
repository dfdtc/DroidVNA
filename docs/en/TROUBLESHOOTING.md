# Troubleshooting

> [中文版](../TROUBLESHOOTING.md)

First note the app version, Android version, VNA / E-Cal model and firmware, then check Device → Log and USB diagnostics. Include this information and the reproduction steps when filing an issue.

## Installation issues

If Xiaomi/Redmi/HyperOS reports `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`, use shell installation (verified on our test devices):

```bash
adb push dist/DroidVNA-0.7.1-beta-release.apk /data/local/tmp/droidvna.apk
adb shell pm install -r -t /data/local/tmp/droidvna.apk
```

Or enable **Install via USB** in Developer options. The app itself only needs USB Host access and does not need root.

If installation reports a signature mismatch, an older build signed with a different keystore is installed: uninstall it first, then install the current APK (uninstalling clears saved calibrations and settings).

## Common symptoms and fixes

| Symptom | What to do |
| --- | --- |
| No USB VNA on the Device page | Try another cable/OTG adapter; confirm the device is powered; tap "Rescan"; check whether USB diagnostics enumerate `0483:564E` or `367F:0200` |
| Stuck on CONNECTING | Check the system USB permission dialog; close the desktop LibreVNA-GUI (it may already own the interface); replug the device |
| DeviceInfo request fails | The firmware protocol may not be v14; upgrade the firmware with the desktop app |
| Sweep paused / rate does not change | Tap "Start sweep"; after a single sweep you must tap Start again for continuous sweeping |
| Xiaomi/HyperOS installation fails | Use `adb push` + `pm install`, or enable Install via USB in Developer options |
| Exported file has a `.txt` suffix | Older-version issue; current builds use `application/octet-stream` and keep `.s2p` |
| Trace does not change after calibration | Make sure the standards were really connected when captured; calibration only interpolates within the saved frequency range; try clearing the calibration and redoing it |
| B60 shows no points / a status every 2 seconds | This is the FW 1.8.6 first-configuration bug; the app resets SA→VNA automatically. If Device → Log reports that automatic recovery failed, replug the B60 and reconnect |
| B60 sweeps very slowly | Check IFBW; start with the default 75 kHz. For lower noise drop to 1 kHz / 100 Hz / 15 Hz; the first frame is slower, but points appear progressively and the breathing indicator is shown next to "Acquisition" |
| `.cal` import fails | Confirm it is a LibreVNA-GUI format-3 file; standards need Touchstone data; connect the USB VNA first because the import is saved under the current serial; the log/prompt lists missing Open/Short/Load or Through data |
| E-Cal not found | Confirm VID:PID `1209:4122` and a powered OTG hub; make sure the device appears in USB diagnostics, then tap "Detect and connect" and allow permission |
| E-Cal calibration fails | Confirm the VNA produces data, coefficients reached 100%, and temperature is stable; on the B60 every standard switch performs an SA→VNA reset, so retry after a failure; do not unplug the device during calibration |

## Related documents

- Connecting and B60 firmware behavior: [User guide](USER-GUIDE.md)
- Calibration flow and limits: [Calibration guide](CALIBRATION.md)
- Known limitations: [Roadmap and limitations](ROADMAP.md)
