# Release process

> [中文版](../RELEASING.md)

This document describes how to produce a publishable GitHub Release from the current code. Commands use PowerShell; on Linux/macOS replace `\` with `/` and `.\gradlew.bat` with `./gradlew`. The example version below is `0.8.0`; substitute the actual version when releasing.

## 1. Decide the version number

Edit `android/app/build.gradle.kts`:

```kotlin
versionCode = 10         // increment on every release, never go backwards (0.7.0-beta is 9)
versionName = "0.8.0"    // the version shown to users
```

Also update:

- `CHANGELOG.md` with an entry for the version
- create `docs/release-notes/0.8.0.md` from `CHANGELOG.md` (copy the previous version's format), and the matching English version `docs/en/release-notes/0.8.0.md`
- the APK file name, size, and SHA-256 in the [User guide](USER-GUIDE.md) "Installation" section
- the APK file name under `dist/`

## 2. Configure release signing

The APK committed to the repository is signed with the project release key (certificate CN=Houran CHEN), but `keystore.properties` and the key file are not committed, so a fresh clone builds an **unsigned** release APK by default.

To produce a signed APK that can upgrade in place:

```powershell
cd android
Copy-Item keystore.properties.example keystore.properties
# edit keystore.properties with the keystore path and passwords
```

`keystore.properties`, `*.jks`, and `*.keystore` are in `.gitignore` and will not be committed.
**Back up the keystore and password offline**; losing them means you cannot upgrade an installed app in place.

If no release keystore is available, you can still publish a debug APK, but mark it clearly in the release notes as
"debug signed, for testing only".

## 3. Run tests and build

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleRelease
```

Output:

- With signing configured: `android/app/build/outputs/apk/release/app-release.apk`
- Without signing: `android/app/build/outputs/apk/release/app-release-unsigned.apk` (cannot be installed or distributed directly)

## 4. Copy and verify the APK

```powershell
Copy-Item android\app\build\outputs\apk\release\app-release.apk dist\DroidVNA-0.8.0-release.apk
Get-FileHash dist\DroidVNA-0.8.0-release.apk -Algorithm SHA256
(Get-Item dist\DroidVNA-0.8.0-release.apk).Length
```

Write the file name, byte size, and SHA-256 into the [User guide](USER-GUIDE.md) "Installation" section.

> `.gitignore` only allow-lists specific APKs under `dist/`. When releasing a new version, either add the new file name to the
> `!` rules, or force-add it with `git add -f dist/DroidVNA-0.8.0-release.apk`.

## 5. Commit and tag

```powershell
git add -A
git commit -m "Release 0.8.0"
git tag -a v0.8.0 -m "DroidVNA 0.8.0"
git push origin main
git push origin v0.8.0
```

## 6. Create the GitHub Release

On the GitHub repository page choose **Releases → Draft a new release**:

- Tag: `v0.8.0`
- Title: `DroidVNA 0.8.0`
- Description: copy the matching version from `CHANGELOG.md` and add upgrade notes
- Attachments: upload `dist/DroidVNA-0.8.0-release.apk`

Or use the GitHub CLI:

```bash
gh release create v0.8.0 dist/DroidVNA-0.8.0-release.apk \
  --title "DroidVNA 0.8.0" \
  --notes-file docs/release-notes/0.8.0.md
```

## 7. Post-release checks

- Download and install from the Release on a device that never had the app installed.
- Confirm the version in the About/Device page matches the tag.
- With a real VNA, verify connection, continuous sweeping, calibration, and `.s2p` export.
- Confirm the repository does not contain `local.properties`, keystores, `build/`, or handout files.

## 8. Versioning conventions

- `0.x`: public testing; protocol/calibration behavior may still change.
- Patch release: bug fixes, no data-format changes.
- Minor release: new features, backward compatible.
- Major release: incompatible protocol, file-format, or calibration-data changes.
