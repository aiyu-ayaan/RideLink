package com.aiyu.ridelink.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterProtocolTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it) }

    @Test fun riderNameIsPaddedTwentyBytes() {
        assertEquals(
            "5b 52 41 79 61 61 6e 00 00 00 00 00 00 00 00 00 00 00 00 ff",
            hex(ClusterProtocol.riderName("Ayaan")),
        )
    }

    @Test fun textReplacesSymbolsAndTruncates() {
        val f = ClusterProtocol.text(ClusterProtocol.DID_TEXT_LINE_1, "Hi! this is way too long")
        assertEquals(20, f.size)
        assertEquals("HiX this is way t", String(f, 2, 17))
    }

    @Test fun callerStripsCountryCode() {
        assertEquals("9876543210", String(ClusterProtocol.caller("+919876543210"), 2, 10))
    }

    private val status = ClusterProtocol.PhoneStatus(
        batteryPercent = 85, signalLevel = 3, networkType = 4,
        hour12 = 9, minute = 5, second = 7, isPm = true, day = 4, month = 11, year = 2026,
        temperatureC = 30,
    )

    @Test fun jupiterFamilyStatusFrame() {
        assertEquals(
            "5b 4a 35 78 46 00 09 05 07 01 00 04 04 0b 1a 00 00 00 00 ff",
            hex(ClusterProtocol.status(ClusterModel.NTORQ_U812, status)),
        )
    }

    @Test fun ntorqFamilyStatusFrame() {
        assertEquals(
            "5b 4a 35 ff 1e 00 09 05 07 00 00 04 04 0b 1a 00 00 00 ff ff",
            hex(ClusterProtocol.status(ClusterModel.NTORQ_U577_PREMIUM, status)),
        )
    }

    @Test fun navigationFrames() {
        val (text, control) = ClusterProtocol.navigation(
            ClusterModel.NTORQ_U812,
            ClusterProtocol.NavStep(300, 600, 70_000, Maneuver.RIGHT, "Turn right"),
        )
        assertEquals("5b 4f 54 75 72 6e 20 72 69 67 68 74 00 00 00 00 00 00 00 ff", hex(text))
        assertEquals("5a 4e 01 2c 00 0a 01 11 70 0a 01 00 00 00 00 00 00 00 00 ff", hex(control))
    }

    @Test fun premiumPictogramUsesHexQuirk() {
        assertEquals(0x10, ClusterModel.NTORQ_U577_PREMIUM.pictogram(Maneuver.RIGHT))
        assertEquals(0x57, ClusterModel.NTORQ_U577_PREMIUM.pictogram(Maneuver.STRAIGHT))
    }

    @Test fun detectsOlderNtorqFromMusicOrWifiFrames() {
        assertEquals(ClusterModel.NTORQ_U577_PREMIUM, ClusterModel.detect(byteArrayOf(0x5A, 0x6B, 1)))
        assertEquals(ClusterModel.NTORQ_U577_PREMIUM, ClusterModel.detect(byteArrayOf(0x5A, 0x29, 1)))
        assertEquals(null, ClusterModel.detect(byteArrayOf(0x5A, 0x10, 1)))
        assertEquals(null, ClusterModel.detect(byteArrayOf(0x5A)))
    }

    @Test fun speedFromSpeedometerFrame() {
        // ParsingMeta(start = 3) is 1-based: speed is index 2, odometer starts at index 3.
        assertEquals(62, ClusterProtocol.speedKmh(byteArrayOf(0x5A, 0x10, 62, 0, 0x30, 0x39)))
        assertEquals(200, ClusterProtocol.speedKmh(byteArrayOf(0x5A, 0x10, 200.toByte(), 0)))
        assertEquals(null, ClusterProtocol.speedKmh(byteArrayOf(0x5A, 0x11, 62, 0)))
        assertEquals(null, ClusterProtocol.speedKmh(byteArrayOf(0x5A, 0x10)))
    }

    @Test fun socialCountsFrame() {
        // WA 5 msgs / 2 chats -> 0b00101_010 = 0x2a; FB 3 -> 0x33; IG 9.
        assertEquals(
            "5b 9c 2a 33 09 00 00 00 00 00 00 00 00 00 00 00 00 00 00 ff",
            hex(ClusterProtocol.socialCounts(ClusterProtocol.SocialCounts(5, 2, 3, 9))),
        )
    }

    @Test fun socialCountsClampToBitFields() {
        // WA msgs cap at 31, chats at 7 -> 0xff; FB caps at 15 -> 0xff; IG caps at 255.
        assertEquals(
            "5b 9c ff ff ff 00 00 00 00 00 00 00 00 00 00 00 00 00 00 ff",
            hex(ClusterProtocol.socialCounts(ClusterProtocol.SocialCounts(40, 9, 20, 300))),
        )
    }
}
