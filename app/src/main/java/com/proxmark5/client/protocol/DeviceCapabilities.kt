package com.proxmark5.client.protocol

import com.proxmark5.client.comm.VersionInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parsed device identity and feature flags from CMD_CAPABILITIES (0x0112, version == 13)
 * with safe fallback when talking to older/unknown firmware.
 */
data class DeviceCapabilities(
    val version: Int = 0,
    val capabilitiesKnown: Boolean = false,
    val baudrate: Long = 0L,
    val bigbufSize: Long = 0L,
    val viaFpc: Boolean = false,
    val viaUsb: Boolean = false,
    val compiledWithFlash: Boolean = false,
    val compiledWithSmartcard: Boolean = false,
    val compiledWithFpcUsart: Boolean = false,
    val compiledWithFpcUsartDev: Boolean = false,
    val compiledWithFpcUsartHost: Boolean = false,
    val compiledWithLf: Boolean = false,
    val compiledWithHitag: Boolean = false,
    val compiledWithEm4x50: Boolean = false,
    val compiledWithEm4x70: Boolean = false,
    val compiledWithZx8211: Boolean = false,
    val compiledWithHfsniff: Boolean = false,
    val compiledWithHfplot: Boolean = false,
    val compiledWithIso14443a: Boolean = false,
    val compiledWithIso14443b: Boolean = false,
    val compiledWithIso15693: Boolean = false,
    val compiledWithFelica: Boolean = false,
    val compiledWithLegicrf: Boolean = false,
    val compiledWithIclass: Boolean = false,
    val compiledWithSeos: Boolean = false,
    val compiledWithNfcBarcode: Boolean = false,
    val compiledWithLcd: Boolean = false,
    val hwAvailableFlash: Boolean = false,
    val hwAvailableSmartcard: Boolean = false,
    val isRdv4: Boolean = false,
    val hwAvailableFpgaFlash: Boolean = false,
    val hwAvailableI2cEeprom: Boolean = false,
    val isPm5: Boolean = false,
    val isPm5StdAnt: Boolean = false,
    val maxCmdDataSize: Int = 512,
    val emSize: Int = 0,
    val emAllocated: Boolean = false,
    val compiledWithBwm: Boolean = false,
    val compiledWithCep: Boolean = false,
    val fallbackVersionText: String = ""
) {
    val modelLabel: String
        get() = when {
            capabilitiesKnown && isPm5 -> "Proxmark5 (PM5)"
            capabilitiesKnown && isRdv4 -> "Proxmark3 RDV4"
            capabilitiesKnown -> "Proxmark3 Generic"
            fallbackVersionText.contains("PM5", ignoreCase = true) -> "Proxmark5 (fallback)"
            fallbackVersionText.isNotBlank() -> "Proxmark3 (fallback)"
            else -> "Proxmark (Disconnected)"
        }

    companion object {
        fun unknown(): DeviceCapabilities = DeviceCapabilities()

        /**
         * Fallback used when CMD_CAPABILITIES fails or reports a version != CAPABILITIES_VERSION.
         * Leaves all optional hardware flags false so PM5/RDV4-only UI and commands stay hidden.
         */
        fun fromVersionFallback(versionInfo: VersionInfo?): DeviceCapabilities =
            DeviceCapabilities(
                capabilitiesKnown = false,
                fallbackVersionText = versionInfo?.versionString ?: ""
            )

        /**
         * Sequential field-by-field parser matching `capabilities_t` in include/pm3_cmd.h
         * (`CAPABILITIES_VERSION == 13`, 18 packed bytes little-endian).
         */
        fun fromCapabilitiesPayload(
            payload: ByteArray,
            versionInfo: VersionInfo? = null
        ): DeviceCapabilities {
            if (payload.size < 18) return fromVersionFallback(versionInfo)
            val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

            val version = buf.get().toInt() and 0xFF
            if (version != Pm5Constants.CAPABILITIES_VERSION) {
                return fromVersionFallback(versionInfo).copy(version = version)
            }

            val baudrate = buf.int.toLong() and 0xFFFFFFFFL
            val bigbufSize = buf.int.toLong() and 0xFFFFFFFFL

            // Sequential bit reader across the 30-bit PACKED bool bitfield block ([9..12])
            val flagsWord = buf.int
            var bitCursor = 0
            fun nextFlag(): Boolean {
                val set = ((flagsWord ushr bitCursor) and 1) == 1
                bitCursor++
                return set
            }

            val viaFpc = nextFlag()
            val viaUsb = nextFlag()
            val compiledWithFlash = nextFlag()
            val compiledWithSmartcard = nextFlag()
            val compiledWithFpcUsart = nextFlag()
            val compiledWithFpcUsartDev = nextFlag()
            val compiledWithFpcUsartHost = nextFlag()
            val compiledWithLf = nextFlag()
            val compiledWithHitag = nextFlag()
            val compiledWithEm4x50 = nextFlag()
            val compiledWithEm4x70 = nextFlag()
            val compiledWithZx8211 = nextFlag()
            val compiledWithHfsniff = nextFlag()
            val compiledWithHfplot = nextFlag()
            val compiledWithIso14443a = nextFlag()
            val compiledWithIso14443b = nextFlag()
            val compiledWithIso15693 = nextFlag()
            val compiledWithFelica = nextFlag()
            val compiledWithLegicrf = nextFlag()
            val compiledWithIclass = nextFlag()
            val compiledWithSeos = nextFlag()
            val compiledWithNfcBarcode = nextFlag()
            val compiledWithLcd = nextFlag()
            val hwAvailableFlash = nextFlag()
            val hwAvailableSmartcard = nextFlag()
            val isRdv4 = nextFlag()
            val hwAvailableFpgaFlash = nextFlag()
            val hwAvailableI2cEeprom = nextFlag()
            val isPm5 = nextFlag()
            val isPm5StdAnt = nextFlag()

            val maxCmdDataSize = buf.short.toInt() and 0xFFFF
            val emSize = buf.short.toInt() and 0xFFFF
            val tailFlags = buf.get().toInt() and 0xFF
            val emAllocated = (tailFlags and 0x01) != 0
            val compiledWithBwm = (tailFlags and 0x02) != 0
            val compiledWithCep = (tailFlags and 0x04) != 0

            return DeviceCapabilities(
                version = version,
                capabilitiesKnown = true,
                baudrate = baudrate,
                bigbufSize = bigbufSize,
                viaFpc = viaFpc,
                viaUsb = viaUsb,
                compiledWithFlash = compiledWithFlash,
                compiledWithSmartcard = compiledWithSmartcard,
                compiledWithFpcUsart = compiledWithFpcUsart,
                compiledWithFpcUsartDev = compiledWithFpcUsartDev,
                compiledWithFpcUsartHost = compiledWithFpcUsartHost,
                compiledWithLf = compiledWithLf,
                compiledWithHitag = compiledWithHitag,
                compiledWithEm4x50 = compiledWithEm4x50,
                compiledWithEm4x70 = compiledWithEm4x70,
                compiledWithZx8211 = compiledWithZx8211,
                compiledWithHfsniff = compiledWithHfsniff,
                compiledWithHfplot = compiledWithHfplot,
                compiledWithIso14443a = compiledWithIso14443a,
                compiledWithIso14443b = compiledWithIso14443b,
                compiledWithIso15693 = compiledWithIso15693,
                compiledWithFelica = compiledWithFelica,
                compiledWithLegicrf = compiledWithLegicrf,
                compiledWithIclass = compiledWithIclass,
                compiledWithSeos = compiledWithSeos,
                compiledWithNfcBarcode = compiledWithNfcBarcode,
                compiledWithLcd = compiledWithLcd,
                hwAvailableFlash = hwAvailableFlash,
                hwAvailableSmartcard = hwAvailableSmartcard,
                isRdv4 = isRdv4,
                hwAvailableFpgaFlash = hwAvailableFpgaFlash,
                hwAvailableI2cEeprom = hwAvailableI2cEeprom,
                isPm5 = isPm5,
                isPm5StdAnt = isPm5StdAnt,
                maxCmdDataSize = maxCmdDataSize,
                emSize = emSize,
                emAllocated = emAllocated,
                compiledWithBwm = compiledWithBwm,
                compiledWithCep = compiledWithCep,
                fallbackVersionText = versionInfo?.versionString ?: ""
            )
        }
    }
}

