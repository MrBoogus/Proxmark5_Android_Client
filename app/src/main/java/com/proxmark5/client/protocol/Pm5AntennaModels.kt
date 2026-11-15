package com.proxmark5.client.protocol

/**
 * PM5 Swappable Antenna Controller (I2C Slave 0x51).
 * Register 0x02: IO Mapping Register:
 * Bit 7: 125 kHz enable (1 = on, 0 = off)
 * Bit 6: 134 kHz enable (1 = on, 0 = off)
 * Bit 5: 250 kHz enable (1 = on, 0 = off)
 * Bit 4: 375 kHz enable (1 = on, 0 = off)
 * Bit 3: 500 kHz enable (1 = on, 0 = off)
 * Bit 2: HF LED (1 = ON, 0 = OFF) - Physical blue LED on antenna
 * Bit 1: LF LED (1 = ON, 0 = OFF) - Physical blue LED on antenna
 * Bit 0: Q Value (1 = High Q, 0 = Low Q)
 */
data class Pm5AntennaConfig(
    val freq125k: Boolean = true,
    val freq134k: Boolean = false,
    val freq250k: Boolean = false,
    val freq375k: Boolean = false,
    val freq500k: Boolean = false,
    val hfLed: Boolean = false,
    val lfLed: Boolean = false,
    val highQ: Boolean = false
) {
    val rawByte: Int
        get() {
            var b = 0
            if (freq125k) b = b or 0x80
            if (freq134k) b = b or 0x40
            if (freq250k) b = b or 0x20
            if (freq375k) b = b or 0x10
            if (freq500k) b = b or 0x08
            if (hfLed) b = b or 0x04
            if (lfLed) b = b or 0x02
            if (highQ) b = b or 0x01
            return b
        }

    fun toByte(): Byte = rawByte.toByte()

    val activeFreqString: String
        get() = when {
            freq125k -> "125 kHz"
            freq134k -> "134 kHz"
            freq250k -> "250 kHz"
            freq375k -> "375 kHz"
            freq500k -> "500 kHz"
            else -> "Custom / None"
        }

    companion object {
        fun fromRaw(raw: Int): Pm5AntennaConfig = Pm5AntennaConfig(
            freq125k = (raw and 0x80) != 0,
            freq134k = (raw and 0x40) != 0,
            freq250k = (raw and 0x20) != 0,
            freq375k = (raw and 0x10) != 0,
            freq500k = (raw and 0x08) != 0,
            hfLed = (raw and 0x04) != 0,
            lfLed = (raw and 0x02) != 0,
            highQ = (raw and 0x01) != 0
        )
    }
}

/**
 * Result of CMD_PM5_QC_TEST_HW (0x0177)
 * tests Antenna (0x51), Mainboard RGB (0x48), LEDs, buzzer, button
 */
data class Pm5QcTestResult(
    val success: Boolean,
    val failedItem: Int,
    val message: String
)
