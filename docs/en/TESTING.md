# Testing and validation

> [中文版](../TESTING.md)

This document collects unit test coverage, hardware validation records, and the acceptance procedure for full SOLT.

Build and test commands are in [Building and development](DEVELOPMENT.md).

## Unit tests

```bash
cd android
./gradlew testDebugUnitTest
```

67 JVM unit tests cover: CRC/frame coding, SweepSettings layout, Datapoint parsing, single/dual stage reassembly, incremental snapshots for slow sweeps, ring-buffer merging, SOL math and delay standards, full 12-term SOLT inverse solving / DUT round-trip correction / isolation subtraction / degenerate-standard rejection, **the forward model matching an independent signal-flow derivation point by point (1e-12)**, standards returning to ideal values after calibration, exact isolation replacement, calibrated-band coverage checks, demo-mode end-to-end SOLT, `.cal` parsing/rebuilding (including a purely parametric calkit and full SOLT), E-Cal coefficient ordering, Touchstone formats, the English UI layer (labels, assembled messages, importer warnings, and Chinese mode returning input unchanged), bandwidth/XdB search (both/one side, edge interpolation, unfinished sweeps, invalid X values), and DTF (synthetic reflection distances, velocity-factor scaling, log-sweep resampling, peak threshold, display-distance truncation, 0 m wrap guard). A robustness group also covers: standard models at 0 Hz, single-point calibration rejection, truncated calibration snapshots not going out of bounds, rejection when standards are too close, and rejection of full SOLT when THRU does not cover the SOL band in a `.cal`.

## Manual validation on this machine

Manual validation (Xiaomi Pad, Android 17):

- Demo mode continuous sweep, pause/single, peak/valley markers, dragging markers
- dB / phase / VSWR / Smith switching, single-finger readout, pinch zoom
- Open/Short/Load capture → SOL computation → S11/S21 change after calibration
- THRU capture and normalization
- `.s2p` saved to `Download/DroidVNA` with 9 columns verified
- Calibration persists across app restarts
- Chinese / English switching takes effect immediately and survives force-stop and reinstall
- Language and About in App settings, plus the log frequency axis switch in trace settings, open correctly
- The log frequency axis shows 500M / 1G / 2G / 3G ticks over 500 MHz – 3 GHz; Y auto and Y manual toggle both ways

### 0.7.1-beta analysis tools (demo mode)

- **Bandwidth search** (1.75–2.65 GHz demo band-pass): reference peak 2.3125 GHz, edges 1.8157 / 2.5889 GHz, two edge markers added; a second search replaced the previous analysis markers.
- **Left-only XdB search**: only the left edge was added, and the sheet reported "only the left edge was found".
- **DTF** (S11, Hann window, VF 0.66, 4× zero-padding): about 15.7 m maximum distance, 4.0 cm range resolution, 2048-point FFT; the distance trace, fault table, and 0 m wrap note all worked.
- The analysis menu, sheets, and result text were fully translated in both Chinese and English.
- **Not yet verified**: DTF distance accuracy against a known-length cable / fault; bandwidth and XdB search on a real VNA.

## Harogic B60 hardware validation

Harogic B60 hardware validation (FW 1.8.6 / HW 0xD0, Android 17 tablet, wireless adb):

- USB permission dialog, DeviceInfo, and DeviceStatus work
- After the automatic SA → VNA reset, a 401-point / 75 kHz IFBW continuous sweep runs at about 19–20 sweeps/s
- **15 Hz / 101 points** continuous and single sweeps both start and draw progressively: the first frame takes tens of seconds, but arrived points are drawn immediately (e.g. `68/101 pts · Acquiring`), and the side panel shows a breathing indicator / "Single sweep"
- **1 kHz / 401 points** verifies ring-buffer updates: after `209/401 pts` the trace still covers the whole band and the right side keeps the previous frame instead of clearing every frame
- S11 / S21 live traces, pause/resume, markers, and chart gestures work
- Calibration is isolated per B60 serial; demo-mode calibration is never applied to a B60
- The watchdog recovers from a failed first configuration (SA reset visible in the log); after connecting or changing settings, stale USB points from the previous sweep are drained so they cannot mislead the watchdog

## E-Cal / `.cal` validation

- `.cal` parsing and rebuilding (1-port SOL + THRU and full 12-term SOLT paths) are covered by JVM unit tests; real `.cal` files exported by VNStudio were also verified:
  - `ec_2p_solt_10m6g_5dbm_1khz_...cal` (ECB-60 E-Cal, Touchstone coefficients): 2-port SOL + THRU, 0 warnings
  - `default_calbrate_<serial>.cal` (MCB-60M manual kit, purely parametric calkit): 2-port SOL + THRU, 0 warnings
- LibreCAL / ECB-60 USB permission, IDN, 2 ports, temperature/stability, FACTORY 7 coefficient sets, and the automatic flow were verified on an ECB-60 (FW 0.3.2, serial redacted); however, calibration results up to 0.4.0 showed Smith-chart spurs. 0.4.1 corrected the measurement sequencing with LibreVNA-GUI's **all-port standard switching** and added `:PORT?` state checks and coefficient-stream integrity checks. `.cal` import was also verified on the tablet with a VNStudio file.
- **0.7.0-beta full 12-term SOLT hardware results** (2026-11-10, B60 FW 1.8.6 + ECB-60 FW 0.3.2, Android 17 tablet):
  - The E-Cal automatic flow solved and applied the full 12-term model at **401 points / 500 MHz – 3 GHz**; the measurement page showed
    `Acquiring CAL`, and the calibration card showed "applied · 12-term SOLT · P1 SOL · P2 SOL", with no crashes.
  - Both isolation checkbox settings ran: on → "E-Cal complete: … isolation measured", off → "… isolation not measured" (off by default).
  - Physical plausibility after calibration: with the E-Cal set to NONE (equivalent to open), S11 ≈ 0 dB (|Γ|≈1) and S21 sits in the −80…−100 dB noise floor.
  - The "sweep range covers an uncalibrated band" warning appeared on hardware as expected.
  - **Still unverified**: amplitude/phase accuracy against the desktop app / a known DUT; the complete manual SOLT flow (no mechanical standards available).

LibreVNA's own hardware has not been tested yet; the USB protocol path is validated on the B60.

## Full SOLT hardware acceptance procedure

`SoltTest` / `DemoVnaSoltTest` can only prove that the algorithm and flow are self-consistent (the forward model and correction are inverses); real-hardware validation still needs the steps below, using the **desktop LibreVNA-GUI** as the reference:

1. With the same VNA, the same standards, and the same sweep settings, run a two-port SOLT (with isolation) on the desktop app and export the `.cal`.
2. Redo a manual SOLT on the tablet with the same standards, or import that `.cal` directly.
3. Connect a **known component with significant reflection on both ports** (for example a poorly matched attenuator or a line with known return loss) and compare S21 across three results:
   uncalibrated / THRU normalized / full SOLT.
4. Criterion: the full SOLT's S21, S11, and S22 should agree with the desktop app's measurement of the same hardware within noise;
   THRU-normalized S21 should show a systematic deviation where port mismatch is significant, while SOLT should not.
5. If S21 jumps or shows spurs, first check that this SOLT really used the correct THRU and isolation data
   (the device log prints `12-term SOLT applied (... isolation measured/not measured)`).

## Pre-submit self-test

See [Contributing](../../CONTRIBUTING.en.md): both `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` must pass; changes to USB / sweep / calibration logic should be exercised on real hardware (or in demo mode).