/**
 * Read-only SPI flash metadata from CMD_FLASHMEM_GET_INFO (0x0148, `spi_flash_t` = 5 bytes).
 */
data class Rdv4FlashInfo(
    val manufacturerId: Int,
    val deviceId: Int,
    val jedecId: Int,
    val pages64k: Int,
    val totalBytes: Int
) {
    val manufacturerName: String
        get() = when (manufacturerId) {
            0xEF -> "Winbond"
            0xC8 -> "GigaDevice"
            0xC2 -> "Macronix"
            0x20 -> "Micron"
            0x1F -> "Atmel / Adesto"
            else -> "0x%02X".format(manufacturerId)
        }

    companion object {
        fun fromPayload(payload: ByteArray): Rdv4FlashInfo? {
            if (payload.size < 5) return null
            val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            val mfg = bb.get().toInt() and 0xFF
            val dev = bb.get().toInt() and 0xFF
            val jedec = bb.short.toInt() and 0xFFFF
            val pages64k = bb.get().toInt() and 0xFF
            return Rdv4FlashInfo(
                manufacturerId = mfg,
                deviceId = dev,
                jedecId = jedec,
                pages64k = pages64k,
                totalBytes = pages64k * 65536
            )
        }
    }
}

/**
 * Read-only smartcard ATR info from CMD_SMART_ATR (0x0143, `smart_card_atr_t`).
 */
data class SmartcardAtrInfo(
    val cardPresent: Boolean,
    val atrHex: String,
    val message: String
) {
    companion object {
        fun fromPayload(status: Int, payload: ByteArray): SmartcardAtrInfo {
            if (status != Pm5Constants.PM3_SUCCESS || payload.isEmpty()) {
                return SmartcardAtrInfo(
                    cardPresent = false,
                    atrHex = "—",
                    message = "No smartcard present (${Pm5Constants.statusToString(status)})"
                )
            }
            val atrLen = (payload[0].toInt() and 0xFF).coerceAtMost(payload.size - 1)
            if (atrLen <= 0) {
                return SmartcardAtrInfo(
                    cardPresent = false,
                    atrHex = "—",
                    message = "Empty ATR returned"
                )
            }
            val hex = (1..atrLen).joinToString(" ") { idx -> "%02X".format(payload[idx]) }
            return SmartcardAtrInfo(
                cardPresent = true,
                atrHex = hex,
                message = "ISO 7816 ATR ($atrLen bytes)"
            )
        }
    }
}
