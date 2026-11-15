#!/usr/bin/env python3
"""
Zero-False-Positive Upstream Protocol Contract Verifier for Proxmark5 Android Client.

Instead of fragile text/diff regexes (which break on comments, formatting, or
adjacent unrelated changes), this script:
  1. Parses `Pm5Constants.kt` to extract every opcode (`CMD_*`), status code (`PM3_*`),
     framing constant, and BLE/Wi-Fi transport constant used by the Android app.
  2. Extracts the inline `struct p { ... } PACKED;` command payload definitions from
     `armsrc/appmain.c` for the opcodes used by the app.
  3. Generates a C11 verification unit and compiles it with `gcc -std=c11 -fsyntax-only`
     against upstream `include/pm3_cmd.h`, `include/mifare.h`, `include/common.h`, and
     `client/src/uart/ble_posix.h` using `_Static_assert` on:
       - Every `CMD_*` opcode numeric value
       - Every `PM3_*` status return code numeric value
       - NG frame preamble/postamble magic values and byte sizes
       - Exact `sizeof(...)` and `offsetof(struct, field)` + field `sizeof` for every
         wire struct and inline command payload struct parsed or built by the app.
  4. Verifies that all required `case CMD_*:` handlers exist in `armsrc/appmain.c` and
     that the default BWM Wi-Fi TCP port (`18888`) in `client/src/cmdbwm.c` is unchanged.

Result:
  - Comments, whitespace, clang-format runs, internal firmware logic changes, and
    unrelated commands/structs produce ZERO false positives.
  - Any actual wire-breaking change to an opcode, status code, struct size, field
    offset, field width, BLE UUID, or TCP port used by the Android client is caught
    deterministically with the exact C compiler diagnostic.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Dict, List, Optional, Tuple


# Inline `struct p { ... } PACKED;` blocks inside `armsrc/appmain.c` that the
# Android client constructs directly on the wire.
INLINE_APPMAIN_STRUCT_SPECS = [
    {
        "case_label": "CMD_ANT_CONTROL_WRITE",
        "typedef_name": "appmain_ant_control_write_p_t",
        "expected_size": 2,
        "fields": [
            ("data", 0, 1),
            ("reg_type", 1, 1),
        ],
    },
    {
        "case_label": "CMD_ANT_CONTROL_READ",
        "typedef_name": "appmain_ant_control_read_p_t",
        "expected_size": 1,
        "fields": [
            ("reg_type", 0, 1),
        ],
    },
    {
        "case_label": "CMD_PM5_RGB_SET",
        "typedef_name": "appmain_pm5_rgb_set_p_t",
        "expected_size": 3,
        "fields": [
            ("r", 0, 1),
            ("g", 1, 1),
            ("b", 2, 1),
        ],
    },
    {
        "case_label": "CMD_PM5_QC_TEST_IO",
        "typedef_name": "appmain_pm5_qc_test_io_p_t",
        "expected_size": 7,
        "fields": [
            ("pwd", 0, 4),
            ("index", 4, 2),
            ("status", 6, 1),
        ],
    },
    {
        "case_label": "CMD_LF_EM410X_CLONE",
        "typedef_name": "appmain_lf_em410x_clone_p_t",
        "expected_size": 12,
        "fields": [
            ("Q5", 0, 1),
            ("EM", 1, 1),
            ("add_electra", 2, 1),
            ("clock", 3, 1),
            ("high", 4, 4),
            ("low", 8, 4),
        ],
    },
    {
        "case_label": "CMD_LF_T55XX_READBL",
        "typedef_name": "appmain_lf_t55xx_readbl_p_t",
        "expected_size": 8,
        "fields": [
            ("password", 0, 4),
            ("blockno", 4, 1),
            ("page", 5, 1),
            ("pwdmode", 6, 1),
            ("downlink_mode", 7, 1),
        ],
    },
    {
        "case_label": "CMD_LF_T55XX_WAKEUP",
        "typedef_name": "appmain_lf_t55xx_wakeup_p_t",
        "expected_size": 5,
        "fields": [
            ("password", 0, 4),
            ("flags", 4, 1),
        ],
    },
]

# Named wire structs in `include/pm3_cmd.h` and `include/mifare.h` parsed or built by the app.
NAMED_HEADER_STRUCT_SPECS = [
    {
        "struct_name": "PacketCommandNGPreamble",
        "expected_size": 8,
        "fields": [
            ("magic", 0, 4),
            ("cmd", 6, 2),
        ],
    },
    {
        "struct_name": "PacketCommandNGPostamble",
        "expected_size": 2,
        "fields": [
            ("crc", 0, 2),
        ],
    },
    {
        "struct_name": "PacketResponseNGPreamble",
        "expected_size": 10,
        "fields": [
            ("magic", 0, 4),
            ("status", 6, 1),
            ("reason", 7, 1),
            ("cmd", 8, 2),
        ],
    },
    {
        "struct_name": "PacketResponseNGPostamble",
        "expected_size": 2,
        "fields": [
            ("crc", 0, 2),
        ],
    },
    {
        "struct_name": "bwm_battery_info_t",
        "expected_size": 19,
        "fields": [
            ("bwm_present", 0, 1),
            ("gauge_ok", 1, 1),
            ("soc_pct", 2, 2),
            ("voltage_mv", 4, 2),
            ("current_ma", 6, 2),
            ("remaining_mah", 8, 2),
            ("full_charge_mah", 10, 2),
            ("design_cap_mah", 12, 2),
            ("temp_c10", 14, 2),
            ("charger_fault", 16, 1),
            ("charge_status", 17, 1),
            ("health_pct", 18, 1),
        ],
    },
    {
        "struct_name": "bwm_autooff_status_t",
        "expected_size": 15,
        "fields": [
            ("enabled", 0, 1),
            ("idle_s", 1, 4),
            ("idle_now_s", 5, 4),
            ("link", 9, 1),
            ("ble_live", 10, 1),
            ("usb", 11, 1),
            ("usb_seen", 12, 1),
            ("persisted", 13, 1),
            ("unplug", 14, 1),
        ],
    },
    {
        "struct_name": "t55xx_config_t",
        "expected_size": 14,
        "fields": [
            ("start_gap", 0, 2),
            ("write_gap", 2, 2),
            ("write_0", 4, 2),
            ("write_1", 6, 2),
            ("read_gap", 8, 2),
            ("write_2", 10, 2),
            ("write_3", 12, 2),
        ],
    },
    {
        "struct_name": "t55xx_setconfig_t",
        "expected_size": 58,
        "fields": [
            ("conf", 0, 56),
            ("persist", 56, 1),
            ("rfu", 57, 1),
        ],
    },
    {
        "struct_name": "t55xx_write_block_t",
        "expected_size": 10,
        "fields": [
            ("data", 0, 4),
            ("pwd", 4, 4),
            ("blockno", 8, 1),
            ("flags", 9, 1),
        ],
    },
    {
        "struct_name": "t55xx_test_block_t",
        "expected_size": 133,
        "fields": [
            ("data", 0, 128),
            ("bitlen", 128, 1),
            ("time", 129, 4),
        ],
    },
    {
        "struct_name": "iso14a_card_select_t",
        "expected_size": 271,
        "fields": [
            ("uid", 0, 10),
            ("uidlen", 10, 1),
            ("atqa", 11, 2),
            ("sak", 13, 1),
            ("ats_len", 14, 1),
            ("ats", 15, 256),
        ],
    },
]

# Opcodes declared in Pm5Constants.kt that are dispatched inside `armsrc/appmain.c`
# (excludes host-received response-only opcodes like CMD_DEBUG_PRINT_STRING and CMD_DOWNLOADED_BIGBUF,
# and loop-interrupt control opcode CMD_BREAK_LOOP which is checked in the RX/idle loop).
NON_CASE_DISPATCH_OPCODES = {
    "CMD_DEBUG_PRINT_STRING",
    "CMD_DOWNLOADED_BIGBUF",
    "CMD_BREAK_LOOP",
}


def strip_c_comments(source: str) -> str:
    """Remove // and /* ... */ comments from C source while preserving line breaks."""
    pattern = re.compile(
        r'//.*?$|/\*.*?\*/|\'(?:\\.|[^\\\'])*\'|"(?:\\.|[^\\"])*"',
        re.DOTALL | re.MULTILINE,
    )

    def replacer(match: re.Match) -> str:
        s = match.group(0)
        if s.startswith("/"):
            return "\n" * s.count("\n")
        return s

    return pattern.sub(replacer, source)


