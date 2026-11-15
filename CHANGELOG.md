# Changelog

All notable changes to **Proxmark5 Android Client** are documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html) with deterministic Android `versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`.

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
- Dual wireless transport over BLE GATT (`0xAE86` service / `0xAE88` data characteristic, MTU 256/512) and Wi-Fi TCP (`:18888`) speaking `PM3a` / `PM3b` Next-Gen (`NG`) frames with `CRC-16/X-25`.
- Adaptive UI supporting both compact phone / cover screens (`ScrollableTabRow`) and unfolded foldable / tablet displays (`NavigationRail` + 2-column workspace).
- Power & Smart Antenna dashboard (`BQ27427` fuel gauge + `AW32001E` charger telemetry via `0x0184`, swappable antenna `I2C 0x51` register `0x02` via `0x0170`/`0x0171`, WS2812 RGB via `0x0178`, resonance sweep via `0x0400`, and HW QC self-test via `0x0177`).
- LF Badge Studio & Cloner (`HID Prox`, `EM4100/EM410x`, `AWID`, `IO Prox` watch modes + `EM410x` cloning to `T5577`, `Q5`, `EM4305`).
- HF NFC Studio (`CMD_HF_ISO14443A_READER` `0x0385` UID, `ATQA`, `SAK`, `ATS` reader & classifier).
- T55xx Suite, Local JSON Credential Vault, and live `Dbprintf` (`0x0100`) protocol console.
