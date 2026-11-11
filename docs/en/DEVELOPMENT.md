# Building and development

> [中文版](../DEVELOPMENT.md)

This document explains how to build DroidVNA from source, the project layout, and USB protocol implementation notes.

- Publishing a new version: see [Releasing](RELEASING.md)
- Tests and hardware validation: see [Testing](TESTING.md)
- Contribution requirements: see [Contributing](../../CONTRIBUTING.en.md)

## Environment

- JDK **17 or 21** (our builds use Corretto 21; JDK 21 is recommended — Android Studio's bundled JBR works)
- Android SDK Platform 36, Build-Tools 36.0.0 (Gradle installs Platform 36 automatically on the first build)
- Gradle 8.14.4 (bundled wrapper)

## Build

```bash
cd android
cp local.properties.example local.properties
# set sdk.dir in local.properties to your Android SDK path
export JAVA_HOME=/path/to/jdk-21
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

APK output:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

Windows example:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd android
Copy-Item local.properties.example local.properties
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

## Release signing

The APK in the repository is signed with the project release key. To build a distributable APK:

```bash
cd android
cp keystore.properties.example keystore.properties
# fill in the keystore path, storePassword, keyAlias, and keyPassword
./gradlew assembleRelease
```

`keystore.properties`, `*.jks`, and `*.keystore` are in `.gitignore`. Back up the keystore and password offline; without them you cannot upgrade an installed app in place. Full steps are in [Releasing](RELEASING.md).

## Tech stack

Kotlin 2.4.21, Jetpack Compose (Material 3, BOM 2025.10.01), AGP 8.13.2, Gradle 8.14.4, compileSdk / targetSdk 36, minSdk 26, Apache Commons Math 3.6.1 (complex matrix solves for 12-term SOLT and the `FastFourierTransformer` IFFT used by DTF). There are no native libraries, so the APK installs directly on arm64 / x86_64 devices.

## Continuous integration

[.github/workflows/android.yml](../../.github/workflows/android.yml) runs unit tests and builds a debug APK on every push / PR, uploading the test report and APK artifacts. Tags matching `v*` also trigger a build.

## Project layout

```
android/
  app/src/main/java/com/droidvna/app/
    MainActivity.kt              Activity, USB attach intent
    MainViewModel.kt             UI state, markers, calibration session, export
    LibreVnaApplication.kt       Application-level USB / repository
    usb/
      Protocol.kt                v14 codec: frames, CRC, SweepSettings, DeviceInfo, Datapoint
      LibrevnaUsbLink.kt         USB bulk I/O, Ack/Nack, device log
      UsbVnaSource.kt            USB device -> VnaSource
      UsbVnaManager.kt           Enumeration, permission, attach/detach
      SweepAssembler.kt          Reassembles complete sweeps by point/stage
      EcalDevice.kt              LibreCAL / ECB-60 SCPI USB driver, coefficients, port switching
    device/
      VnaSource.kt / DemoVnaSource.kt
      VnaRepository.kt           Connection, sweep control, averaging, calibration, persistence
    model/                       Complex, SweepConfig/Result, SOL and full 12-term SOLT math (Solt.kt), complex matrix adapter (ComplexMatrix.kt), .cal import, E-Cal coefficient parsing
      analysis/                  Bandwidth/XdB search (XdbSearch.kt) and DTF (Dtf.kt)
    ui/                          Compose screens, charts (including the DTF distance-domain chart), bottom sheets, theme
      text/                      Chinese/English copy, translating Text wrapper, language state
      screens/                   Measure / Device / Calibration screens
      sheets/                    Sweep, export, app settings, trace settings, analysis sheet
    export/                      Touchstone/CSV, MediaStore/share
    data/CalibrationStore.kt     JSON persistence
  app/src/test/                  JVM unit tests
docs/
  en/                            English documentation
  screenshots/                   UI screenshots
  release-notes/                 Per-version release notes
  USER-GUIDE.md                  User guide (Chinese)
  CALIBRATION.md                 Calibration guide (Chinese)
  TROUBLESHOOTING.md             Troubleshooting (Chinese)
  DEVELOPMENT.md                 This document (Chinese)
  TESTING.md                     Tests and hardware validation (Chinese)
  ROADMAP.md                     Known limitations and roadmap (Chinese)
  RELEASING.md                   Release process (Chinese)
.github/                         CI, issue / PR templates
dist/                            Prebuilt APKs
```

## USB protocol notes

- USB interface 0 (vendor specific), bulk endpoints:
  - `0x01` OUT data
  - `0x81` IN data
  - `0x82` IN device log
- Frame: `0x5A | uint16 length LE | uint8 type | payload | uint32 CRC32`; the VNADatapoint CRC is 0.
- `SweepSettings` is a **32-byte** packed C struct on the VNB-60 (31 bytes upstream) with bitfields in LSB order:
  - standby / syncMaster / suppressPeaks / fixedPower / logSweep / syncMode
  - stages, port1Stage…port4Stage
  - start/stop frequency, points, IFBW, start/stop power, dwell
  - offset 31: `extTrigger` (Harogic extension; `0` = free-running continuous sweep, non-zero = wait
    for N trigger events per point). It **must be sent explicitly**: the firmware decodes into a
    shared, reused receive buffer and only overwrites the bytes actually received, so a 31-byte
    packet lets this byte keep the previous packet's value. A non-zero residue makes the device wait
    for triggers and looks hung (`0` normal, `1` about 11 points, `0xFF` no data at all). See the
    `Protocol.SweepSettings` KDoc.
- External triggering is not implemented yet: `extTrigger` is always 0 and `SetTrigger` (28) /
  `ClearTrigger` (29) only reserve the packet numbers until Harogic defines non-zero values
  (`Protocol.Type`, `Protocol.SweepSettings.extTrigger`).
- A `VNADatapoint` can contain every stage for that point; each stage has P1, P2, and Ref complex values. The descriptor's low 5 bits are the receiver mask (bit0 P1, bit1 P2, bit4 Ref) and the high 3 bits are the stage.
- Device VID/PID: LibreVNA `0483:564E`, `0483:4121`, `1209:4121`; Harogic B60 `367F:0200` (product string `B60`); LibreCAL / ECB-60 E-Cal `1209:4122` (SCPI on interface 2, vendor class 0xFF, bulk 0x03/0x83).
- LibreVNA-GUI's USB VNA driver really only supports LibreVNA (including V1/P2/PT) and Harogic B60; `SNA5000A` and `SSA3000X` are TCP/SCPI instruments, not USB VNAs, and are not implemented here.