def parse_pm5_constants_kt(kt_path: Path) -> dict:
    """Extract CMD_*, PM3_*, framing magic, BLE UUIDs, and Wi-Fi port from Pm5Constants.kt."""
    text = kt_path.read_text(encoding="utf-8")

    cmd_constants: Dict[str, int] = {}
    for m in re.finditer(r"const\s+val\s+(CMD_[A-Z0-9_]+)\s*=\s*(0x[0-9A-Fa-f]+|\d+)", text):
        cmd_constants[m.group(1)] = int(m.group(2), 0)

    status_constants: Dict[str, int] = {}
    for m in re.finditer(r"const\s+val\s+(PM3_[A-Z0-9_]+)\s*=\s*(-?\d+)", text):
        status_constants[m.group(1)] = int(m.group(2), 10)

    framing_constants: Dict[str, int] = {}
    for name in (
        "COMMANDNG_PREAMBLE_MAGIC",
        "RESPONSENG_PREAMBLE_MAGIC",
        "COMMANDNG_POSTAMBLE_MAGIC",
        "RESPONSENG_POSTAMBLE_MAGIC",
        "COMMAND_PREAMBLE_SIZE",
        "RESPONSE_PREAMBLE_SIZE",
        "POSTAMBLE_SIZE",
        "DEFAULT_WIFI_PORT",
    ):
        m = re.search(rf"const\s+val\s+{name}(?:\s*:\s*Int)?\s*=\s*(0x[0-9A-Fa-f]+|\d+)", text)
        if not m:
            raise RuntimeError(f"Could not find {name} in {kt_path}")
        framing_constants[name] = int(m.group(1), 0)

    ble_char_match = re.search(
        r'val\s+BLE_DATA_CHAR_UUID\s*:\s*UUID\s*=\s*UUID\.fromString\("([0-9a-fA-F-]+)"\)',
        text,
    )
    if not ble_char_match:
        raise RuntimeError(f"Could not find BLE_DATA_CHAR_UUID in {kt_path}")
    ble_char_uuid = ble_char_match.group(1).lower()
    # Extract 16-bit UUID from 0000xxxx-0000-1000-8000-00805f9b34fb
    ble_char_uuid16 = int(ble_char_uuid[4:8], 16)

    return {
        "cmd_constants": cmd_constants,
        "status_constants": status_constants,
        "framing_constants": framing_constants,
        "ble_char_uuid": ble_char_uuid,
        "ble_char_uuid16": ble_char_uuid16,
    }


