# Contributing

> [中文版](CONTRIBUTING.md)

Thanks for improving DroidVNA. This project targets real hardware, so changes affect not only the UI but also USB timing, calibration accuracy, and device compatibility. Please describe your test environment when submitting.

## What you can contribute

- Bug reports: include the app version, Android version, device model, VNA/E-Cal model and firmware, reproduction steps, and relevant logs (Device page → Log).
- Support for new hardware: LibreVNA V1 / P2 / PT, Harogic B60 / VNB-60, LibreCAL / ECB-60.
- Improvements to calibration algorithms, chart interaction, export formats, documentation, and translations.
- Code copied from closed-source projects is not accepted, nor are data sets containing keys, serial numbers, or other people's private information.

## Development environment

- JDK 17 or 21
- Android SDK Platform 36, Build-Tools 36.0.0
- Gradle 8.14.4 (use the bundled wrapper)
- A device with USB Host / OTG support running Android 8.0+, plus a VNA or demo mode

```bash
cd android
cp local.properties.example local.properties   # point to your SDK
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Windows:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
cd android
Copy-Item local.properties.example local.properties
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

## Pre-submit checks

1. `./gradlew testDebugUnitTest` passes.
2. `./gradlew assembleDebug` produces an APK.
3. If USB / sweep / calibration logic changed, verify connection, sweeping, and calibration on real hardware at least once; without hardware, verify the UI flow in demo mode.
4. Update `CHANGELOG.md` for user-visible changes; when behavior, limits, or screenshots change, update `README.md` / `README.en.md` and the matching documents under `docs/` (User guide, Calibration guide, etc.), keeping the Chinese and English versions in sync.
5. Do not commit `local.properties`, `keystore.properties`, `*.jks`, `build/`, `LibreVNA/`, or handout files.

## Code style

- Official Kotlin style (4-space indentation, see `.editorconfig`).
- Keep the Compose UI touch-first: tap targets should be at least 48 dp, and destructive actions need confirmation.
- Avoid blocking the main thread in USB / protocol code; new device behavior needs timeouts and failure messages.
- Calibration math changes need JVM unit tests, preferably with fixed synthetic data.

## Commit messages and PRs

- Use one sentence in Chinese or English describing what changed, for example
  `Fix first-frame timeout after B60 IFBW change`.
- One PR does one thing; UI changes include a screenshot or recording, protocol changes include a log excerpt.
- State the test devices and verification steps in the PR description.

## License

By submitting code you agree to license your contribution under **GPL-3.0** and confirm that you have the right to submit it. See `LICENSE` and `NOTICE`.
