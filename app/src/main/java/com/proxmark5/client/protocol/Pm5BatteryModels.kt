package com.proxmark5.client.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Compact BWM battery telemetry parsed from CMD_PM5_BWM_GET_BATTERY (0x0184)
 * Wire struct: 18 bytes packed
 */
data class Pm5BatteryInfo(
    val bwmPresent: Boolean,
    val gaugeOk: Boolean,
    val socPct: Int,               // State of charge (0 - 100%)
    val voltageMv: Int,            // Cell voltage in millivolts
    val currentMa: Int,            // Current in mA (+charge / -discharge)
    val remainingMah: Int,         // Remaining capacity in mAh
    val fullChargeMah: Int,        // Full charge capacity in mAh
    val designCapMah: Int,         // Design capacity in mAh
    val tempC: Float,              // Temperature in Celsius (temp_c10 / 10.0f)
    val chargerFault: Int,         // AW32001E fault flags
    val chargeStatus: Int,         // 0=not charging, 1=pre-charge, 2=charging, 3=done
    val healthPct: Int             // Battery health percentage
) {
    val isCharging: Boolean get() = chargeStatus in 1..2 || currentMa > 0
    val chargeStatusLabel: String get() = when (chargeStatus) {
        0 -> if (currentMa < -5) "Discharging" else "Idle / Standby"
        1 -> "Pre-charging"
        2 -> "Fast Charging"
        3 -> "Full / Charged"
        else -> "Status ($chargeStatus)"
    }

    val voltageFormatted: String get() = "%.2f V".format(voltageMv / 1000.0f)
    val currentFormatted: String get() = "%+d mA".format(currentMa)
    val tempFormatted: String get() = "%.1f °C".format(tempC)

    companion object {
        fun fromBytes(payload: ByteArray): Pm5BatteryInfo? {
            if (payload.size < 19) return null
            val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

            val bwmPresent = bb.get() != 0.toByte()
            val gaugeOk = bb.get() != 0.toByte()
            val soc = bb.short.toInt() and 0xFFFF
            val volt = bb.short.toInt() and 0xFFFF
            val curr = bb.short.toInt() // signed 16-bit
            val rem = bb.short.toInt() and 0xFFFF
            val full = bb.short.toInt() and 0xFFFF
            val design = bb.short.toInt() and 0xFFFF
            val tempC10 = bb.short.toInt() // tenths of a degree C
            val fault = bb.get().toInt() and 0xFF
            val status = bb.get().toInt() and 0xFF
            val health = bb.get().toInt() and 0xFF

            return Pm5BatteryInfo(
                bwmPresent = bwmPresent,
                gaugeOk = gaugeOk,
                socPct = soc,
                voltageMv = volt,
                currentMa = curr,
                remainingMah = rem,
                fullChargeMah = full,
                designCapMah = design,
                tempC = tempC10 / 10.0f,
                chargerFault = fault,
                chargeStatus = status,
                healthPct = health
            )
        }
    }
}

/**
 * Auto-off status from CMD_PM5_BWM_AUTOOFF (0x017B)
 */
data class Pm5AutoOffStatus(
    val enabled: Boolean,
    val idleSeconds: Long,
    val idleNowSeconds: Long,
    val link: Int,
    val bleLive: Int,
    val usb: Boolean,
    val usbSeen: Boolean,
    val persisted: Boolean,
    val unplug: Boolean
) {
    companion object {
        fun fromBytes(payload: ByteArray): Pm5AutoOffStatus? {
            if (payload.size < 15) return null
            val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            val enabled = bb.get() != 0.toByte()
            val idleS = bb.int.toLong() and 0xFFFFFFFFL
            val idleNowS = bb.int.toLong() and 0xFFFFFFFFL
            val link = bb.get().toInt() and 0xFF
            val bleLive = bb.get().toInt() and 0xFF
            val usb = bb.get() != 0.toByte()
            val usbSeen = bb.get() != 0.toByte()
            val persisted = bb.get() != 0.toByte()
            val unplug = if (bb.hasRemaining()) bb.get() != 0.toByte() else true

            return Pm5AutoOffStatus(
                enabled = enabled,
                idleSeconds = idleS,
                idleNowSeconds = idleNowS,
                link = link,
                bleLive = bleLive,
                usb = usb,
                usbSeen = usbSeen,
                persisted = persisted,
                unplug = unplug
            )
        }
    }
}

/**
 * Antenna resonance tuning snapshot from CMD_MEASURE_ANTENNA_TUNING (0x0400)
 */
data class Pm5AntennaTuning(
    val lf125Mv: Long,
    val lf134Mv: Long,
    val hfMv: Long,
    val peakMv: Long,
    val peakDivisor: Long
) {
    val lf125V: Float get() = lf125Mv / 1000.0f
    val lf134V: Float get() = lf134Mv / 1000.0f
    val hfV: Float get() = hfMv / 1000.0f
    val peakV: Float get() = peakMv / 1000.0f
}
