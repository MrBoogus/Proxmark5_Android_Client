#!/usr/bin/env bash
# Repeatable Live Hardware Demo & Screenshot Capture Script
# Hardware:
#   - Android device over ADB (Galaxy Z Fold 7, RFCY80A7WVL)
#   - Battery-powered Proxmark5 BWM connected over BLE (58:2A:BD:XX:XX:XX)
#   - Proxmark3 Easy connected over USB (/dev/ttyACM0)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MEDIA_DIR="${REPO_ROOT}/docs/media"
RAW_DIR="${REPO_ROOT}/build/demo_raw"
FASTLANE_SHOTS="${REPO_ROOT}/fastlane/metadata/android/en-US/images/phoneScreenshots"
SERIAL="${ADB_SERIAL:-RFCY80A7WVL}"
APP_PKG="${APP_PKG:-com.proxmark5.client.debug}"
PM3_BIN="${PM3_BIN:-/home/Boogus/proxmark3/pm3}"

COVER_DISPLAY="4630946872173396372"
INNER_DISPLAY="4630946449689556883"

mkdir -p "${MEDIA_DIR}" "${RAW_DIR}" "${FASTLANE_SHOTS}"

adb_cmd() {
    adb -s "${SERIAL}" "$@"
}

send_action() {
    local act="$1"
    shift
    adb_cmd shell am broadcast -a "com.proxmark5.client.${act}" -p "${APP_PKG}" "$@" >/dev/null
}

echo "[1/6] Checking connected hardware..."
adb devices -l
if [[ -x "${PM3_BIN}" ]]; then
    "${PM3_BIN}" -c "hw version" > "${RAW_DIR}/pm3_hw_version.log" 2>&1 || true
    grep -E "Proxmark3|Bootrom|OS|LF|HF" "${RAW_DIR}/pm3_hw_version.log" | head -n 10 || true
fi

echo "[2/6] Enabling SystemUI Demo Mode & Do Not Disturb..."
adb_cmd shell svc power stayon true
adb_cmd shell cmd device_state state reset
sleep 1
adb_cmd shell input keyevent KEYCODE_WAKEUP
adb_cmd shell cmd statusbar collapse || true
adb_cmd shell cmd notification set_dnd priority
adb_cmd shell settings put global sysui_demo_allowed 1
adb_cmd shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb_cmd shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null
adb_cmd shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
adb_cmd shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e datatype 5g -e level 4 >/dev/null
adb_cmd shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null

echo "[3/6] Ensuring app is running and connected to Proxmark5 over BLE..."
adb_cmd shell am start -n "${APP_PKG}/com.proxmark5.client.MainActivity" >/dev/null
sleep 2
send_action "CONNECT_FIRST"
sleep 3
send_action "SWITCH_TAB" --ei tab 0
send_action "READ_BATTERY"
send_action "SET_ANTENNA_LEDS" --ez lf true --ez hf true
send_action "SET_ANTENNA_FREQ" --es freq "125k"
send_action "SET_ANTENNA_Q" --ez highQ true
send_action "SET_RGB" --ei r 0 --ei g 229 --ei b 255
sleep 2

echo "[4/6] Recording live hardware scenes..."

# Scene 1: Power, BLE & Smart Antenna / WS2812 RGB (~11s)
echo "  -> Scene 1: Power, BQ27427 Fuel Gauge, Smart Antenna & WS2812 RGB..."
adb_cmd shell "screenrecord --display-id ${COVER_DISPLAY} --bit-rate 8000000 --time-limit 11 /sdcard/scene1_power_rgb.mp4" &
REC_PID=$!
sleep 1.2
send_action "READ_BATTERY"
sleep 1.2
send_action "SET_ANTENNA_LEDS" --ez lf true --ez hf false
sleep 1.0
send_action "SET_ANTENNA_LEDS" --ez lf true --ez hf true
send_action "SET_ANTENNA_FREQ" --es freq "134k"
send_action "SET_ANTENNA_Q" --ez highQ false
sleep 1.2
send_action "SET_ANTENNA_FREQ" --es freq "125k"
send_action "SET_ANTENNA_Q" --ez highQ true
sleep 1.2
send_action "SET_RGB" --ei r 255 --ei g 23 --ei b 68
sleep 1.2
send_action "SET_RGB" --ei r 0 --ei g 230 --ei b 118
sleep 1.2
send_action "SET_RGB" --ei r 0 --ei g 229 --ei b 255
wait "${REC_PID}" || true
adb_cmd pull /sdcard/scene1_power_rgb.mp4 "${RAW_DIR}/scene1_power_rgb.mp4" >/dev/null

# Scene 2: Live Antenna Resonance Sweep (~11s)
echo "  -> Scene 2: Live Antenna Resonance Sweep (0x0400)..."
adb_cmd shell "screenrecord --display-id ${COVER_DISPLAY} --bit-rate 8000000 --time-limit 11 /sdcard/scene2_antenna_tune.mp4" &
REC_PID=$!
sleep 1.0
adb_cmd shell input swipe 540 1950 540 950 500
sleep 1.2
send_action "MEASURE_ANTENNA"
sleep 4.5
send_action "READ_ANTENNA"
wait "${REC_PID}" || true
adb_cmd pull /sdcard/scene2_antenna_tune.mp4 "${RAW_DIR}/scene2_antenna_tune.mp4" >/dev/null

