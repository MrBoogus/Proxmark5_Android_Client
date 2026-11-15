package com.proxmark5.client.protocol

import com.proxmark5.client.data.CapturedTag
import com.proxmark5.client.data.SoftwareAesGcmVaultAead
import com.proxmark5.client.data.TagStorageManager
import com.proxmark5.client.data.TinkLegacyVaultMigrator
import com.proxmark5.client.data.VaultResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ProtocolUnitTest {

    @Test
    fun `crc16 computes deterministic ISO14443A checksum and validates postamble magic`() {
        val sample = "PM3a".toByteArray(Charsets.US_ASCII)
        val crc = Pm5Crc16.compute(sample)
        assertTrue(crc in 1..0xFFFF)
        assertTrue(Pm5Crc16.validate(sample, 0, sample.size, crc))
        assertTrue(Pm5Crc16.validate(sample, 0, sample.size, Pm5Constants.RESPONSENG_POSTAMBLE_MAGIC))
        assertFalse(Pm5Crc16.validate(sample, 0, sample.size, crc xor 0xFFFF))
        // Standard CRC-16/ISO14443-A check vector for "123456789" -> 0xBF05 little-endian (first=0x05, second=0xBF)
        assertEquals(0x05BF, Pm5Crc16.compute("123456789".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `command frame builder serializes 8-byte preamble payload and 2-byte crc`() {
        val payload = byteArrayOf(0x10, 0x20, 0x30)
        val frame = Pm5CommandFrameBuilder.build(Pm5Constants.CMD_PM5_RGB_SET, payload, useCrc = true)
        assertEquals(8 + 3 + 2, frame.size)

        val bb = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(Pm5Constants.COMMANDNG_PREAMBLE_MAGIC, bb.int)
        val lenAndNg = bb.short.toInt() and 0xFFFF
        assertEquals(3, lenAndNg and 0x7FFF)
        assertTrue((lenAndNg and 0x8000) != 0)
        assertEquals(Pm5Constants.CMD_PM5_RGB_SET, bb.short.toInt() and 0xFFFF)
    }

    @Test
    fun `frame parser reassembles fragmented chunks and discards leading noise`() {
        val received = mutableListOf<Pm5ResponseFrame>()
        val errors = mutableListOf<String>()
        val parser = Pm5FrameParser(
            onFrameReceived = { received.add(it) },
            onParseError = { errors.add(it) }
        )

        val payload = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
        val rawFrame = ByteBuffer.allocate(10 + payload.size + 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(Pm5Constants.RESPONSENG_PREAMBLE_MAGIC)
            putShort(((payload.size and 0x7FFF) or 0x8000).toShort())
            put(Pm5Constants.PM3_SUCCESS.toByte())
            put(0.toByte())
            putShort(Pm5Constants.CMD_PING.toShort())
            put(payload)
        }
        val crc = Pm5Crc16.compute(rawFrame.array(), 0, 10 + payload.size)
        rawFrame.putShort(crc.toShort())
        val bytes = rawFrame.array()

        // Prepend 3 bytes of garbage noise and feed in 2-byte fragments
        val stream = byteArrayOf(0xFF.toByte(), 0x00, 0x55) + bytes
        var offset = 0
        while (offset < stream.size) {
            val chunkLen = minOf(2, stream.size - offset)
            parser.onBytesReceived(stream.copyOfRange(offset, offset + chunkLen), chunkLen)
            offset += chunkLen
        }

        assertEquals(0, errors.size)
        assertEquals(1, received.size)
        val parsed = received.first()
        assertEquals(Pm5Constants.CMD_PING, parsed.cmd)
        assertEquals(Pm5Constants.PM3_SUCCESS.toByte(), parsed.status)
        assertTrue(parsed.ng)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun `battery info decoder parses 19-byte bwm_battery_info_t accurately`() {
        val bb = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(1.toByte())           // bwm_present = true
            put(1.toByte())           // gauge_ok = true
            putShort(84.toShort())    // soc_pct = 84%
            putShort(4085.toShort())  // voltage_mv = 4085 mV
            putShort((-145).toShort())// current_ma = -145 mA (discharging)
            putShort(670.toShort())   // remaining_mah
            putShort(800.toShort())   // full_charge_mah
            putShort(800.toShort())   // design_cap_mah
            putShort(295.toShort())   // temp_c10 = 29.5 C
            put(0.toByte())           // charger_fault = 0
            put(0.toByte())           // charge_status = 0 (discharging)
            put(98.toByte())          // health_pct = 98%
        }

        val info = Pm5BatteryInfo.fromBytes(bb.array())
        assertNotNull(info)
        info!!
        assertTrue(info.bwmPresent)
        assertTrue(info.gaugeOk)
        assertEquals(84, info.socPct)
        assertEquals(4085, info.voltageMv)
        assertEquals(-145, info.currentMa)
        assertEquals(670, info.remainingMah)
        assertEquals(800, info.fullChargeMah)
        assertEquals(800, info.designCapMah)
        assertEquals(29.5f, info.tempC, 0.01f)
        assertEquals(98, info.healthPct)
        assertFalse(info.isCharging)
        assertNull(Pm5BatteryInfo.fromBytes(ByteArray(18)))
    }

    @Test
    fun `auto-off status decoder parses 15-byte bwm_autooff_status_t accurately`() {
        val bb = ByteBuffer.allocate(15).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(1.toByte())        // enabled
            putInt(600)            // idle_s
            putInt(42)             // idle_now_s
            put(2.toByte())        // link
            put(2.toByte())        // ble_live
            put(0.toByte())        // usb
            put(1.toByte())        // usb_seen
            put(1.toByte())        // persisted
            put(1.toByte())        // unplug
        }
        val status = Pm5AutoOffStatus.fromBytes(bb.array())
        assertNotNull(status)
        status!!
        assertTrue(status.enabled)
        assertEquals(600L, status.idleSeconds)
        assertEquals(42L, status.idleNowSeconds)
        assertEquals(2, status.link)
        assertTrue(status.persisted)
        assertTrue(status.unplug)
        assertNull(Pm5AutoOffStatus.fromBytes(ByteArray(14)))
    }

    @Test
    fun `antenna config register 0x02 bitmask round-trips cleanly`() {
        val cfg = Pm5AntennaConfig(
            freq125k = true,
            freq134k = false,
            freq250k = false,
            freq375k = false,
            freq500k = false,
            hfLed = true,
            lfLed = true,
            highQ = true
        )
        assertEquals(0x87, cfg.rawByte)
        val decoded = Pm5AntennaConfig.fromRaw(0x87)
        assertEquals(cfg, decoded)
        assertEquals("125 kHz", decoded.activeFreqString)
    }

    @Test
    fun `badge parser decodes HID EM410x AWID and IO Prox logs`() {
        val hid = BadgeParser.parse("[+] [H10301  ] HID H10301 26-bit                FC: 118  Card: 1603")
            ?: BadgeParser.parse("[+] TAG ID: 2006ec0c86 (1603) - Format Len: 26 bit - FC: 118 - Card: 1603")
        assertNotNull(hid)
        assertEquals("2006EC0C86", hid!!.uidHex)
        assertEquals("118", hid.facilityCode)
        assertEquals("1603", hid.cardId)

        val em = BadgeParser.parse("[+] EM TAG ID: 0102030405 - ( 01029_003_00263173 )")
        assertNotNull(em)
        assertEquals("0102030405", em!!.uidHex)

        val awid = BadgeParser.parse("[+] AWID Found - Bit length: 26, FC: 50, Card: 1234 - Wiegand: 06409a4, Raw: 011d806409a40000")
        assertNotNull(awid)
        assertEquals("50", awid!!.facilityCode)
        assertEquals("1234", awid.cardId)

        val io = BadgeParser.parse("[+] IO Prox XSF(01)be:12345 (0078563412)")
        assertNotNull(io)
        assertEquals("12345", io!!.cardId)
    }

    @Test
    fun `em4100 decoder computes 64-bit manchester frame and 12-byte clone payload`() {
        val tag = Em4100Decoder.fromHex("0102030405")
        assertNotNull(tag)
        tag!!
        assertEquals("0102030405", tag.uidHex)
        assertEquals(3, tag.facilityCode)
        assertEquals(0x0405, tag.cardNumber)
        assertTrue(tag.parity.isValid)
        assertEquals(64, tag.parity.raw64Bits.length)
        assertTrue(tag.parity.raw64Bits.startsWith("111111111"))
        assertTrue(tag.parity.raw64Bits.endsWith("0"))

        val payload = Em4100Decoder.buildClonePayload(
            uid = tag.uidLong,
            targetChip = Pm5Constants.TARGET_T55X7,
            clock = 64,
            addElectra = false
        )
        assertEquals(12, payload.size)
    }

    @Test
    fun `hf card decoder parses iso14a_card_select_t for MIFARE Classic and DESFire`() {
        val bb = ByteBuffer.allocate(4 + 15 + 5).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(20.toShort()) // respLen
            put(1.toByte())        // sel = 1 (OK w/ ATS)
            put(0.toByte())        // rfu
            // uid[10]
            val uid = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x00, 0x00, 0x00)
            put(uid)
            put(7.toByte())        // uidlen = 7
            put(0x44.toByte())     // atqa[0]
            put(0x03.toByte())     // atqa[1]
            put(0x20.toByte())     // sak = 0x20 (DESFire)
            put(5.toByte())        // ats_len = 5
            put(byteArrayOf(0x75, 0x77, 0x81.toByte(), 0x02, 0x80.toByte()))
        }

        val card = HfCardDecoder.parseIso14a(bb.array())
        assertNotNull(card)
        card!!
        assertEquals("04112233445566", card.uidHex)
        assertEquals(7, card.uidLen)
        assertEquals("0344", card.atqaHex)
        assertEquals("20", card.sakHex)
        assertEquals("7577810280", card.atsHex)
        assertTrue(card.cardType.contains("DESFire"))
    }

    @Test
    fun `t55xx payload builders match firmware struct layouts`() {
        val readPayload = T55xxPayloadBuilder.buildReadBlock(
            blockNo = 3,
            page = 0,
            password = 0x51243648L,
            usePwd = true,
            downlinkMode = Pm5Constants.T55XX_DLMODE_LLR
        )
        assertEquals(8, readPayload.size)
        val rbb = ByteBuffer.wrap(readPayload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x51243648, rbb.int)
        assertEquals(3, rbb.get().toInt() and 0xFF)
        assertEquals(0, rbb.get().toInt() and 0xFF)
        assertEquals(1, rbb.get().toInt() and 0xFF)
        assertEquals(Pm5Constants.T55XX_DLMODE_LLR, rbb.get().toInt() and 0xFF)

        val writePayload = T55xxPayloadBuilder.buildWriteBlock(
            blockNo = 1,
            value = 0xDEADBEEFL,
            page = 1,
            password = 0x19920427L,
            usePwd = true,
            testMode = true,
            downlinkMode = Pm5Constants.T55XX_DLMODE_1OF4
        )
        assertEquals(10, writePayload.size)
        val wbb = ByteBuffer.wrap(writePayload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0xDEADBEEF.toInt(), wbb.int)
        assertEquals(0x19920427, wbb.int)
        assertEquals(1, wbb.get().toInt() and 0xFF)
        val expectedFlags = 0x01 or 0x02 or 0x04 or (Pm5Constants.T55XX_DLMODE_1OF4 shl 3)
        assertEquals(expectedFlags, wbb.get().toInt() and 0xFF)

        assertEquals(5, T55xxPayloadBuilder.buildWakeup(0x12345678L, 0).size)
        assertEquals(1, T55xxPayloadBuilder.buildResetRead(0).size)
        assertEquals(133, T55xxPayloadBuilder.buildDangerRaw("101010", 500).size)
        assertEquals(58, T55xxTimingConfig.defaultTimings(persist = false).toWireBytes().size)
        assertTrue(T55xxPayloadBuilder.decodeT55xxBlock0(0x00148040L).contains("EM4100 Standard"))
    }

    @Test
    fun `software vault aead encrypts decrypts and rejects tampering or wrong AAD`() {
        val key = ByteArray(32) { (it * 7 + 3).toByte() }
        val aead = SoftwareAesGcmVaultAead(key)
        val plaintext = """[{"uidHex":"04112233445566","technology":"MIFARE DESFire"}]""".toByteArray(StandardCharsets.UTF_8)

        val envelope = aead.encrypt(plaintext, TagStorageManager.MAGIC_V2)
        assertArrayEquals(TagStorageManager.MAGIC_V2, envelope.copyOfRange(0, TagStorageManager.MAGIC_V2.size))
        assertArrayEquals(plaintext, aead.decrypt(envelope, TagStorageManager.MAGIC_V2))

        val tampered = envelope.clone().apply { this[lastIndex] = (this[lastIndex].toInt() xor 0x01).toByte() }
        try {
            aead.decrypt(tampered, TagStorageManager.MAGIC_V2)
            fail("Expected GeneralSecurityException on tampered ciphertext")
        } catch (_: GeneralSecurityException) {
            // Expected
        }

        try {
            aead.decrypt(envelope, "WRONG!".toByteArray(StandardCharsets.US_ASCII))
            fail("Expected GeneralSecurityException on wrong AAD")
        } catch (_: GeneralSecurityException) {
            // Expected
        }

        try {
            aead.decrypt(TagStorageManager.MAGIC_V2 + ByteArray(8), TagStorageManager.MAGIC_V2)
            fail("Expected GeneralSecurityException on truncated envelope")
        } catch (_: GeneralSecurityException) {
            // Expected
        }
    }

    @Test
    fun `tink legacy stream migrator decrypts single and multi-segment AES256_GCM_HKDF streams`() {
        fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(salt, "HmacSHA256"))
            val prk = mac.doFinal(ikm)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val out = ByteArray(length)
            var prev = ByteArray(0)
            var off = 0
            var ctr = 1
            while (off < length) {
                mac.reset()
                mac.update(prev)
                mac.update(info)
                mac.update(ctr.toByte())
                prev = mac.doFinal()
                val take = minOf(prev.size, length - off)
                System.arraycopy(prev, 0, out, off, take)
                off += take
                ctr++
            }
            return out
        }

        fun buildTinkStream(
            plaintext: ByteArray,
            ikm: ByteArray,
            salt: ByteArray,
            noncePrefix: ByteArray,
            segmentSize: Int,
            aad: ByteArray
        ): ByteArray {
            val headerLen = 1 + 32 + 7
            val subKey = SecretKeySpec(hkdfSha256(ikm, salt, aad, 32), "AES")
            val stream = ByteArrayOutputStream()
            stream.write(headerLen)
            stream.write(salt)
            stream.write(noncePrefix)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            var pos = 0
            var segNr = 0
            while (pos < plaintext.size || segNr == 0) {
                val maxCipherSeg = if (segNr == 0) segmentSize - headerLen else segmentSize
                val maxPlainSeg = maxCipherSeg - 16
                val end = minOf(plaintext.size, pos + maxPlainSeg)
                val isLast = (end == plaintext.size)
                val nonce = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).apply {
                    put(noncePrefix)
                    putInt(segNr)
                    put(if (isLast) 1.toByte() else 0.toByte())
                }.array()
                cipher.init(Cipher.ENCRYPT_MODE, subKey, GCMParameterSpec(128, nonce))
                stream.write(cipher.doFinal(plaintext, pos, end - pos))
                pos = end
                segNr++
            }
            return stream.toByteArray()
        }

        val ikm = ByteArray(32) { (it + 0x10).toByte() }
        val salt = ByteArray(32) { (0xAA - it).toByte() }
        val noncePrefix = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
        val aad = "pm5_saved_tags.json".toByteArray(StandardCharsets.UTF_8)

        val singlePlain = """[{"uidHex":"0102030405","technology":"EM4100"}]""".toByteArray(StandardCharsets.UTF_8)
        val singleStream = buildTinkStream(singlePlain, ikm, salt, noncePrefix, 4096, aad)
        assertArrayEquals(
            singlePlain,
            TinkLegacyVaultMigrator.decryptAesGcmHkdfStream(singleStream, ikm, 32, 4096, aad)
        )

        val multiPlain = "Segment0-Segment1-Segment2-Payload-0123456789ABCDEF".repeat(3).toByteArray(StandardCharsets.UTF_8)
        val multiStream = buildTinkStream(multiPlain, ikm, salt, noncePrefix, 96, aad)
        assertArrayEquals(
            multiPlain,
            TinkLegacyVaultMigrator.decryptAesGcmHkdfStream(multiStream, ikm, 32, 96, aad)
        )
    }

    @Test
    fun `tag storage manager preserves corrupt encrypted file on decrypt failure without overwriting`() {
        val tempFile = File.createTempFile("pm5_vault_corrupt_", ".json")
        try {
            val corruptPayload = TagStorageManager.MAGIC_V2 + ByteArray(48) { (it xor 0x5A).toByte() }
            tempFile.writeBytes(corruptPayload)

            val manager = TagStorageManager(
                context = null,
                storageFileOverride = tempFile,
                vaultAead = SoftwareAesGcmVaultAead(ByteArray(32) { 0x42.toByte() })
            )

            runBlocking {
                val loadResult = manager.loadAll()
                assertTrue(loadResult is VaultResult.Error)

                val saveResult = manager.save(
                    CapturedTag(uidHex = "04112233445566", technology = "MIFARE DESFire", frequency = "HF")
                )
                assertTrue(saveResult is VaultResult.Error)
            }

            assertArrayEquals(corruptPayload, tempFile.readBytes())
        } finally {
            tempFile.delete()
        }
    }
}