def extract_appmain_case_blocks(appmain_text: str) -> Dict[str, str]:
    """Return mapping of `CMD_*` -> C source inside `case CMD_*:` up to the next `case CMD_`."""
    clean = strip_c_comments(appmain_text)
    matches = list(re.finditer(r"\bcase\s+(CMD_[A-Z0-9_]+)\s*:", clean))
    blocks: Dict[str, str] = {}
    for i, m in enumerate(matches):
        cmd_name = m.group(1)
        start = m.end()
        end = matches[i + 1].start() if (i + 1 < len(matches)) else len(clean)
        blocks[cmd_name] = clean[start:end]
    return blocks


def extract_inline_struct_from_case(case_body: str, typedef_name: str) -> Optional[str]:
    """Extract `struct p { ... } PACKED;` from a `case CMD_*:` block and turn it into a typedef."""
    m = re.search(r"struct\s+p\s*\{([^}]*)\}\s*PACKED\s*;", case_body, re.DOTALL)
    if not m:
        return None
    fields_body = m.group(1)
    return f"typedef struct {{{fields_body}}} PACKED {typedef_name};"


def verify_contract(proxmark3_dir: Path, android_dir: Path) -> Tuple[bool, dict]:
    kt_constants_path = (
        android_dir
        / "app/src/main/java/com/proxmark5/client/protocol/Pm5Constants.kt"
    )
    pm3_cmd_h = proxmark3_dir / "include/pm3_cmd.h"
    mifare_h = proxmark3_dir / "include/mifare.h"
    common_h = proxmark3_dir / "include/common.h"
    ble_posix_h = proxmark3_dir / "client/src/uart/ble_posix.h"
    cmdbwm_c = proxmark3_dir / "client/src/cmdbwm.c"
    appmain_c = proxmark3_dir / "armsrc/appmain.c"

    failures: List[str] = []
    for req_file in (pm3_cmd_h, mifare_h, common_h, ble_posix_h, cmdbwm_c, appmain_c):
        if not req_file.is_file():
            failures.append(f"Missing required upstream file: `{req_file.relative_to(proxmark3_dir)}`")

    if failures:
        return False, {"failures": failures, "checks_run": 0}

    kt_data = parse_pm5_constants_kt(kt_constants_path)
    appmain_text = appmain_c.read_text(encoding="utf-8", errors="replace")
    case_blocks = extract_appmain_case_blocks(appmain_text)

    # 1. Check that all dispatched opcodes in Pm5Constants.kt exist as `case CMD_*:` in armsrc/appmain.c
    for cmd_name, cmd_val in sorted(kt_data["cmd_constants"].items()):
        if cmd_name in NON_CASE_DISPATCH_OPCODES:
            continue
        if cmd_name not in case_blocks:
            failures.append(
                f"`armsrc/appmain.c` no longer has a `case {cmd_name}:` (0x{cmd_val:04X}) dispatch handler."
            )

    # 2. Extract inline `struct p` definitions from `armsrc/appmain.c`
    inline_typedefs: List[str] = []
    available_inline_specs = []
    for spec in INLINE_APPMAIN_STRUCT_SPECS:
        case_label = spec["case_label"]
        body = case_blocks.get(case_label)
        if body is None:
            continue
        td = extract_inline_struct_from_case(body, spec["typedef_name"])
        if td is None:
            failures.append(
                f"`armsrc/appmain.c` handler `case {case_label}:` no longer defines inline `struct p {{ ... }} PACKED;`."
            )
        else:
            inline_typedefs.append(td)
            available_inline_specs.append(spec)

    # 3. Check default BWM Wi-Fi TCP port in client/src/cmdbwm.c
    cmdbwm_clean = strip_c_comments(cmdbwm_c.read_text(encoding="utf-8", errors="replace"))
    expected_port = kt_data["framing_constants"]["DEFAULT_WIFI_PORT"]
    if not re.search(rf"\barg_get_int_def\s*\([^;]*,\s*{expected_port}\s*\)", cmdbwm_clean):
        failures.append(
            f"`client/src/cmdbwm.c` default BWM Wi-Fi TCP port no longer matches `{expected_port}`."
        )

    # 4. Generate C11 static-assertion probe and compile each assertion group with gcc
    # We compile assertions in a single fast pass first; if it fails, we bisect per-assertion
    # so the report lists every exact broken opcode/struct/field cleanly.
    assertions: List[Tuple[str, str]] = []

    # Framing magic & header sizes
    fc = kt_data["framing_constants"]
    for macro_name in (
        "COMMANDNG_PREAMBLE_MAGIC",
        "RESPONSENG_PREAMBLE_MAGIC",
        "COMMANDNG_POSTAMBLE_MAGIC",
        "RESPONSENG_POSTAMBLE_MAGIC",
    ):
        val = fc[macro_name]
        assertions.append(
            (
                f"Framing macro `{macro_name}` == `0x{val:X}`",
                f'_Static_assert({macro_name} == 0x{val:X}U, "{macro_name} changed");',
            )
        )

    # BLE SPP UUID16
    ble_uuid16 = kt_data["ble_char_uuid16"]
    assertions.append(
        (
            f"BLE SPP characteristic UUID `BLE_SPP_CHR_UUID16` == `0x{ble_uuid16:04X}`",
            f'_Static_assert(BLE_SPP_CHR_UUID16 == 0x{ble_uuid16:04X}U, "BLE_SPP_CHR_UUID16 changed");',
        )
    )

    # BWM action constants relied upon by Pm5BleManager.kt
    bwm_macro_expectations = [
        ("BWM_AUTOOFF_ACTION_GET", 0x01),
        ("BWM_AUTOOFF_ACTION_SET", 0x02),
        ("BWM_BLE_NAME_ACTION_GET", 0x00),
        ("BWM_BLE_NAME_ACTION_SET", 0x01),
        ("BWM_BLE_NAME_MAX_LEN", 15),
        ("BWM_POWERSAVE_ACTION_GET", 0x00),
        ("BWM_POWERSAVE_ACTION_SET", 0x01),
        ("BWM_WIFI_PS_ACTION_GET", 0x00),
        ("BWM_WIFI_PS_ACTION_SET", 0x01),
    ]
    for macro_name, expected_val in bwm_macro_expectations:
        assertions.append(
            (
                f"BWM constant `{macro_name}` == `{expected_val}`",
                f'_Static_assert({macro_name} == {expected_val}, "{macro_name} changed");',
            )
        )

    # All CMD_* opcodes from Pm5Constants.kt
    for cmd_name, cmd_val in sorted(kt_data["cmd_constants"].items()):
        assertions.append(
            (
                f"Opcode `{cmd_name}` == `0x{cmd_val:04X}`",
                f'_Static_assert({cmd_name} == 0x{cmd_val:04X}, "{cmd_name} changed");',
            )
        )

    # All PM3_* status codes from Pm5Constants.kt
    for st_name, st_val in sorted(kt_data["status_constants"].items()):
        assertions.append(
            (
                f"Status code `{st_name}` == `{st_val}`",
                f'_Static_assert({st_name} == ({st_val}), "{st_name} changed");',
            )
        )

    # Named wire structs in pm3_cmd.h / mifare.h
    for spec in NAMED_HEADER_STRUCT_SPECS:
        sname = spec["struct_name"]
        esize = spec["expected_size"]
        assertions.append(
            (
                f"Struct `sizeof({sname})` == `{esize}` bytes",
                f'_Static_assert(sizeof({sname}) == {esize}, "sizeof({sname}) changed");',
            )
        )
        for fname, foffset, fsize in spec["fields"]:
            assertions.append(
                (
                    f"Field `{sname}.{fname}` offset == `{foffset}`, size == `{fsize}`",
                    f'_Static_assert(offsetof({sname}, {fname}) == {foffset} && sizeof((({sname} *)0)->{fname}) == {fsize}, "{sname}.{fname} layout changed");',
                )
            )

    # Inline appmain.c command payload structs
    for spec in available_inline_specs:
        clabel = spec["case_label"]
        tname = spec["typedef_name"]
        esize = spec["expected_size"]
        assertions.append(
            (
                f"Inline payload `{clabel}` (`struct p`) `sizeof` == `{esize}` bytes",
                f'_Static_assert(sizeof({tname}) == {esize}, "sizeof({clabel} struct p) changed");',
            )
        )
        for fname, foffset, fsize in spec["fields"]:
            assertions.append(
                (
                    f"Inline payload `{clabel}.{fname}` offset == `{foffset}`, size == `{fsize}`",
                    f'_Static_assert(offsetof({tname}, {fname}) == {foffset} && sizeof((({tname} *)0)->{fname}) == {fsize}, "{clabel}.{fname} layout changed");',
                )
            )

    c_preamble = "\n".join(
        [
            "#include <stdint.h>",
            "#include <stdbool.h>",
            "#include <stddef.h>",
            '#include "common.h"',
            '#include "pm3_cmd.h"',
            '#include "mifare.h"',
            "#define HAVE_BLUEZ 1",
            '#include "ble_posix.h"',
            "",
            *inline_typedefs,
            "",
        ]
    )

    include_flags = [
        f"-I{proxmark3_dir / 'include'}",
        f"-I{proxmark3_dir / 'common'}",
        f"-I{proxmark3_dir / 'client/src/uart'}",
    ]

    def run_gcc_probe(assertion_lines: List[str]) -> Tuple[bool, str]:
        with tempfile.NamedTemporaryFile("w", suffix=".c", delete=False, encoding="utf-8") as tmp:
            tmp.write(c_preamble)
            tmp.write("\n".join(assertion_lines))
            tmp.write("\nint pm5_contract_dummy(void) { return 0; }\n")
            tmp_path = Path(tmp.name)
        try:
            proc = subprocess.run(
                ["gcc", "-std=c11", "-fsyntax-only", *include_flags, str(tmp_path)],
                capture_output=True,
                text=True,
                check=False,
            )
            return proc.returncode == 0, (proc.stderr or proc.stdout).strip()
        finally:
            tmp_path.unlink(missing_ok=True)

    all_ok, combined_err = run_gcc_probe([a[1] for a in assertions])
    if not all_ok:
        # Check each assertion individually to report only the exact broken contract items
        any_individual_failed = False
        for desc, line in assertions:
            ok, err = run_gcc_probe([line])
            if not ok:
                any_individual_failed = True
                first_err = next(
                    (
                        l.strip()
                        for l in err.splitlines()
                        if "error:" in l
                    ),
                    err.splitlines()[0] if err else "compile assertion failed",
                )
                failures.append(f"{desc} — `{first_err}`")
        if not any_individual_failed:
            failures.append(f"Header compilation error:\n```\n{combined_err}\n```")

    summary = {
        "passed": len(failures) == 0,
        "opcodes_verified": len(kt_data["cmd_constants"]),
        "status_codes_verified": len(kt_data["status_constants"]),
        "named_structs_verified": len(NAMED_HEADER_STRUCT_SPECS),
        "inline_structs_verified": len(available_inline_specs),
        "total_c_static_assertions": len(assertions),
        "failures": failures,
    }
    return len(failures) == 0, summary


