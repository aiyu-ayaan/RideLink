package com.aiyu.ridelink.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameDecoderTest {
    private fun frame(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test fun statusFrameShowsWhatTheIconsGet() {
        val s = ClusterProtocol.PhoneStatus(85, 3, 4, 9, 5, 7, true, 4, 11, 2026, missedCalls = 2)
        val line = FrameDecoder.decode(ClusterProtocol.status(ClusterModel.NTORQ_U812, s))!!
        assertTrue(line, line.startsWith("STATUS signal=3 batt=5 "))
        assertTrue(line, "missed=2 net=4 " in line)
    }

    @Test fun speedometer1ShowsTripAndOdometer() {
        val line = FrameDecoder.decode(frame("5a 10 3e 00 06 b6 07 29 00 58 00 00 01 00 00 00 7b 1f 40 ff"))!!
        assertTrue(line, "odo=1718 " in line)
        assertTrue(line, "trip=123 " in line)
        assertTrue(line, "switch=1 " in line)
    }

    @Test fun textAndSocialFrames() {
        assertEquals("TEXT caller 'Mom'", FrameDecoder.decode(ClusterProtocol.caller("Mom")))
        assertEquals(
            "SOCIAL whatsapp=5/2 facebook=3 instagram=9",
            FrameDecoder.decode(ClusterProtocol.socialCounts(ClusterProtocol.SocialCounts(5, 2, 3, 9))),
        )
        assertEquals("SCREEN id=0x78", FrameDecoder.decode(ClusterProtocol.screen(ClusterProtocol.Screen.WEATHER)))
    }

    @Test fun unknownFramesAreNull() {
        assertNull(FrameDecoder.decode(frame("5a 6b 01 02")))
        assertNull(FrameDecoder.decode(frame("5a")))
    }
}
