package com.proxmark5.client.protocol

import java.util.UUID

object Pm5Constants {
    // Proxmark5 BWM BLE UUIDs
    val BLE_SERVICE_UUID: UUID = UUID.fromString("0000ae86-0000-1000-8000-00805f9b34fb")
    val BLE_DATA_CHAR_UUID: UUID = UUID.fromString("0000ae88-0000-1000-8000-00805f9b34fb")
    val BLE_CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val DEFAULT_BLE_NAME = "Proxmark5"
    const val DEFAULT_WIFI_PORT = 18888

    // Preamble Magic numbers (little-endian wire representation)
    // 0x61334d50 -> "PM3a" (bytes: 0x50, 0x4D, 0x33, 0x61)
    const val COMMANDNG_PREAMBLE_MAGIC: Int = 0x61334D50

    // 0x62334d50 -> "PM3b" (bytes: 0x50, 0x4D, 0x33, 0x62)
    const val RESPONSENG_PREAMBLE_MAGIC: Int = 0x62334D50

    // Postamble Magic / placeholder CRC numbers
    const val COMMANDNG_POSTAMBLE_MAGIC: Int = 0x3361
    const val RESPONSENG_POSTAMBLE_MAGIC: Int = 0x3362

    // Header size constants
    const val COMMAND_PREAMBLE_SIZE = 8
    const val RESPONSE_PREAMBLE_SIZE = 10
    const val POSTAMBLE_SIZE = 2
    const val MAX_PAYLOAD_SIZE = 4096

    // Exact Opcodes from pm3_cmd.h
    const val CMD_DEBUG_PRINT_STRING     = 0x0100
    const val CMD_VERSION                = 0x0107
    const val CMD_STATUS                 = 0x0108
    const val CMD_PING                   = 0x0109
    const val CMD_BREAK_LOOP             = 0x0118

    // PM5 Antenna & Hardware Control Opcodes (I2C 0x51 Antenna Board, 0x48 RGB)
    const val CMD_ANT_CONTROL_WRITE      = 0x0170
    const val CMD_ANT_CONTROL_READ       = 0x0171
    const val CMD_EEPROM_FACTORY_INFO_READ  = 0x0172
    const val CMD_EEPROM_FACTORY_INFO_WRITE = 0x0173
    const val CMD_PM5_QC_TEST_IO         = 0x0176
    const val CMD_PM5_QC_TEST_HW         = 0x0177
    const val CMD_PM5_RGB_SET            = 0x0178
    const val CMD_PM5_BWM_SET_CAP        = 0x0179
    const val CMD_PM5_BWM_CHARGE_EN      = 0x017A
    const val CMD_PM5_BWM_AUTOOFF        = 0x017B
    const val CMD_PM5_BWM_WIFI           = 0x017C
    const val CMD_PM5_BWM_SET_VCHG       = 0x017D
    const val CMD_PM5_BWM_ESP_OTA        = 0x017E
    const val CMD_PM5_BWM_BLE_NAME       = 0x017F
    const val CMD_PM5_POWERSAVE          = 0x0180
    const val CMD_PM5_BWM_POWERSAVE      = 0x0181
    const val CMD_PM5_BWM_WIFI_PS        = 0x0182
    const val CMD_PM5_BWM_BLE            = 0x0183
    const val CMD_PM5_BWM_GET_BATTERY    = 0x0184
    const val CMD_CEP_STATUS             = 0x0185

    // LF Opcodes
    const val CMD_LF_TI_READ             = 0x0202
    const val CMD_LF_ACQ_RAW_ADC         = 0x0205
    const val CMD_DOWNLOAD_BIGBUF        = 0x0207
    const val CMD_DOWNLOADED_BIGBUF      = 0x0208
    const val CMD_LF_HID_WATCH           = 0x020B
    const val CMD_LF_EM410X_CLONE        = 0x0211
    const val CMD_LF_T55XX_READBL        = 0x0214
    const val CMD_LF_T55XX_WRITEBL       = 0x0215
    const val CMD_LF_T55XX_RESET_READ    = 0x0216
    const val CMD_LF_IO_WATCH            = 0x021A
    const val CMD_LF_EM410X_WATCH        = 0x021C
    const val CMD_LF_AWID_WATCH          = 0x0221
    const val CMD_LF_T55XX_WAKEUP        = 0x0224
    const val CMD_LF_COTAG_READ          = 0x0225
    const val CMD_LF_T55XX_SET_CONFIG    = 0x0226
    const val CMD_LF_SAMPLING_GET_CONFIG = 0x0228
    const val CMD_LF_T55XX_CHK_PWDS      = 0x0230
    const val CMD_LF_T55XX_DANGERRAW     = 0x0231
    const val CMD_LF_EM4X50_INFO         = 0x0240
    const val CMD_LF_HITAG_READER        = 0x0372

