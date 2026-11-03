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

    @Test fun screenSwitchFrame() {
        assertEquals(
            "5b 4c 00 78 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 ff",
            hex(ClusterProtocol.screen(ClusterProtocol.Screen.WEATHER)),
        )
        assertEquals(0x00, ClusterProtocol.screen(ClusterProtocol.Screen.DEFAULT)[3].toInt())
    }

    @Test fun weatherFrameIsSpacePadded() {
        val w = ClusterProtocol.Weather(WeatherKind.RAIN, temperatureC = 24, aqi = 312, rainPercent = 80, description = "Rain")
        // icon 5, AQI 312 = 01 38, rain 80 = 0x50, "Rain" + 9 spaces.
        assertEquals(
            "5b 5d 05 01 38 50 52 61 69 6e 20 20 20 20 20 20 20 20 20 ff",
            hex(ClusterProtocol.weather(ClusterModel.NTORQ_U812, w)),
        )
    }

    @Test fun weatherFrameClampsAndTruncates() {
        val w = ClusterProtocol.Weather(WeatherKind.THUNDERSTORM, 30, aqi = 70_000, rainPercent = 140, description = "Thunderstorms nearby")
        val f = ClusterProtocol.weather(ClusterModel.NTORQ_U812, w)
        assertEquals(20, f.size)
        assertEquals("03 ff ff 64", hex(f.copyOfRange(2, 6)))
        assertEquals("Thunderstorms", String(f, 6, 13))
    }

    @Test fun weatherIconsPerFamily() {
        assertEquals(11, ClusterModel.NTORQ_U812.weatherIcon(WeatherKind.CLEAR_NIGHT))
        assertEquals(16, ClusterModel.NTORQ_U812.weatherIcon(WeatherKind.HAIL))
        assertEquals(2, ClusterModel.NTORQ_U577_PREMIUM.weatherIcon(WeatherKind.CLEAR_NIGHT))
        assertEquals(9, ClusterModel.NTORQ_U577_PREMIUM.weatherIcon(WeatherKind.FOG))
    }

    @Test fun wmoCodesMapToKinds() {
        assertEquals(WeatherKind.CLEAR, WeatherKind.fromWmo(0, isDay = true))
        assertEquals(WeatherKind.CLEAR_NIGHT, WeatherKind.fromWmo(1, isDay = false))
        assertEquals(WeatherKind.RAIN, WeatherKind.fromWmo(81, isDay = true))
        assertEquals(WeatherKind.SNOW, WeatherKind.fromWmo(73, isDay = true))
        assertEquals(WeatherKind.HAIL, WeatherKind.fromWmo(99, isDay = true))
        assertEquals(WeatherKind.FOG, WeatherKind.fromWmo(45, isDay = false))
    }

    @Test fun locationFrame() {
        assertEquals(
            "5b 5c 49 6e 64 69 72 61 6e 61 67 61 72 00 00 00 00 00 00 ff",
            hex(ClusterProtocol.location("Indiranagar")),
        )
    }

    @Test fun locationKeepsUtf8CharactersWhole() {
        // 9 two-byte characters = 18 bytes; only 8 (16 bytes) fit in 17.
        val f = ClusterProtocol.location("ééééééééé")
        assertEquals("éééééééé", String(f, 2, 16, Charsets.UTF_8))
        assertEquals(0, f[18].toInt())
    }

    private val music = ClusterProtocol.Music(
        "Kesariya", "Arijit Singh", ClusterProtocol.PlayState.PAUSED,
        positionMs = 83_000, durationMs = 268_000, volumePercent = 60,
    )

    @Test fun songAndArtistAreTextFrames() {
        assertEquals("5b 62 4b 65 73 61 72 69 79 61 00 00 00 00 00 00 00 00 00 ff", hex(ClusterProtocol.song(music.title)))
        assertEquals("5b 63 41 72 69 6a 69 74 20 53 69 6e 67 68 00 00 00 00 00 ff", hex(ClusterProtocol.artist(music.artist)))
    }

    @Test fun genericPlayStateFrame() {
        assertEquals(
            "5b 60 00 01 00 00 00 3c 00 00 00 00 00 00 00 00 00 00 00 ff",
            hex(ClusterProtocol.playState(ClusterModel.NTORQ_U812, music)),
        )
        val stopped = ClusterProtocol.playState(ClusterModel.NTORQ_U812, music.copy(state = ClusterProtocol.PlayState.STOPPED))
        assertEquals(4, stopped[3].toInt())
        assertEquals(0, ClusterProtocol.playState(ClusterModel.NTORQ_U812, music.copy(state = ClusterProtocol.PlayState.PLAYING))[3].toInt())
    }

    @Test fun u577PlayStateCarriesProgress() {
        // 83 s = 1:23, 268 s = 4:28, volume 60 = 0x3c.
        assertEquals(
            "5b 60 00 01 01 01 17 3c 00 00 00 00 04 1c 00 00 00 00 00 ff",
            hex(ClusterProtocol.playState(ClusterModel.NTORQ_U577_PREMIUM, music)),
        )
        val stopped = ClusterProtocol.playState(ClusterModel.NTORQ_U577_BASE, music.copy(state = ClusterProtocol.PlayState.STOPPED))
        assertEquals(254, stopped[3].toInt() and 0xFF)
    }

    @Test fun overspeedAndMissedCallsInStatus() {
        val alert = status.copy(overSpeedKmh = 80, missedCalls = 3)
        // Jupiter family: limit in byte 3; missed calls in byte 10 (stock always sends 0 there, RideLink the count).
        val jupiter = ClusterProtocol.status(ClusterModel.NTORQ_U812, alert)
        assertEquals(80, jupiter[3].toInt())
        assertEquals(3, jupiter[10].toInt())
        // Older Ntorq: limit in byte 3 (0xFF = off), missed calls in byte 10.
        val ntorq = ClusterProtocol.status(ClusterModel.NTORQ_U577_PREMIUM, alert)
        assertEquals(80, ntorq[3].toInt())
        assertEquals(3, ntorq[10].toInt())
        assertEquals(0xFF, ClusterProtocol.status(ClusterModel.NTORQ_U577_PREMIUM, status)[3].toInt() and 0xFF)
    }

    @Test fun customTextLines() {
        assertEquals("5b 4c 52 69 64 65 20 73 61 66 65 00 00 00 00 00 00 00 00 ff", hex(ClusterProtocol.customLine1("Ride safe")))
        assertEquals("5b 63 4b 41 31 32 41 42 31 32 33 34 00 00 00 00 00 00 00 ff", hex(ClusterProtocol.customLine2("KA12AB1234")))
        // Never starts with 00, so it cannot be mistaken for the 5B 4C 00 <id> screen switch.
        assertEquals(0x4B, ClusterProtocol.customLine1("Kesari@").let { it[2].toInt() })
    }
}
