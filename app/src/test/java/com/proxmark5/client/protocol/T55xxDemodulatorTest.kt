package com.proxmark5.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T55xxDemodulatorTest {

    private fun loadDump(name: String): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream("t55xx_dumps/$name")
            ?: error("Missing test resource: t55xx_dumps/$name")
        return stream.use { it.readBytes() }
    }

    @Test
    fun `no-card, wrong-password, and empty BigBuf captures fail with could not decode`() {
        val noCard = loadDump("rdv4_no_card.bin")
        val wrongPwd = loadDump("rdv4_wrong_pwd.bin")

        val resNoCard = T55xxDemodulator.demodulate(noCard, isBlock0 = true)
        assertTrue(resNoCard is T55xxDemodResult.Failure)
        assertEquals("could not decode", (resNoCard as T55xxDemodResult.Failure).reason)

        val resWrongPwd = T55xxDemodulator.verifyTwoReads(wrongPwd, wrongPwd, isBlock0 = true)
        assertTrue(resWrongPwd is T55xxDemodResult.Failure)
        assertEquals("could not decode", (resWrongPwd as T55xxDemodResult.Failure).reason)

        val resEmpty = T55xxDemodulator.demodulate(ByteArray(0), isBlock0 = true)
        assertTrue(resEmpty is T55xxDemodResult.Failure)
        assertEquals("could not decode", (resEmpty as T55xxDemodResult.Failure).reason)
    }

    @Test
    fun `blank T5577 block 0 and EM4100 block 0 and block 1 decode across two reads`() {
        val blankB0 = loadDump("rdv4_blank_t5577_b0.bin")
        val emB0 = loadDump("rdv4_em4100_t5577_b0.bin")
        val emB1 = loadDump("rdv4_em4100_t5577_b1.bin")

        val blankRes = T55xxDemodulator.verifyTwoReads(blankB0, blankB0, isBlock0 = true)
        assertTrue(blankRes is T55xxDemodResult.Success)
        val blankOk = blankRes as T55xxDemodResult.Success
        assertEquals(0x000880E0L, blankOk.blockValue)
        assertEquals(32, blankOk.clock)
        assertEquals(T55xxModulation.MANCHESTER, blankOk.modulation)
        assertEquals("Manchester, RF/32, MaxBlk:7", T55xxPayloadBuilder.decodeT55xxBlock0(blankOk.blockValue))

        val emB0Res = T55xxDemodulator.verifyTwoReads(emB0, emB0, isBlock0 = true)
        assertTrue(emB0Res is T55xxDemodResult.Success)
        val emB0Ok = emB0Res as T55xxDemodResult.Success
        assertEquals(0x00148040L, emB0Ok.blockValue)
        assertEquals(64, emB0Ok.clock)
        assertEquals(T55xxModulation.MANCHESTER, emB0Ok.modulation)
        assertEquals(
            "Manchester, RF/64, MaxBlk:2, EM4100 Standard",
            T55xxPayloadBuilder.decodeT55xxBlock0(emB0Ok.blockValue)
        )

        val emB1Res = T55xxDemodulator.verifyTwoReads(emB1, emB1, isBlock0 = false)
        assertTrue(emB1Res is T55xxDemodResult.Success)
        val emB1Ok = emB1Res as T55xxDemodResult.Success
        assertEquals(0xFF806028L, emB1Ok.blockValue)
        assertEquals(64, emB1Ok.clock)
    }

    @Test
    fun `mismatched two reads fail and Mode 2 write_1 timing equals 40 times 8`() {
        val blankB0 = loadDump("rdv4_blank_t5577_b0.bin")
        val emB0 = loadDump("rdv4_em4100_t5577_b0.bin")

        val mismatch = T55xxDemodulator.verifyTwoReads(blankB0, emB0, isBlock0 = true)
        assertTrue(mismatch is T55xxDemodResult.Failure)
        assertEquals("could not decode", (mismatch as T55xxDemodResult.Failure).reason)

        val defaultTimings = T55xxTimingConfig.defaultTimings()
        assertEquals(40 * 8, defaultTimings.modes[2].write1)
    }

    @Test
    fun `real physical T5577 card BigBuf capture decodes block 0 0x00148040`() {
        val realB0 = loadDump("rdv4_real_t5577_b0_00148040.bin")

        val singleRes = T55xxDemodulator.demodulate(realB0, isBlock0 = true)
        assertTrue(singleRes is T55xxDemodResult.Success)
        val ok = singleRes as T55xxDemodResult.Success
        assertEquals(0x00148040L, ok.blockValue)
        assertEquals(64, ok.clock)
        assertEquals(T55xxModulation.MANCHESTER, ok.modulation)
        assertEquals(
            "Manchester, RF/64, MaxBlk:2, EM4100 Standard",
            T55xxPayloadBuilder.decodeT55xxBlock0(ok.blockValue)
        )

        val verifiedRes = T55xxDemodulator.verifyTwoReads(realB0, realB0, isBlock0 = true)
        assertTrue(verifiedRes is T55xxDemodResult.Success)
        assertEquals(0x00148040L, (verifiedRes as T55xxDemodResult.Success).blockValue)
    }
}