# Capture crisp screenshot of Power & Antenna Resonance
adb_cmd shell screencap -d "${COVER_DISPLAY}" -p > "${MEDIA_DIR}/screen_power_antenna.png"

# Scene 3: LF Studio, Watch Loop & EM4100 Manchester Bit Matrix (~11s)
echo "  -> Scene 3: LF Studio & EM4100 Manchester Encoder..."
send_action "SWITCH_TAB" --ei tab 1
sleep 1
adb_cmd shell screencap -d "${COVER_DISPLAY}" -p > "${MEDIA_DIR}/screen_lf_studio.png"
adb_cmd shell "screenrecord --display-id ${COVER_DISPLAY} --bit-rate 8000000 --time-limit 11 /sdcard/scene3_lf_studio.mp4" &
REC_PID=$!
sleep 1.0
send_action "TOGGLE_WATCH" --es tech "EM4100"
sleep 2.2
send_action "TOGGLE_WATCH" --es tech "EM4100"
sleep 1.0
send_action "TOGGLE_WATCH" --es tech "HID"
sleep 2.0
send_action "TOGGLE_WATCH" --es tech "HID"
sleep 1.0
send_action "EXECUTE_CLONE" --es uid "0F0368568B"
sleep 1.2
adb_cmd shell input swipe 540 1950 540 900 500
wait "${REC_PID}" || true
adb_cmd pull /sdcard/scene3_lf_studio.mp4 "${RAW_DIR}/scene3_lf_studio.mp4" >/dev/null
adb_cmd shell screencap -d "${COVER_DISPLAY}" -p > "${MEDIA_DIR}/screen_lf_manchester.png"

# Scene 4: T55xx Transponder Suite & White Cloner Calculator (~11s)
echo "  -> Scene 4: T55xx Transponder Suite & White Cloner Key..."
send_action "SWITCH_TAB" --ei tab 3
sleep 1
adb_cmd shell "screenrecord --display-id ${COVER_DISPLAY} --bit-rate 8000000 --time-limit 11 /sdcard/scene4_t55xx_suite.mp4" &
REC_PID=$!
sleep 1.0
send_action "READ_T55XX_ALL"
sleep 5.0
adb_cmd shell input swipe 540 1950 540 900 500
sleep 1.0
send_action "WHITE_CLONER_KEY" --es uid "0F0368568B"
wait "${REC_PID}" || true
adb_cmd pull /sdcard/scene4_t55xx_suite.mp4 "${RAW_DIR}/scene4_t55xx_suite.mp4" >/dev/null
adb_cmd shell screencap -d "${COVER_DISPLAY}" -p > "${MEDIA_DIR}/screen_t55xx_suite.png"

# Scene 5: Protocol Console & Hardware Diagnostics (~10s)
echo "  -> Scene 5: Live Protocol Console & Diagnostics..."
send_action "SWITCH_TAB" --ei tab 5
sleep 1
adb_cmd shell "screenrecord --display-id ${COVER_DISPLAY} --bit-rate 8000000 --time-limit 10 /sdcard/scene5_console_protocol.mp4" &
REC_PID=$!
sleep 1.2
send_action "PING"
sleep 1.8
send_action "READ_BATTERY"
sleep 1.8
send_action "READ_ANTENNA"
sleep 1.8
send_action "MEASURE_ANTENNA"
wait "${REC_PID}" || true
adb_cmd pull /sdcard/scene5_console_protocol.mp4 "${RAW_DIR}/scene5_console_protocol.mp4" >/dev/null
adb_cmd shell screencap -d "${COVER_DISPLAY}" -p > "${MEDIA_DIR}/screen_console_logs.png"

# Scene 6: Unfolded Foldable NavigationRail + 2-Column Workspace (~11s)
echo "  -> Scene 6: Unfolded Foldable Adaptive Workspace..."
adb_cmd shell cmd device_state state 3
sleep 2
adb_cmd shell input keyevent KEYCODE_WAKEUP
adb_cmd shell am start -n "${APP_PKG}/com.proxmark5.client.MainActivity" >/dev/null
sleep 1
send_action "SWITCH_TAB" --ei tab 0
sleep 1
adb_cmd shell screencap -d "${INNER_DISPLAY}" -p > "${MEDIA_DIR}/screen_unfolded_tablet.png"
adb_cmd shell "screenrecord --display-id ${INNER_DISPLAY} --bit-rate 8000000 --time-limit 11 /sdcard/scene6_foldable_tablet.mp4" &
REC_PID=$!
sleep 1.2
send_action "MEASURE_ANTENNA"
sleep 2.8
send_action "SWITCH_TAB" --ei tab 1
sleep 2.0
send_action "SWITCH_TAB" --ei tab 3
sleep 2.0
send_action "SWITCH_TAB" --ei tab 5
wait "${REC_PID}" || true
adb_cmd pull /sdcard/scene6_foldable_tablet.mp4 "${RAW_DIR}/scene6_foldable_tablet.mp4" >/dev/null

echo "[5/6] Restoring phone state..."
adb_cmd shell cmd device_state state reset
adb_cmd shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null
adb_cmd shell cmd notification set_dnd off
adb_cmd shell svc power stayon false

echo "[6/6] Raw scenes captured in ${RAW_DIR} and screenshots saved in ${MEDIA_DIR}."
