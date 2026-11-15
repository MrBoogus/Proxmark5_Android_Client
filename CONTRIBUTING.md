# Contributing to Proxmark5 Android Client

Contributions, bug reports, protocol fixes, and hardware test results are welcome.

## Scope & Project Philosophy

This project is a **native Kotlin / Jetpack Compose companion studio for the Proxmark5** over its ESP32-C2 Battery / Wireless Module (BLE GATT `0xAE86`/`0xAE88` and Wi-Fi TCP `:18888`).

- **In scope:** Native mobile workflows for Proxmark5 hardware telemetry (`BQ27427` fuel gauge, `AW32001E` charger), swappable smart antenna (`I2C 0x51`), RGB LED (`I2C 0x48`), LF badge reading/cloning, T55xx block tools, ISO 14443-A / HF card inspection, local credential vault, and transport reliability.
- **Out of scope:** Embedding the full desktop `pm3` CLI or competing with [`RfidResearchGroup/ProxmarkStudio`](https://github.com/RfidResearchGroup/ProxmarkStudio).

---

## Development Setup

1. **Prerequisites:**
   - JDK 17 (or JDK 21)
   - Android SDK (`compileSdk 34`, `minSdk 26`)
   - GCC (`gcc` C11 compiler, optional for running the upstream C header wire-contract verifier)

2. **Build & Run Unit Tests:**
   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```

3. **Verify Wire Protocol Contract Against Upstream `proxmark3`:**
   Whenever you add or modify an opcode in `Pm5Constants.kt` or a wire struct parser in `app/src/main/java/com/proxmark5/client/protocol/`, run:
   ```bash
   python3 .github/scripts/verify_upstream_contract.py \
     --proxmark3-dir /path/to/proxmark3 \
     --self-test
   ```
   This compiles `178+` C11 `_Static_assert` checks against `include/pm3_cmd.h`, `include/mifare.h`, and `armsrc/appmain.c`.

---

## Submitting Pull Requests

1. Fork the repository and create a focused branch (`fix/...` or `feat/...`).
2. Keep wire protocol structs in `protocol/` strictly aligned with `include/pm3_cmd.h` and `armsrc/appmain.c` in [`RfidResearchGroup/proxmark3`](https://github.com/RfidResearchGroup/proxmark3).
3. Add or update JUnit tests in `app/src/test/java/com/proxmark5/client/protocol/ProtocolUnitTest.kt` for any parser or encoder change.
4. Do not commit personal credential dumps, real access badge UIDs, or local machine paths (`local.properties`, `keystore.properties`, `*.jks`).
5. By contributing, you agree that your contributions will be licensed under the **GNU General Public License v3.0 (GPL-3.0)** matching the repository license.
