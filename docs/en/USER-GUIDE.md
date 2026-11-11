# User guide

> [中文版](../USER-GUIDE.md)

This guide covers installing DroidVNA, connecting a device, touch gestures, UI settings, and data export.

- Calibration (SOL / SOLT / E-Cal / `.cal` import): see the [Calibration guide](CALIBRATION.md)
- Problems: see [Troubleshooting](TROUBLESHOOTING.md)
- Building and development: see [Building and development](DEVELOPMENT.md)
- Version history: see [CHANGELOG](../../CHANGELOG.md) and [release notes](release-notes/)

## Feature overview

### Measurement and charts

- **Real-time two-port sweeps**: S11 / S21 / S12 / S22, continuous or single sweeps, pause/resume.
- **Visible progress on slow sweeps**: at low IFBW, when the first frame is slow, both the "Acquisition" card and the chart show a breathing indicator that a sweep is running; points that have arrived are drawn immediately instead of waiting for the whole frame.
- **Ring-buffer updates**: continuous sweeps no longer clear and redraw the chart every frame. New-frame points overwrite previous ones one by one, and points that have not arrived keep the previous value, so the chart always shows a complete trace and reading is never interrupted.

  ![Low IFBW sweep progress](../screenshots/low_ifbw.png)

- **Y-axis auto / manual**: the `Y auto` button above the chart switches between auto and manual scaling; pinching the chart switches to manual, and one tap restores auto.
- **Touch-first chart**:
  - Pinch to zoom / pan (both axes); the Smith chart has its own zoom/pan viewport
  - Tap or drag with one finger for cursor readout
  - Drag a marker line to change its frequency; tap `FIT` to restore the full span
  - dB, phase, VSWR, and Smith chart display modes
  - Trace settings can enable a **log frequency axis**, with 1-2-5 logarithmic ticks on X
- **Markers**: add, drag, and delete freely; peak/valley search; dual-marker Δ difference.
- **Analysis**: the "Analysis ▾" menu next to the marker row provides three tools.
  - **Bandwidth search**: use the peak or a custom cursor (an existing marker) on the selected S-parameter trace as the reference, find both edges at -X dB (default -3 dB), and add the two edge markers.
  - **XdB search**: the general form of bandwidth search, with a choice of left, right, or both sides; bandwidth search is the both-sides XdB search.
  - **DTF (time domain / distance to fault)**: windowed IFFT of the S11 / S22 complex reflection, converted to fault distance using the velocity factor; configure the window, velocity factor, IFFT zero-padding, peak threshold, and cable-loss compensation, and get a distance-domain trace plus a fault table.

### UI and interaction

- **Single top-right menu**: all settings entry points (sweep settings, trace settings, calibration, export, device, app settings, hide controls) live in the top-right `⋮` menu; the measurement page has no title bar, so the trace chart uses the full height.
- **Background watermark**: the DroidVNA mark is drawn as a translucent watermark behind the trace chart (beneath the traces). "Disable app watermark" in App settings removes the whole layer.
- **Collapsible control rows**: the trace/mode/Y-axis row and the acquisition card column on the right of large screens can be collapsed with one tap (menu "Hide trace controls", the `⌃` button at the end of the row, or `⌃` on the acquisition card). The chart gets larger while marker shortcuts remain.
- **Vertical acquisition rail in landscape**: on a phone in landscape, acquisition controls no longer squeeze into the bottom; they form a column pinned to the left, with the chart on the right.

  ![Phone landscape](../screenshots/phone-landscape.png)

- **Remembered settings**: sweep parameters, marker positions, trace parameters, chart mode, Y-axis scaling, log frequency axis, watermark switch, collapsed-control state, and UI language are saved automatically and restored on the next launch; "Auto-start sweep after connect" can be turned off to require tapping "Start sweep".
- **Chinese/English UI**: `⋮` → "App settings" → "Interface language" offers Follow system / Chinese / English; switching takes effect immediately and persists. About information is in the same panel.

