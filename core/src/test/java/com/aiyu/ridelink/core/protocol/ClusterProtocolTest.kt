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
        assertEquals(62, ClusterProtocol.speedKmh(byteArrayOf(0x5A, 0x10, 0, 62, 0)))
        assertEquals(null, ClusterProtocol.speedKmh(byteArrayOf(0x5A, 0x11, 0, 62)))
    }
}