    // HF Opcodes
    const val CMD_HF_ISO15693_READER     = 0x0310
    const val CMD_HF_ISO14443A_READER    = 0x0385
    const val CMD_HF_ICLASS_READER       = 0x0394

    // Antenna Measurement
    const val CMD_MEASURE_ANTENNA_TUNING = 0x0400

    // T55xx Downlink Modes
    const val T55XX_DLMODE_FIXED         = 0
    const val T55XX_DLMODE_LLR           = 1
    const val T55XX_DLMODE_LEADING_ZERO  = 2
    const val T55XX_DLMODE_1OF4          = 3

    // Target chips for EM410x Clone
    const val TARGET_T55X7  = 0
    const val TARGET_Q5     = 1
    const val TARGET_EM4305 = 2

    // Status return codes
    const val PM3_SUCCESS       = 0
    const val PM3_EUNDEF        = -1
    const val PM3_EINVARG       = -2
    const val PM3_EDEVNOTSUPP   = -3
    const val PM3_ETIMEOUT      = -4
    const val PM3_EOPABORTED    = -5
    const val PM3_ENOTIMPL      = -6
    const val PM3_ERFTRANS      = -7
    const val PM3_EIO           = -8
    const val PM3_EOVFLOW       = -9
    const val PM3_ESOFT         = -10
    const val PM3_EFLASH        = -11
    const val PM3_EMALLOC       = -12
    const val PM3_EFILE         = -13
    const val PM3_ENOTTY        = -14
    const val PM3_EINIT         = -15
    const val PM3_EWRONGANSWER  = -16
    const val PM3_EOUTOFBOUND   = -17
    const val PM3_ECARDEXCHANGE = -18
    const val PM3_EFAILED       = -21
    const val PM3_EPARTIAL      = -22
    const val PM3_ETEAROFF      = -23
    const val PM3_ECRC          = -24

    fun statusToString(status: Int): String {
        return when (status) {
            PM3_SUCCESS -> "SUCCESS (0)"
            PM3_EUNDEF -> "UNDEFINED ERROR (-1)"
            PM3_EINVARG -> "INVALID ARGUMENT (-2)"
            PM3_EDEVNOTSUPP -> "DEVICE NOT SUPPORTED (-3)"
            PM3_ETIMEOUT -> "OPERATION TIMEOUT (-4)"
            PM3_EOPABORTED -> "OPERATION ABORTED (-5)"
            PM3_ENOTIMPL -> "NOT IMPLEMENTED (-6)"
            PM3_ERFTRANS -> "RF TRANSMISSION ERROR (-7)"
            PM3_EIO -> "I/O ERROR (-8)"
            PM3_EOVFLOW -> "BUFFER OVERFLOW (-9)"
            PM3_ESOFT -> "SOFTWARE ERROR (-10)"
            PM3_EFLASH -> "FLASH ERROR (-11)"
            PM3_EMALLOC -> "MEMORY ALLOCATION ERROR (-12)"
            PM3_EFILE -> "FILE ERROR (-13)"
            PM3_ENOTTY -> "TTY ERROR (-14)"
            PM3_EINIT -> "INIT ERROR (-15)"
            PM3_EWRONGANSWER -> "WRONG ANSWER (-16)"
            PM3_EOUTOFBOUND -> "OUT OF BOUNDS (-17)"
            PM3_ECARDEXCHANGE -> "CARD EXCHANGE ERROR (-18)"
            PM3_EFAILED -> "CMD FAILED (-21)"
            PM3_EPARTIAL -> "PARTIAL OPERATION (-22)"
            PM3_ETEAROFF -> "TEAROFF OCCURRED (-23)"
            PM3_ECRC -> "CRC MISMATCH (-24)"
            else -> "STATUS $status"
        }
    }
}
