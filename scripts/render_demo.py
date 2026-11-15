#!/usr/bin/env python3
"""
Render 1080p demo.mp4 (60-90s) and demo.gif (<10MB, 15-20s) from live hardware captures.
Combines the live ADB screenrecord clips with a clean technical HUD panel showing
the real PM5 BWM opcodes, BLE telemetry, and PM3 Easy bench logs.
"""
import os
import shutil
import subprocess
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

REPO_ROOT = Path(__file__).resolve().parent.parent
RAW_DIR = REPO_ROOT / "build" / "demo_raw"
MEDIA_DIR = REPO_ROOT / "docs" / "media"
IMAGES_DIR = REPO_ROOT / "docs" / "images"
FASTLANE_SHOTS = REPO_ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "phoneScreenshots"


def get_font(size: int, bold: bool = False):
    candidates = [
        "/usr/share/fonts/google-droid-sans-mono-fonts/DroidSansMono.ttf",
        "/usr/share/fonts/dejavu-sans-mono-fonts/DejaVuSansMono-Bold.ttf" if bold else "/usr/share/fonts/dejavu-sans-mono-fonts/DejaVuSansMono.ttf",
        "/usr/share/fonts/liberation-mono-fonts/LiberationMono-Bold.ttf" if bold else "/usr/share/fonts/liberation-mono-fonts/LiberationMono-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
    ]
    for c in candidates:
        if os.path.exists(c):
            return ImageFont.truetype(c, size)
    return ImageFont.load_default()


def create_hud_overlay(
    out_path: Path,
    scene_badge: str,
    title: str,
    subtitle: str,
    opcodes: list[str],
    terminal_lines: list[str],
    video_x: int,
    video_y: int,
    video_w: int,
    video_h: int,
):
    img = Image.new("RGB", (1920, 1080), (6, 9, 15))
    draw = ImageDraw.Draw(img)

    # Subtle grid background
    for gx in range(0, 1920, 48):
        draw.line([(gx, 0), (gx, 1080)], fill=(14, 21, 35), width=1)
    for gy in range(0, 1080, 48):
        draw.line([(0, gy), (1920, gy)], fill=(14, 21, 35), width=1)

    # Video frame border glow
    pad = 4
    draw.rounded_rectangle(
        [video_x - pad, video_y - pad, video_x + video_w + pad, video_y + video_h + pad],
        radius=14,
        outline=(0, 229, 255),
        width=2,
        fill=(0, 0, 0),
    )

    # Right HUD Column
    rx = video_x + video_w + 48
    rw = 1920 - rx - 48

    f_badge = get_font(18, bold=True)
    f_title = get_font(30, bold=True)
    f_sub = get_font(18, bold=False)
    f_sec = get_font(17, bold=True)
    f_code = get_font(16, bold=False)

    # Top hardware badge pill
    draw.rounded_rectangle([rx, 44, rx + 420, 82], radius=8, fill=(8, 36, 48), outline=(0, 229, 255), width=2)
    draw.text((rx + 16, 52), scene_badge, font=f_badge, fill=(0, 229, 255))

    # Live Hardware Status Pill (Right-aligned)
    draw.rounded_rectangle([rx + 440, 44, rx + rw, 82], radius=8, fill=(8, 40, 26), outline=(0, 230, 118), width=2)
    draw.text((rx + 458, 52), "LIVE BLE: PM5 BWM (58:2A:BD:XX:XX:XX)", font=f_badge, fill=(0, 230, 118))

    # Title & Subtitle
    draw.text((rx, 104), title, font=f_title, fill=(240, 246, 252, 255))
    draw.text((rx, 148), subtitle, font=f_sub, fill=(148, 163, 184, 255))

    # Card 1: Wire Protocol & Opcode Telemetry
    c1_top = 198
    c1_bot = 510
    draw.rounded_rectangle([rx, c1_top, rx + rw, c1_bot], radius=12, fill=(13, 19, 31, 245), outline=(30, 41, 59, 255), width=2)
    draw.text((rx + 22, c1_top + 18), "PM3 / PM5 BWM BINARY FRAME TELEMETRY (PREAMBLE 'PM3a')", font=f_sec, fill=(0, 229, 255, 255))
    draw.line([(rx + 22, c1_top + 48), (rx + rw - 22, c1_top + 48)], fill=(30, 41, 59, 255), width=1)

    y = c1_top + 64
    for line in opcodes:
        color = (226, 232, 240, 255)
        if line.startswith("TX >>"):
            color = (0, 229, 255, 255)
        elif line.startswith("RX <<"):
            color = (0, 230, 118, 255)
        elif line.startswith("I2C"):
            color = (255, 179, 0, 255)
        draw.text((rx + 22, y), line, font=f_code, fill=color)
        y += 30

    # Card 2: Hardware Bench & ADB / PM3 Easy Log
    c2_top = 534
    c2_bot = 960
    draw.rounded_rectangle([rx, c2_top, rx + rw, c2_bot], radius=12, fill=(10, 14, 23, 245), outline=(30, 41, 59, 255), width=2)
    draw.text((rx + 22, c2_top + 18), "LIVE HARDWARE TESTBENCH LOG (ADB + PROXMARK3 EASY + PM5 BLE)", font=f_sec, fill=(0, 230, 118, 255))
    draw.line([(rx + 22, c2_top + 48), (rx + rw - 22, c2_top + 48)], fill=(30, 41, 59, 255), width=1)

    y = c2_top + 64
    for line in terminal_lines:
        color = (168, 181, 200, 255)
        if line.startswith("$") or line.startswith("[usb]"):
            color = (255, 193, 7, 255)
        elif "[+]" in line or "OK" in line or "CONNECTED" in line:
            color = (0, 230, 118, 255)
        draw.text((rx + 22, y), line, font=f_code, fill=color)
        y += 28

    # Footer caption bar
    draw.rounded_rectangle([rx, 982, rx + rw, 1036], radius=10, fill=(15, 23, 42, 240), outline=(51, 65, 85, 255), width=1)
    draw.text(
        (rx + 20, 998),
        "MrBoogus/Proxmark5_Android_Client  •  Native Kotlin & Jetpack Compose  •  GPL-3.0",
        font=f_sub,
        fill=(148, 163, 184, 255),
    )

    img.save(out_path)


