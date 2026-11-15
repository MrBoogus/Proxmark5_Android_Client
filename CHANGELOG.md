# Changelog

All notable changes to **Proxmark5 Android Client** are documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html) with deterministic Android `versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`.

## [1.0.3] - 2026-11-15 (`versionCode 10003`)

### Security
- **Permission-Gated Debug Receiver (`MainActivity.kt`):** Initialized `debugReceiver` only when `BuildConfig.DEBUG` is `true` (`null` in release builds, stripping all automation hooks from release DEX) and registered it in debug builds with `"android.permission.DUMP"` and `ContextCompat.RECEIVER_EXPORTED` so `adb shell` and system callers work without exposing hooks to third-party apps.
- **Demo Script Configuration (`scripts/capture_demo.sh`):** Removed hardcoded ADB device serial and display IDs, requiring `ADB_SERIAL`, `COVER_DISPLAY`, and `INNER_DISPLAY` via environment variables.

### Changed & Build Toolchain
- **AGP 9.5.1 / Gradle 9.9.1 & Library Upgrades (`build.gradle.kts`, `app/build.gradle.kts`, `gradle-wrapper.properties`):** Upgraded Gradle Wrapper (`8.11.1` → `9.9.1` with `distributionSha256Sum`), Android Gradle Plugin (`8.7.3` → `9.5.1` with built-in Kotlin), Kotlin Compose compiler plugin (`2.0.21` → `2.4.21`), `compileSdk` (`35` → `37`, retaining `minSdk = 26` and `targetSdk = 35`), `androidx.compose:compose-bom` (`2026.09.00`), `androidx.core:core-ktx` (`1.19.1`), `androidx.lifecycle` (`2.11.0`), `androidx.activity:activity-compose` (`1.13.0`), `androidx.window:window` (`1.5.1`), and `kotlinx-coroutines-android` (`1.11.0`).

### Localization
- **ViewModel String Externalization (`Pm5ViewModel.kt`, `strings.xml`):** Moved all `Pm5ViewModel` status banners, operation statuses, HF format descriptions, and vault export error messages into `strings.xml`, including `<plurals>` resources `status_testing_passwords` and `status_vault_exported`.

---

## [1.0.2] - 2026-11-15 (`versionCode 10002`)

### Security
- **Native `AndroidKeyStore` AES-256-GCM Vault (`CapturedTag.kt`):** Replaced deprecated `androidx.security.crypto.EncryptedFile` / `MasterKeys` (`security-crypto 1.0.0`) with direct `AndroidKeyStore` AES-256-GCM encryption (`pm5_vault_aes256_gcm_key`, `PM5V2\0` header bound as AAD), preserving automatic one-time migration from `v1.0.1` Tink `AES256_GCM_HKDF_4KB` vaults and `v1.0.0` plaintext JSON vaults.
- **Non-Destructive Vault Error Preservation & Atomic Writes (`CapturedTag.kt`):** Decrypt/corrupt vault errors now return `VaultResult.Error` instead of wiping to an empty list, write updates atomically via `.tmp` + `fsync` + `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`, and execute all vault I/O off the main thread on `Dispatchers.IO`.
- **Safe Vault Export & Sensitive Clipboard (`Pm5AppScreen.kt`, `Pm5ViewModel.kt`):** Replaced the Tag Vault clipboard dump with Android Storage Access Framework `ActivityResultContracts.CreateDocument("application/json")` file export, and tagged UID/card ID clipboard copies with `ClipDescription.EXTRA_IS_SENSITIVE`.
- **Lock-Screen Hardening & Lifecycle Cleanup (`MainActivity.kt`, `Pm5ViewModel.kt`, `Pm5BleManager.kt`):** Removed `setShowWhenLocked` / `setTurnScreenOn`, scoped `FLAG_KEEP_SCREEN_ON` strictly to active hardware operations, and added `Pm5ViewModel.onCleared()` to stop scans/polling and close BLE/TCP connections on exit.

### Changed & Build Hardening
- **Android 15 Target & Edge-to-Edge (`app/build.gradle.kts`, `Theme.kt`, `Pm5AppScreen.kt`):** Raised `compileSdk` and `targetSdk` to `35` with `enableEdgeToEdge()` and `safeDrawing` window insets, upgraded Gradle (`8.11.1` + `distributionSha256Sum`), AGP (`8.7.3`), Kotlin (`2.0.21` Compose compiler plugin), enabled R8 minification + resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), and disabled `dependenciesInfo` for F-Droid reproducibility.
- **UI String Externalization & Accessibility (`strings.xml`, `Pm5AppScreen.kt`):** Moved hardcoded UI strings into `strings.xml` and added meaningful `contentDescription` labels across action icons.
- **GitHub Actions & Supply-Chain Hardening (`.github/workflows/`, `.github/dependabot.yml`):** Pinned workflow runners to `ubuntu-24.04` and actions to immutable commit SHAs with `persist-credentials: false`, added `wrapper-validation` and `concurrency` groups, restricted keystore decode/release signing strictly to `refs/tags/v*`, scoped `GH_TOKEN` / `issues: write` in `watch-upstream.yml`, and added weekly Dependabot updates.