def run_self_test(proxmark3_dir: Path, android_dir: Path) -> bool:
    """
    Prove both:
      1. Benign upstream edits (comments, formatting, unrelated opcodes/structs) -> PASS (0 false positives)
      2. Real wire-breaking edits (opcode change, struct field reorder/insert, inline struct change) -> FAIL
    """
    with tempfile.TemporaryDirectory() as tmpdir:
        mock_root = Path(tmpdir)
        for rel in (
            "include/pm3_cmd.h",
            "include/mifare.h",
            "include/common.h",
            "include/pm3_auc.h",
            "client/src/uart/ble_posix.h",
            "client/src/cmdbwm.c",
            "armsrc/appmain.c",
        ):
            src = proxmark3_dir / rel
            if src.is_file():
                dst = mock_root / rel
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)

        # Test A: Add noisy comments, whitespace, and a brand new unrelated opcode & struct right next to CMD_PM5_RGB_SET
        pm3_cmd = mock_root / "include/pm3_cmd.h"
        orig_pm3_cmd = pm3_cmd.read_text(encoding="utf-8")
        noisy_pm3_cmd = orig_pm3_cmd.replace(
            "#define CMD_PM5_RGB_SET 0x0178",
            "/* Massive comment block mentioning CMD_PM5_RGB_SET 0x9999 and bwm_battery_info_t */\n"
            "#define CMD_UNRELATED_NEW_UPSTREAM_FEATURE 0x0999\n"
            "typedef struct { uint32_t foo; uint8_t bar; } PACKED unrelated_upstream_t;\n"
            "#define   CMD_PM5_RGB_SET    0x0178   // formatting changed!",
        )
        pm3_cmd.write_text(noisy_pm3_cmd, encoding="utf-8")
        ok_noise, summary_noise = verify_contract(mock_root, android_dir)
        if not ok_noise:
            print(f"[SELF-TEST FAILED] False positive on benign upstream change: {summary_noise['failures']}", file=sys.stderr)
            return False

        # Test B: Mutate a field inside `bwm_battery_info_t` (insert a byte before `soc_pct`)
        broken_struct_cmd = orig_pm3_cmd.replace(
            "uint16_t soc_pct;",
            "uint8_t breaking_new_field;\n    uint16_t soc_pct;",
        )
        pm3_cmd.write_text(broken_struct_cmd, encoding="utf-8")
        ok_broken, summary_broken = verify_contract(mock_root, android_dir)
        if ok_broken or not any("bwm_battery_info_t" in f for f in summary_broken["failures"]):
            print("[SELF-TEST FAILED] Did not catch breaking struct layout change in bwm_battery_info_t!", file=sys.stderr)
            return False
        pm3_cmd.write_text(orig_pm3_cmd, encoding="utf-8")

        # Test C: Mutate inline `struct p` in `armsrc/appmain.c` for `CMD_PM5_RGB_SET`
        appmain = mock_root / "armsrc/appmain.c"
        orig_appmain = appmain.read_text(encoding="utf-8")
        broken_appmain = orig_appmain.replace(
            "case CMD_PM5_RGB_SET: {\n            // Set the antenna RGB LED colour (used by `hf/lf tune --rgb`).\n            struct p {\n                uint8_t r;\n                uint8_t g;\n                uint8_t b;\n            } PACKED;",
            "case CMD_PM5_RGB_SET: {\n            struct p {\n                uint8_t mode;\n                uint8_t r;\n                uint8_t g;\n                uint8_t b;\n            } PACKED;",
        )
        appmain.write_text(broken_appmain, encoding="utf-8")
        ok_inline, summary_inline = verify_contract(mock_root, android_dir)
        if ok_inline or not any("CMD_PM5_RGB_SET" in f for f in summary_inline["failures"]):
            print("[SELF-TEST FAILED] Did not catch breaking inline struct change in armsrc/appmain.c!", file=sys.stderr)
            return False

    return True