def compose_scene(
    raw_mp4: Path,
    hud_png: Path,
    out_mp4: Path,
    video_x: int,
    video_y: int,
    video_w: int,
    video_h: int,
    duration: float,
):
    cmd = [
        "ffmpeg",
        "-y",
        "-loop",
        "1",
        "-t",
        f"{duration:.2f}",
        "-i",
        str(hud_png),
        "-i",
        str(raw_mp4),
        "-filter_complex",
        f"[1:v]fps=30,scale={video_w}:{video_h}:flags=lanczos[vid];"
        f"[0:v][vid]overlay={video_x}:{video_y}:shortest=1,format=yuv420p[outv]",
        "-map",
        "[outv]",
        "-t",
        f"{duration:.2f}",
        "-c:v",
        "libx264",
        "-preset",
        "fast",
        "-crf",
        "20",
        "-pix_fmt",
        "yuv420p",
        str(out_mp4),
    ]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def make_hf_vault_scene(out_raw_mp4: Path):
    """Create a 10s video from the genuine bench screenshots (HF Classic 1K, HF DESFire, Tag Vault)."""
    shots = [
        IMAGES_DIR / "hf_cover_folded.png",
        IMAGES_DIR / "hf_nfc_unfolded.png",
        IMAGES_DIR / "vault_unfolded.png",
    ]
    slides_dir = RAW_DIR / "hf_slides"
    slides_dir.mkdir(parents=True, exist_ok=True)
    slide_mp4s = []
    for idx, shot in enumerate(shots):
        # Fit inside 880x1000 box on black background
        im = Image.open(shot).convert("RGB")
        canvas = Image.new("RGB", (880, 1000), (8, 11, 18))
        im.thumbnail((880, 1000), Image.Resampling.LANCZOS)
        ox = (880 - im.width) // 2
        oy = (1000 - im.height) // 2
        canvas.paste(im, (ox, oy))
        slide_png = slides_dir / f"slide_{idx}.png"
        canvas.save(slide_png)
        s_mp4 = slides_dir / f"slide_{idx}.mp4"
        subprocess.run(
            [
                "ffmpeg",
                "-y",
                "-loop",
                "1",
                "-t",
                "3.4",
                "-i",
                str(slide_png),
                "-vf",
                "fps=30,format=yuv420p",
                "-c:v",
                "libx264",
                "-pix_fmt",
                "yuv420p",
                str(s_mp4),
            ],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        slide_mp4s.append(s_mp4)

    concat_txt = slides_dir / "concat.txt"
    concat_txt.write_text("".join(f"file '{p}'\n" for p in slide_mp4s))
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            str(concat_txt),
            "-c",
            "copy",
            str(out_raw_mp4),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def main():
    MEDIA_DIR.mkdir(parents=True, exist_ok=True)
    FASTLANE_SHOTS.mkdir(parents=True, exist_ok=True)

    # Copy screenshots to Fastlane phoneScreenshots (1.png .. 6.png)
    shot_pairs = [
        (MEDIA_DIR / "screen_power_antenna.png", FASTLANE_SHOTS / "1.png"),
        (MEDIA_DIR / "screen_lf_studio.png", FASTLANE_SHOTS / "2.png"),
        (MEDIA_DIR / "screen_lf_manchester.png", FASTLANE_SHOTS / "3.png"),
        (IMAGES_DIR / "hf_cover_folded.png", FASTLANE_SHOTS / "4.png"),
        (MEDIA_DIR / "screen_t55xx_suite.png", FASTLANE_SHOTS / "5.png"),
        (MEDIA_DIR / "screen_console_logs.png", FASTLANE_SHOTS / "6.png"),
    ]
    for src, dst in shot_pairs:
        if src.exists():
            shutil.copyfile(src, dst)
    if (IMAGES_DIR / "hf_cover_folded.png").exists():
        shutil.copyfile(IMAGES_DIR / "hf_cover_folded.png", MEDIA_DIR / "screen_hf_reader.png")
    if (IMAGES_DIR / "vault_unfolded.png").exists():
        shutil.copyfile(IMAGES_DIR / "vault_unfolded.png", MEDIA_DIR / "screen_tag_vault.png")

    # Build Scene 7 raw clip from genuine HF + Vault bench captures
    scene7_raw = RAW_DIR / "scene7_hf_vault.mp4"
    make_hf_vault_scene(scene7_raw)

    scenes = [
        {
            "id": "scene1",
            "raw": RAW_DIR / "scene1_power_rgb.mp4",
            "duration": 10.5,
            "vx": 48,
            "vy": 40,
            "vw": 428,
            "vh": 1000,
            "badge": "SCENE 1 / 7  •  BLE & HARDWARE",
            "title": "BLE GATT Link, Fuel Gauge & Smart Antenna",
            "subtitle": "Direct BLE connection to untethered battery-powered Proxmark5 BWM",
            "opcodes": [
                "GATT Service: 0xAE86  |  Char: 0xAE88 (Write + Notify, MTU 256)",
                "TX >> CMD_PM5_BWM_GET_BATTERY (0x0184) [10B PM3a frame]",
                "RX << CMD_PM5_BWM_GET_BATTERY (0x0184) [19B BQ27427 payload]",
                "      SoC: 69%  |  Volt: 3805 mV  |  Curr: -231 mA  |  Temp: 31.0 C",
                "TX >> CMD_FPC_SEND_I2C (0x0170) -> I2C 0x51 Reg 0x02 = 0x87",
                "      [LF 125 kHz + High-Q Resonance Boost + LF/HF Blue LEDs ON]",
                "TX >> CMD_FPC_WS2812_SET_COLOR (0x0178) -> I2C 0x48 [RGB Cycle]",
            ],
            "term": [
                "$ adb devices -l",
                "RFCY80A7WVL  device model:SM_F966B transport_id:1",
                "[+] BLE GATT Connected: Proxmark5 (58:2A:BD:XX:XX:XX), MTU=256",
                "[+] handshake complete: PM3a binary protocol active",
                "[+] BQ27427 fuel gauge: SoC=69%, 3805mV, -231mA (Discharging)",
                "[+] Smart Antenna (I2C 0x51) Reg 0x02 <- 0x87 (125kHz + High-Q)",
                "[+] WS2812 RGB Co-Processor (I2C 0x48): Red -> Green -> Cyan",
            ],
        },
        {
            "id": "scene2",
            "raw": RAW_DIR / "scene2_antenna_tune.mp4",
            "duration": 10.0,
            "vx": 48,
            "vy": 40,
            "vw": 428,
            "vh": 1000,
            "badge": "SCENE 2 / 7  •  RF TUNING",
            "title": "Live Antenna Tank Voltage & Resonance Sweep",
            "subtitle": "Real-time LF 125/134 kHz & HF 13.56 MHz ADC sweep with High-Q active",
            "opcodes": [
                "TX >> CMD_MEASURE_ANTENNA_TUNING (0x0400) [NG=true]",
                "RX << CMD_MEASURE_ANTENNA_TUNING (0x0400) [295B payload, status=0]",
                "      vLf125: 48403 mV (48.40 V)  <-- Boosted +114% by High-Q!",
                "      vLf134: 22342 mV (22.34 V)",
                "      vHf:    34873 mV (34.87 V)",
                "      peakV:  50401 mV (50.40 V) @ Divisor 95 (125.00 kHz)",
                "I2C   Smart Antenna Reg 0x02 = 0x87 verified via 0x0171 readback",
            ],
            "term": [
                "[usb] pm3 --> lf tune --divisor 95",
                "[=] Measuring LF antenna on Proxmark3 Easy bench sensor...",
                "[+] PM5 0x0400 sweep triggered over BLE (58:2A:BD:XX:XX:XX)",
                "[+] PM5 ADC result: LF125=48.40V | LF134=22.34V | HF=34.87V",
                "[+] Peak resonance: 50.40V @ 125.00 kHz (divisor 95)",
                "[+] High-Q factor switch doubled LF 125kHz tank voltage",
                "    from 22.62V (Low-Q 0x07) -> 48.40V (High-Q 0x87)",
            ],
        },
        {
            "id": "scene3",
            "raw": RAW_DIR / "scene3_lf_studio.mp4",
            "duration": 10.5,
            "vx": 48,
            "vy": 40,
            "vw": 428,
            "vh": 1000,
            "badge": "SCENE 3 / 7  •  LF STUDIO",
            "title": "LF Watch Loops & EM4100 64-Bit Manchester Matrix",
            "subtitle": "Continuous LF polling (EM4100 / HID / IO / AWID) & parity matrix encoder",
            "opcodes": [
                "TX >> CMD_LF_EM410X_WATCH (0x021C) -> Continuous LF field polling",
                "TX >> CMD_BREAK_LOOP (0x0118)      -> Cleanly halts PM5 watch loop",
                "TX >> CMD_LF_HID_WATCH (0x020B)    -> Continuous HID Prox polling",
                "TX >> CMD_LF_EM410X_CLONE (0x0211) -> 6B payload [card, hi, lo]",
                "EM4100 64-Bit Frame: 9-bit header (111111111) + 10x(4d+1p) + col parity",
                "Decoded UID: 0F0368568B -> 64-bit word: 0xFF87C065A2EA3D24",
            ],
            "term": [
                "[+] LF Studio: Toggling EM4100 watch loop (0x021C)",
                "[+] PM5 Dbprintf stream (0x0100): Waiting for EM410x tag...",
                "[+] Sent CMD_BREAK_LOOP (0x0118) -> PM5 returned status=0",
                "[+] LF Studio: Toggling HID Prox watch loop (0x020B)",
                "[+] Sent CMD_BREAK_LOOP (0x0118) -> PM5 returned status=0",
                "[+] Inspecting 64-bit EM4100 Manchester parity matrix:",
                "    Header=111111111 | Row/Column Even Parity | Stop=0",
            ],
        },
        {
            "id": "scene4",
            "raw": RAW_DIR / "scene4_t55xx_suite.mp4",
            "duration": 10.5,
            "vx": 48,
            "vy": 40,
            "vw": 428,
            "vh": 1000,
            "badge": "SCENE 4 / 7  •  T55XX SUITE",
            "title": "T55xx Transponder Suite & White Cloner Calculator",
            "subtitle": "Page 0 block reader/writer, dictionary audit & handheld cloner keygen",
            "opcodes": [
                "White Cloner Keygen (UID 0F0368568B) -> Password: 0x02698D8B",
                "TX >> CMD_LF_T55XX_SET_CONFIG (0x0224) [26B t55xx_cfg_block_t]",
                "      modulation=ASK (1), inverted=false, offset=0x20, rate=RF/64",
                "TX >> CMD_LF_T55XX_READBL (0x0214) [8B t55xx_read_block_t: Blk 0..7]",
                "TX >> CMD_LF_T55XX_WRITEBL (0x0215) [10B t55xx_write_block_t]",
                "TX >> CMD_LF_T55XX_CHKPWDS (0x0231) [Batch 13x 4B dictionary]",
            ],
            "term": [
                "[+] White Cloner Key Calculator: UID 0F0368568B -> 0x02698D8B",
                "[+] Configuring T55xx demodulator (0x0224: ASK, RF/64)",
                "[+] Scanning T5577 Page 0 Blocks 0..7 (0x0214):",
                "    Blk 0 (Config) .. Blk 7 (Password) streamed over BLE",
                "[+] Dictionary Audit ready: 52 common T55xx passwords",
                "    (00000000, 19920427, 51243648, AA55BBBB, ...)",
            ],
        },
        {
            "id": "scene5",
            "raw": RAW_DIR / "scene5_console_protocol.mp4",
            "duration": 9.5,
            "vx": 48,
            "vy": 40,
            "vw": 428,
            "vh": 1000,
            "badge": "SCENE 5 / 7  •  PROTOCOL CONSOLE",
            "title": "Real-Time Protocol Console & Diagnostics",
            "subtitle": "Live color-coded TX/RX frame monitor and asynchronous Dbprintf logs",
            "opcodes": [
                "TX >> CMD_PING (0x0109) [32B echo payload, CRC-16/ISO14443-A]",
                "RX << CMD_PING (0x0109) [status=0, 32B verified in 39 ms]",
                "TX >> CMD_PM5_BWM_GET_BATTERY (0x0184) -> 69%, 3805mV, -231mA",
                "TX >> CMD_FPC_READ_I2C (0x0171)        -> I2C 0x51 Reg 0x02 = 0x87",
                "RX << CMD_STATUS (0x0100)              -> Live firmware Dbprintf()",
                "CRC-16/ISO14443-A: poly=0x1021, init=0xC6C6, refin/out=true, xor=0x0000",
            ],
            "term": [
                "[+] Console: Sending CMD_PING (0x0109) 32-byte echo test...",
                "[+] PING OK: 32 bytes echoed in 39ms (CRC-16/ISO14443-A valid)",
                "[+] Console: Querying BQ27427 fuel gauge (0x0184)...",
                "[+] Battery: SoC=69%, 3805mV, -231mA, 31.0C",
                "[+] Console: Reading Smart Antenna I2C 0x51 (0x0171)...",
                "[+] Smart Antenna Reg 0x02 = 0x87 [125kHz, LF+HF LED, High-Q]",
            ],
        },
        {
            "id": "scene6",
            "raw": RAW_DIR / "scene6_foldable_tablet.mp4",
            "duration": 10.5,
            "vx": 48,
            "vy": 40,
            "vw": 880,
            "vh": 976,
            "badge": "SCENE 6 / 7  •  FOLDABLE & TABLET",
            "title": "Adaptive Foldable NavigationRail & 2-Column Layout",
            "subtitle": "Seamless posture transition on Galaxy Z Fold 7 (screenWidthDp >= 600dp)",
            "opcodes": [
                "Folded Cover Screen (1080x2520): ScrollableTabRow + 1-col stack",
                "Unfolded Inner Display (1968x2184): NavigationRail + 2-col workspace",
                "Left Rail: Persistent icon navigation across all 6 studio modules",
                "TwoColumnAdaptiveLayout: Side-by-side controls & live telemetry",
                "Zero state loss across fold/unfold posture transitions",
            ],
            "term": [
                "$ adb shell cmd device_state state 3   # Unfold inner display",
                "[+] Display switched: 1968x2184 (screenWidthDp >= 600dp)",
                "[+] Activated Material 3 NavigationRail + TwoColumnAdaptiveLayout",
                "[+] Triggering live 0x0400 antenna sweep on unfolded dashboard",
                "[+] Switching modules: Power -> LF Studio -> T55xx -> Console",
            ],
        },
        {
            "id": "scene7",
            "raw": scene7_raw,
            "duration": 10.0,
            "vx": 48,
            "vy": 40,
            "vw": 880,
            "vh": 1000,
            "badge": "SCENE 7 / 7  •  HF 14A & TAG VAULT",
            "title": "ISO14443A Reader & Persistent JSON Tag Vault",
            "subtitle": "Verified against Proxmark3 Easy emulating MIFARE Classic 1K & DESFire",
            "opcodes": [
                "TX >> CMD_HF_ISO14443A_READER (0x0385) [ISO14A_CONNECT=0x01]",
                "RX << iso14a_card_select_t (25B): UID[10], uidlen, ATQA[2], SAK",
                "      UID=11223344       | ATQA=00 04 | SAK=08 -> MIFARE Classic 1K",
                "      UID=04AABBCCDDEE11 | ATQA=03 44 | SAK=20 -> MIFARE DESFire",
                "Tag Vault: Auto-persists LF/HF captures with 1-tap JSON export",
            ],
            "term": [
                "[usb] pm3 --> hf 14a sim -t 1 -u 11223344",
                "[+] PM5 read UID=11223344 (ATQA=00 04, SAK=08 MIFARE Classic 1K)",
                "[usb] pm3 --> hf 14a sim -t 3 -u 04AABBCCDDEE11",
                "[+] PM5 read UID=04AABBCCDDEE11 (ATQA=03 44, SAK=20 DESFire)",
                "[+] Saved dummy bench tags to Vault & verified JSON export",
            ],
        },
    ]

    rendered_mp4s = []
    for s in scenes:
        hud_png = RAW_DIR / f"{s['id']}_hud.png"
        out_mp4 = RAW_DIR / f"{s['id']}_rendered.mp4"
        create_hud_overlay(
            hud_png,
            s["badge"],
            s["title"],
            s["subtitle"],
            s["opcodes"],
            s["term"],
            s["vx"],
            s["vy"],
            s["vw"],
            s["vh"],
        )
        compose_scene(
            s["raw"],
            hud_png,
            out_mp4,
            s["vx"],
            s["vy"],
            s["vw"],
            s["vh"],
            s["duration"],
        )
        rendered_mp4s.append(out_mp4)
        print(f"Rendered {out_mp4.name} ({s['duration']}s)")

    # Concatenate all 7 scenes into docs/media/demo.mp4 (~71.5s, 1080p)
    concat_file = RAW_DIR / "scenes_concat.txt"
    concat_file.write_text("".join(f"file '{p}'\n" for p in rendered_mp4s))
    demo_mp4 = MEDIA_DIR / "demo.mp4"
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            str(concat_file),
            "-c:v",
            "libx264",
            "-preset",
            "medium",
            "-crf",
            "21",
            "-pix_fmt",
            "yuv420p",
            "-movflags",
            "+faststart",
            str(demo_mp4),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    print(f"Created {demo_mp4} ({demo_mp4.stat().st_size / (1024*1024):.2f} MB)")

    # Build docs/media/demo.gif (16.5s highlight reel from Scenes 1, 2, 6, < 10 MB)
    gif_clips = [
        (rendered_mp4s[0], "0", "5.5"),
        (rendered_mp4s[1], "0", "5.5"),
        (rendered_mp4s[5], "0", "5.5"),
    ]
    sub_mp4s = []
    for idx, (src_mp4, ss, dur) in enumerate(gif_clips):
        sub_p = RAW_DIR / f"gif_part_{idx}.mp4"
        subprocess.run(
            [
                "ffmpeg",
                "-y",
                "-ss",
                ss,
                "-t",
                dur,
                "-i",
                str(src_mp4),
                "-c",
                "copy",
                str(sub_p),
            ],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        sub_mp4s.append(sub_p)

    gif_concat = RAW_DIR / "gif_concat.txt"
    gif_concat.write_text("".join(f"file '{p}'\n" for p in sub_mp4s))
    gif_source_mp4 = RAW_DIR / "gif_source.mp4"
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            str(gif_concat),
            "-c",
            "copy",
            str(gif_source_mp4),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )

    demo_gif = MEDIA_DIR / "demo.gif"
    palette_png = RAW_DIR / "palette.png"
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-i",
            str(gif_source_mp4),
            "-vf",
            "fps=10,scale=860:-1:flags=lanczos,palettegen=max_colors=128:stats_mode=diff",
            str(palette_png),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-i",
            str(gif_source_mp4),
            "-i",
            str(palette_png),
            "-lavfi",
            "fps=10,scale=860:-1:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle",
            str(demo_gif),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    print(f"Created {demo_gif} ({demo_gif.stat().st_size / (1024*1024):.2f} MB)")


if __name__ == "__main__":
    main()