### Fixed
- **Documentation & Demo Script Accuracy (`README.md`, `fastlane`, `scripts/`):** Corrected BLE GATT service UUID (`0xAE86`), Wi-Fi TCP port (`18888`), `CRC-16/ISO14443-A` specification (`poly=0x1021, init=0xC6C6, refin=true, refout=true, xorout=0x0000`), debug broadcast `-p com.proxmark5.client.debug` targeting in `capture_demo.sh`, and test device model (`Samsung Galaxy Z Fold 7`).

---

## [1.0.1] - 2026-11-15 (`versionCode 10001`)

### Fixed
- **T55xx Wire Struct Alignment (`T55xxModels.kt`):** Aligned `CMD_LF_T55XX_READBL` (`0x0214`) request builder with the 8-byte `struct p` in `armsrc/appmain.c` (`password`, `blockno`, `page`, `pwdmode`, `downlink_mode`) and `CMD_LF_T55XX_WRITEBL` (`0x0215`) request builder with the 10-byte `t55xx_write_block_t` in `include/pm3_cmd.h` (`data`, `pwd`, `blockno`, `flags`).
- **BWM Telemetry Payload Size Guards (`Pm5BatteryModels.kt`):** Updated `Pm5BatteryInfo.fromBytes` size check to `19` bytes (`sizeof(bwm_battery_info_t)`) and `Pm5AutoOffStatus.fromBytes` size check to `15` bytes (`sizeof(bwm_autooff_status_t)`).
- **BLE Disconnect & Permission Resilience (`Pm5BleManager.kt`, `Pm5ViewModel.kt`, `MainActivity.kt`):** Added `BluetoothGatt.close()` cleanup on unexpected BLE disconnect, automatic cancellation of in-flight UI jobs on disconnect, and graceful `SecurityException` handling when Android 12+ `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` permissions are denied or pending.
- **Documentation Accuracy (`README.md`):** Corrected BLE GATT characteristic documentation (`0xAE88` single bidirectional data characteristic under service `0xAE86`) and opcode tables (`0x0184` `CMD_PM5_BWM_GET_BATTERY`, `0x017D` `CMD_PM5_BWM_SET_VCHG`, `0x0118` `CMD_BREAK_LOOP`).

### Added
- **Unit Test Suite (`ProtocolUnitTest.kt`):** Added JUnit 4 unit tests covering `Pm5Crc16`, `Pm5CommandFrameBuilder`, `Pm5FrameParser` chunk reassembly, `Pm5BatteryInfo`, `Pm5AutoOffStatus`, `Pm5AntennaConfig`, `BadgeParser`, `Em4100Decoder`, `HfCardDecoder`, and `T55xxPayloadBuilder`.
- **C11 ABI Upstream Protocol Watcher (`.github/workflows/watch-upstream.yml`):** Added automated `gcc -std=c11` `_Static_assert` wire-contract verifier checking `49` opcodes, `23` status codes, and `18` wire structs against `RfidResearchGroup/proxmark3`.
- **Repository & Store Metadata:** Added `LICENSE` (`GPL-3.0`), `CONTRIBUTING.md`, `SECURITY.md`, GitHub Issue templates, F-Droid / IzzyOnDroid Fastlane metadata (`fastlane/metadata/android/en-US/`), and automated live hardware demo recordings (`docs/media/`).

---

## [1.0.0] - 2026-11-15 (`versionCode 10000`)

### Added
- Initial public release of the native Kotlin / Jetpack Compose Android companion app for the Proxmark5 (`AT32F435` + Gowin FPGA + ESP32-C2 BWM).
- Dual wireless transport over BLE GATT (`0xAE86` service / `0xAE88` data characteristic, MTU 256/512) and Wi-Fi TCP (`:18888`) speaking `PM3a` / `PM3b` Next-Gen (`NG`) frames with `CRC-16/ISO14443-A`.
- Adaptive UI supporting both compact phone / cover screens (`ScrollableTabRow`) and unfolded foldable / tablet displays (`NavigationRail` + 2-column workspace).
- Power & Smart Antenna dashboard (`BQ27427` fuel gauge + `AW32001E` charger telemetry via `0x0184`, swappable antenna `I2C 0x51` register `0x02` via `0x0170`/`0x0171`, WS2812 RGB via `0x0178`, resonance sweep via `0x0400`, and HW QC self-test via `0x0177`).
- LF Badge Studio & Cloner (`HID Prox`, `EM4100/EM410x`, `AWID`, `IO Prox` watch modes + `EM410x` cloning to `T5577`, `Q5`, `EM4305`).
- HF NFC Studio (`CMD_HF_ISO14443A_READER` `0x0385` UID, `ATQA`, `SAK`, `ATS` reader & classifier).
- T55xx Suite, Local JSON Credential Vault, and live `Dbprintf` (`0x0100`) protocol console.