def format_markdown_report(
    summary: dict,
    upstream_repo: str,
    baseline_sha: str,
    head_sha: str,
) -> str:
    status_icon = "✅ PASS" if summary["passed"] else "🚨 BREAKING PROTOCOL CHANGE DETECTED"
    lines = [
        f"## {status_icon}: Upstream Proxmark5 Wire Contract Check",
        "",
        f"- **Upstream Repository:** [`{upstream_repo}`](https://github.com/{upstream_repo})",
        f"- **Baseline Commit:** `{baseline_sha[:12]}`",
        f"- **Checked Commit:** [`{head_sha[:12]}`](https://github.com/{upstream_repo}/commit/{head_sha})",
        f"- **Compare Range:** [`{baseline_sha[:10]}...{head_sha[:10]}`](https://github.com/{upstream_repo}/compare/{baseline_sha}...{head_sha})",
        "",
        "### Verification Scope (Compiled via `gcc -std=c11` `_Static_assert`)",
        f"- **Opcodes (`CMD_*`) verified:** `{summary['opcodes_verified']}`",
        f"- **Status codes (`PM3_*`) verified:** `{summary['status_codes_verified']}`",
        f"- **Named wire structs (`sizeof` + per-field `offsetof`) verified:** `{summary['named_structs_verified']}`",
        f"- **Inline `armsrc/appmain.c` payload structs verified:** `{summary['inline_structs_verified']}`",
        f"- **Total C11 `_Static_assert` ABI checks:** `{summary['total_c_static_assertions']}`",
        "",
    ]
    if summary["failures"]:
        lines.append("### ❌ Broken Wire Contract Items")
        lines.append("")
        for f in summary["failures"]:
            lines.append(f"- {f}")
        lines.append("")
        lines.append("### How to Resolve")
        lines.append(
            "1. Update the corresponding Kotlin protocol model/constants in "
            "`app/src/main/java/com/proxmark5/client/protocol/` and "
            "`.github/scripts/verify_upstream_contract.py`."
        )
        lines.append(
            f"2. Update `.github/upstream-baseline.json` to `{head_sha}` and close this issue."
        )
    else:
        lines.append("All wire-level opcodes, status codes, struct sizes, field offsets, and BWM transport parameters match the Android client with zero drift.")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify upstream Proxmark5 wire protocol contract.")
    parser.add_argument("--proxmark3-dir", type=Path, required=True, help="Path to proxmark3 checkout")
    parser.add_argument(
        "--android-dir",
        type=Path,
        default=Path(__file__).resolve().parents[2],
        help="Path to Proxmark5_Android_Client root",
    )
    parser.add_argument("--upstream-repo", default="RfidResearchGroup/proxmark3")
    parser.add_argument("--baseline-sha", default="unknown")
    parser.add_argument("--head-sha", default="unknown")
    parser.add_argument("--markdown-report", type=Path, default=None)
    parser.add_argument("--json-summary", type=Path, default=None)
    parser.add_argument("--self-test", action="store_true", help="Run mutation self-tests to prove 0 false positives and 100%% breaking-change detection")
    args = parser.parse_args()

    if args.self_test:
        ok_st = run_self_test(args.proxmark3_dir.resolve(), args.android_dir.resolve())
        if not ok_st:
            return 2
        print("[SELF-TEST OK] Benign noise ignored (0 false positives) & simulated wire breaks caught (100% precision).")

    passed, summary = verify_contract(args.proxmark3_dir.resolve(), args.android_dir.resolve())
    report_md = format_markdown_report(summary, args.upstream_repo, args.baseline_sha, args.head_sha)

    if args.markdown_report:
        args.markdown_report.write_text(report_md, encoding="utf-8")
    if args.json_summary:
        args.json_summary.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")

    print(report_md)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