### Calibration and data

- **Calibration memory**: SOLT / SOL / THRU / E-Cal / `.cal` import results are saved immediately as a snapshot tagged with the device serial and source, loaded automatically on the next launch, and shown as "Loaded the previous calibration" at the top of the measurement page. You can "disable calibration" (keep the coefficients) or "forget the saved calibration" at any time. See the [Calibration guide](CALIBRATION.md).
- **1-port SOL calibration**: Open / Short / Load, solving directivity, source match, and reflection tracking, saved by frequency and applied with interpolation; optional manual delay. Calibrations are isolated per device serial, so a demo/other device's calibration is never applied to a B60.
- **Full 12-term two-port SOLT**: Open / Short / Load on both ports + THRU, optionally one isolation measurement, solving directivity, source match, reflection tracking, receiver match, transmission tracking, and isolation, then correcting every point with the multiport formula `S = B·A⁻¹`. Unlike THRU normalization it also removes port mismatch, so S21 / S12 stay accurate with strong port reflections.
- **THRU normalization**: through-response calibration for S21 / S12 (0 dB normalization only, no mismatch correction; still useful for single-port devices or old calibrations).
- **Desktop `.cal` import**: reads a LibreVNA-GUI format-3 `.cal`, rebuilds the calibration on the phone, and applies it immediately; when both ports' 1-port measurements and THRU are present it rebuilds the full 12-term SOLT (including Isolation), otherwise it falls back to 1-port SOL + THRU; saved under the current VNA serial.
- **Electronic calibration kits (LibreCAL / ECB-60)**: reads FACTORY coefficients over USB SCPI and switches Open/Short/Load/Through automatically; a two-port kit solves the full 12-term SOLT directly. **The isolation measurement is an optional checkbox (off by default)** because two-port E-Cals such as the ECB-60 have poor isolation paths; you can measure isolation later with mechanical 50 Ω loads. Temperature/stability status is shown.
- **Export**: Touchstone `.s2p` / `.s1p` and CSV to `Download/DroidVNA`, or send through the system share sheet. See [Export](#export).
- **Demo mode**: a built-in 1.75–2.65 GHz band-pass filter DUT and a realistic systematic error model, so the complete SOL calibration and export flow works without hardware.

### Device and connection

- **Device page**: hardware/firmware/protocol versions, lock and temperature status, USB diagnostics, device log.
- **Protocol v14**: supports LibreVNA V1 / P2 / PT and Harogic B60 (the same USB protocol; the device's DeviceInfo is authoritative).
- **Plug and play**: recognizes LibreVNA `0483:564E`, `0483:4121`, `1209:4121` and Harogic B60 `367F:0200`, with a consistent USB attach intent and permission flow; the E-Cal is recognized as `1209:4122` (LibreCAL / ECB-60).

## Installation

Prebuilt release APK (in the repository): [dist/DroidVNA-0.7.2-beta-release.apk](../../dist/DroidVNA-0.7.2-beta-release.apk)

```
dist/DroidVNA-0.7.2-beta-release.apk
size: 13,225,877 bytes
SHA-256: 4A65646620BB4E835480B6D9E4E730540B34C0DACFBA6E9FCB1A6E55C0D59E39
version: 0.7.2-beta (versionCode 11)
```

```bash
adb install -r dist/DroidVNA-0.7.2-beta-release.apk
```

You can also download the same version from the repository's **Releases** page. The APK is a release build signed with the project release key (certificate CN=Houran CHEN); re-signing with a different keystore prevents in-place upgrades, so uninstall first. See [Releasing](RELEASING.md) for the signing flow.

If Xiaomi/Redmi/HyperOS reports `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`, use the shell installation method or enable **Install via USB** in Developer options; see [Troubleshooting](TROUBLESHOOTING.md) for the exact commands.

Requirements:

- Android 8.0 (API 26) or newer
- A tablet or phone with USB Host / OTG support
- A USB-C cable / OTG adapter that supports data transfer
- LibreVNA / Harogic B60 firmware protocol version 14 (upgrade older firmware with the desktop LibreVNA-GUI first)

## Connecting a USB VNA

1. Connect the LibreVNA or Harogic B60 to the tablet/phone with an OTG adapter and power the device.
   - Harogic B60 / VNB-60 appears as **B60** in the system with VID:PID `367F:0200`.
2. Open the app; when the top status says **OFFLINE**, tap it to open the Device page.
3. Tap **Connect** under "USB devices".
4. Allow access in the system USB permission dialog.
5. When connected, the status becomes **USB** and the Device page shows hardware, firmware, temperature, lock status, and the live sweep rate.

> The LibreVNA / B60 USB power budget is about 500 mA. Some phones limit current; a USB-C hub with external power is recommended. The Device page's "USB diagnostics" lists every USB device enumerated by the system and its authorization state, which helps diagnose OTG/cable issues.

**Harogic B60 on real hardware (401 points / 75 kHz IFBW, port open):**

![Harogic B60](../screenshots/b60.png)

### Harogic B60 extTrigger residue (fixed)

The VNB-60's `SweepSettings` (FW 1.8.6, HW 0xD0) has one byte more than an upstream LibreVNA: byte 32 is `extTrigger` (`0` = free-running continuous sweep, non-zero = wait for that many trigger events per point). The firmware copies received packets into a **shared, reused** receive buffer and only overwrites the bytes actually received, so a 31-byte packet leaves `extTrigger` holding whatever a previous packet put there. With a non-zero residue the firmware believes it must wait for external triggers: residue `0` sweeps normally, `1` leaves about 11 points, `0xFF` returns no data at all — which looks exactly like a dead device.

The desktop app and earlier versions of this app "worked around" it by switching to SA mode and back to VNA, but that only helped because the mode switch happened to overwrite that area of the buffer, which is why it failed intermittently (a random "success, success, failure" cycle that re-sending the settings did not fix). The app now **always sends the complete 32-byte `SweepSettings`** with `extTrigger` explicitly zero, so nothing depends on buffer history; the SA reset logic has been removed and configuring a B60 no longer includes a pointless mode switch and its delays.

If a configuration is still ACKed without producing data, the app re-sends the very same configuration after 6 seconds (up to 2 times) and only then suggests replugging the device in Device → Log. That recovery path only re-sends the sweep configuration; it never switches modes.

- **External triggering is not implemented yet**: the protocol structures already reserve the `extTrigger` byte and the `SetTrigger` (28) / `ClearTrigger` (29) packet numbers, but Harogic has not defined what non-zero values mean (255 produced no output at all), so the app always sends 0.
- The B60 defaults to **75 kHz IFBW** (spec range **15 Hz – 75 kHz**) and **401 points**; a 75 kHz continuous sweep measures about **19–20 sweeps/s**. The B60 DeviceInfo misreports 11 Hz – 93.5 kHz; the app overrides it with the specification range 15 Hz – 75 kHz.
- Low IFBW (such as 1 kHz, 100 Hz, 15 Hz) significantly increases per-point integration time: both the first frame and the whole frame are slower, but the app draws as it sweeps and shows a breathing indicator next to "Acquisition", so it never looks hung.

## Touch gestures

- **One finger**: tap or drag on the chart for cursor readout; drag near a marker line to move the marker.
- **Two fingers**: pinch to zoom; drag with two fingers to pan.
- **FIT** (top-right of the chart): restore the full span / auto Y-axis.
- **Double-tap**: also resets the view.
- **Markers**: "+ Marker" in the side panel adds a marker at the center of the span; "Peak"/"Valley" search automatically; the × on a marker card deletes it.
- **Analysis**: "Analysis ▾" in the marker row opens bandwidth search / XdB search / DTF; a new analysis replaces the markers created by the previous analysis and leaves manual markers untouched.
- **Pause**: the large button at the top/side pauses or resumes continuous sweeping; "Single" triggers one standby sweep.
- **Y axis**: `Y auto` / `Y manual` in the control row toggles auto and manual scaling; after manual pinch-zoom, one tap restores auto.
- **Traces**: trace-parameter chips in the control row toggle S11/S21/S12/S22; the mode chip switches dB/Phase/VSWR/Smith.
- **Hide controls**: the `⌃` at the end of the control row hides the whole row, equivalent to "Hide trace controls" in the menu; `⌃` on the acquisition card on large screens hides that column.

## UI and settings

### Top-right menu

The measurement page has a single control row: trace/mode/Y-axis selectors on the left (horizontally scrollable), connection status, the collapse button, and the single `⋮` menu on the right. Menu items:

- **Sweep settings**: start/stop frequency, points, IFBW, power, averaging, single/two-port, log sweep, auto continuous sweep.
- **Trace settings**: log frequency axis, Y-axis autoscale switch, current trace summary.
- **Calibration** / **Export** / **Device**: open the calibration wizard, export panel, or Device page.
- **Hide / show trace controls**: equivalent to the `⌃` at the end of the control row.
- **App settings**: see below.

### App settings

- **Interface language**: Follow system / Chinese / English; applied immediately and persisted.
- **Interface → Disable app watermark**: when enabled, the translucent DroidVNA mark is no longer drawn behind the trace chart.
- **Remembered settings → Remember sweep settings**: reuse the frequency range, points, IFBW, power, and averaging on the next launch.
- **Remembered settings → Auto-start sweep after connect**: when off, connecting only sends the settings; tap "Start sweep" manually.
- **Calibration memory → Auto-save and load the last calibration**: shows the saved calibration's source (E-Cal / `.cal` import / manual SOL), ports, THRU count, save time, and device serial; you can "disable calibration" (keep coefficients, show raw data only) or "forget the saved calibration".
- **About**: version, protocol, supported devices, GPL-3.0, and upstream project information.

Settings live in the app's private directory: `filesDir/settings.json` (written via a temporary file plus rename, so a power loss never leaves a half-written file).

**English UI / App settings:**

| English UI | App settings |
| --- | --- |
| ![English UI](../screenshots/measure-en.png) | ![App settings](../screenshots/settings.png) |

### Trace settings

Open it from the end of the chart control row, the gear on the right of the "Trace" card, or the top-right menu. It currently contains:

- **Log frequency axis**: display X on a logarithmic frequency axis with 1-2-5 log ticks; turn it off to return to a linear axis.
- **Y-axis autoscale**: when off, the current Y range is locked, equivalent to double-tapping the chart to restore auto.

Future trace display options will continue to be added to this panel.

![Trace settings](../screenshots/trace-settings.png)

## Export

Open "Export measurement data" from the measurement page or the top save button:

- Touchstone `.s2p`: `# Hz S RI R 50`, columns S11 S21 S12 S22 (re/im per column).
- Touchstone `.s1p`: S11 only.
- CSV: frequency plus dB / phase for each parameter.
- Location: `Download/DroidVNA` (Android 10+, MediaStore); Android 8/9 saves to the app's external directory.
- Share button: send the file through the system share sheet.

File names look like `droidvna_20261107_205533_cal.s2p`. `cal` means the current data has calibration applied; `raw` means uncalibrated.

## Demo mode

Without hardware, use the built-in demo mode: the DUT is a 1.75–2.65 GHz band-pass filter with a realistic systematic error model, so you can walk through sweeping, SOL calibration, markers, and export end-to-end — useful for getting familiar with the UI on a tablet first.

## Known limitations

See [Roadmap and limitations](ROADMAP.md) for the complete list.

## Troubleshooting

Common connection, installation, calibration, and E-Cal issues are covered in [Troubleshooting](TROUBLESHOOTING.md).
