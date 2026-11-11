# Roadmap and limitations

> [中文版](../ROADMAP.md)

This document records DroidVNA's current boundaries and future direction. For practical problems, start with [Troubleshooting](TROUBLESHOOTING.md).

## Known limitations

- No firmware update, manual control, signal generator, spectrum analysis, amplitude/power calibration, frequency calibration, or SCPI server.
- Full 12-term SOLT supports two ports (two ports already covers all 12 terms); three/four-port extensions are not implemented. Manual THRU is treated as an ideal through; for a non-ideal through, enter its delay or use E-Cal / `.cal`.
- Two-port electronic kits (such as the ECB-60) have poor isolation paths, so the E-Cal's built-in isolation measurement is off by default; when isolation correction is needed, measure it separately with two mechanical 50 Ω loads (the Calibration page can replace just the isolation term).
- There is no standalone Touchstone calibration-kit file import; `.cal` import can rebuild a full SOLT, but a 1-port `.cal` still yields only 1-port SOL + THRU.
- E-Cal uses a fixed VNA P1/P2 ↔ Cal P1/P2 mapping with no automatic port detection; an externally powered OTG hub is required.
- The chart is a custom Compose Canvas and is not optimized for very large point counts (>1001).
- DTF uses band-pass mode (no DC data); the velocity factor and cable loss are entered by the user. Distance accuracy has not been calibrated against a known-length cable / fault, and velocity-factor estimation is not automatic.
- Bandwidth / XdB search works on the current trace snapshot and is bounded by the sweep span; it does not expand the sweep automatically.
- The prebuilt APK in the repository is a release build signed with the project release key; for official distribution, rebuild with your own release keystore (see [Releasing](RELEASING.md)).
- Try demo mode first on a phone to confirm the layout and controls.
- English translations cover the main UI and common messages; a few low-level device/protocol log lines may remain in Chinese.
- LibreVNA's own hardware has not been tested yet; full SOLT has not been compared against the desktop app / a known DUT for absolute accuracy, and the complete manual SOLT flow has not been validated on hardware.

## Validation status

The reasons the current version is marked **beta**, plus hardware validation records, are in [Testing](TESTING.md) and the [0.7.2-beta release notes](release-notes/0.7.2-beta.md).

## Next steps

- Compare amplitude/phase accuracy against the desktop LibreVNA-GUI and a known DUT.
- Validate DTF distance accuracy with a known-length cable / known fault, and evaluate automatic velocity-factor estimation.
- Validate the complete manual SOLT flow with mechanical standards.
- Test LibreVNA's own hardware.
- Calibration-kit file import, three/four-port device support, chart performance for large point counts.
- Broader English coverage (low-level device/protocol log lines).
