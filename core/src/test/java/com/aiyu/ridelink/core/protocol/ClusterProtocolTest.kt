package com.aiyu.ridelink.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun plainTextTurnsPunctuationIntoSpaces() {
        assertEquals("WhatsApp Mom", ClusterProtocol.plainText("WhatsApp: Mom"))
        assertEquals("Hi where are you", ClusterProtocol.plainText("Hi, where are you?? 😀"))
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

    /** Maps gives the street; the display gets the direction in words, like the stock Mappls short text. */
    @Test fun navTextIsTheDirectionNotTheStreet() {
        val (text, control) = ClusterProtocol.navigation(
            ClusterModel.NTORQ_U458,
            ClusterProtocol.NavStep(250, 180, 1_800, Maneuver.SHARP_LEFT, "Mohan Khoper Rd"),
        )
        assertEquals("Sharp left", String(text, 2, 10, Charsets.UTF_8))
        assertEquals(0, text[12].toInt())
        assertEquals(1, control[10].toInt())
        Maneuver.entries.forEach { assertTrue(it.name, ClusterProtocol.directionText(it).length <= 17) }
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
            "5b 9c 2a 33 09 00 00 00 00 00 00 00 00 00 00 00 00 00 a2 ff",
            hex(ClusterProtocol.socialCounts(ClusterProtocol.SocialCounts(5, 2, 3, 9))),
        )
    }

    @Test fun callerNumberIsDigitsOnly() {
        assertEquals("9876543210", String(ClusterProtocol.caller("(987) 654-3210"), 2, 10, Charsets.US_ASCII))
        assertEquals("9876543210", String(ClusterProtocol.caller("+91 98765 43210"), 2, 10, Charsets.US_ASCII))
        assertEquals("9876543210", String(ClusterProtocol.caller("\u202A(987) 654-3210\u202C"), 2, 10, Charsets.US_ASCII))
        assertEquals("Mom", String(ClusterProtocol.caller("Mom"), 2, 3, Charsets.US_ASCII))
    }

    @Test fun checksumMatchesStockAddChecksum() {
        // 5B 9C 2A 33 09: sum = 0x5b+0x9c+0x2a+0x33+0x09 = 0x15d; 255 - (0x15d % 256) = 255 - 0x5d = 0xa2.
        val f = ClusterProtocol.socialCounts(ClusterProtocol.SocialCounts(5, 2, 3, 9))
        assertEquals(0xa2, f[18].toInt() and 0xFF)
        assertEquals(0xff, f[19].toInt() and 0xFF)
        // A 17-character title loses its last character to the checksum, as on the stock app.
        assertEquals("ABCDEFGHIJKLMNOP", String(ClusterProtocol.song("ABCDEFGHIJKLMNOPQ"), 2, 16, Charsets.US_ASCII))
    }

    @Test fun socialCountsClampToBitFields() {
        // WA msgs cap at 31, chats at 7 -> 0xff; FB caps at 15 -> 0xff; IG caps at 255.
        assertEquals(
            "5b 9c ff ff ff 00 00 00 00 00 00 00 00 00 00 00 00 00 0b ff",
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
        assertEquals("5b 62 4b 65 73 61 72 69 79 61 00 00 00 00 00 00 00 00 09 ff", hex(ClusterProtocol.song(music.title)))
        assertEquals("5b 63 41 72 69 6a 69 74 20 53 69 6e 67 68 00 00 00 00 c5 ff", hex(ClusterProtocol.artist(music.artist)))
    }

    @Test fun genericPlayStateFrame() {
        assertEquals(
            "5b 60 00 01 00 00 00 3c 00 00 00 00 00 00 00 00 00 00 07 ff",
            hex(ClusterProtocol.playState(ClusterModel.NTORQ_U812, music)),
        )
        val stopped = ClusterProtocol.playState(ClusterModel.NTORQ_U812, music.copy(state = ClusterProtocol.PlayState.STOPPED))
        assertEquals(4, stopped[3].toInt())
        assertEquals(0, ClusterProtocol.playState(ClusterModel.NTORQ_U812, music.copy(state = ClusterProtocol.PlayState.PLAYING))[3].toInt())
    }

    @Test fun u577PlayStateCarriesProgress() {
        // 83 s = 1:23, 268 s = 4:28, volume 60 = 0x3c.
        assertEquals(
            "5b 60 00 01 01 01 17 3c 00 00 00 00 04 1c 00 00 00 00 ce ff",
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

    /** N109U347N251DataSenderToCluster.sendMobileData for the Race XP theme: temp 0, byte 3 = 2, network type 0. */
    @Test fun smartXonnectStatusFrame() {
        assertEquals(
            "5b 4a 35 78 00 02 09 05 07 01 00 00 04 0b 1a 00 00 00 00 ff",
            hex(ClusterProtocol.status(ClusterModel.NTORQ_SMARTXONNECT, status)),
        )
    }

    /** TVSMMapActivity.N251N109U347sendNavigationData: control first (byte 11 = 1, stage in byte 14), then text. */
    @Test fun smartXonnectNavigationFrames() {
        val frames = ClusterProtocol.navigation(
            ClusterModel.NTORQ_SMARTXONNECT,
            ClusterProtocol.NavStep(310, 600, 3_300, Maneuver.LEFT, "Mohan Khoper Rd"),
        )
        assertEquals(2, frames.size)
        assertEquals("5a 4e 01 2c 00 0a 00 0c e4 00 01 01 00 00 01 00 00 00 00 ff", hex(frames[0]))
        assertEquals("5b 4f 54 75 72 6e 20 6c 65 66 74 00 00 00 00 00 00 00 00 ff", hex(frames[1]))
        assertEquals(100L, ClusterProtocol.navGapMs(ClusterModel.NTORQ_SMARTXONNECT))
        assertEquals(2_200L, ClusterProtocol.navResendMs(ClusterModel.NTORQ_SMARTXONNECT))
    }

    @Test fun smartXonnectStageFollowsDistance() {
        fun nav(m: Maneuver, d: Int, stopped: Boolean = false) = ClusterProtocol.navigation(
            ClusterModel.NTORQ_SMARTXONNECT, ClusterProtocol.NavStep(d, 120, 2_000, m, "", stopped),
        )
        fun stage(f: List<ByteArray>) = f[0][14].toInt()
        fun code(f: List<ByteArray>) = f[0][9].toInt() and 0xFF
        fun text(f: List<ByteArray>) = String(f[1], 2, 17, Charsets.UTF_8).trimEnd('\u0000')

        // Chevron stages only at the junction (distance 0); with a distance in the frame the LCD flickered (issue #5).
        assertEquals(1, stage(nav(Maneuver.SHARP_LEFT, 50)))
        assertEquals(1, code(nav(Maneuver.SHARP_LEFT, 50)))
        assertEquals(1, stage(nav(Maneuver.RIGHT, 40)))
        assertEquals(3, stage(nav(Maneuver.SHARP_LEFT, 0)))
        assertEquals(4, stage(nav(Maneuver.RIGHT, 0)))
        assertEquals(1, stage(nav(Maneuver.U_TURN, 30)))
        assertEquals(19, code(nav(Maneuver.U_TURN, 30)))
        // Right family uses the 2025+ arrow ids: the raw 3 / 4 / 5 drew nothing on the rider's Race XP.
        assertEquals(10, code(nav(Maneuver.RIGHT, 300)))
        assertEquals(11, code(nav(Maneuver.SHARP_RIGHT, 300)))
        assertEquals(1, stage(nav(Maneuver.SLIGHT_RIGHT, 600)))
        assertEquals(12, code(nav(Maneuver.SLIGHT_RIGHT, 600)))
        assertEquals(17, code(nav(Maneuver.FORK_RIGHT, 600)))
        assertEquals(7, code(nav(Maneuver.FORK_LEFT, 600)))
        assertEquals(22, code(nav(Maneuver.DESTINATION, 600)))
        assertEquals(48, code(nav(Maneuver.ROUNDABOUT, 600)))
        assertEquals(28, code(nav(Maneuver.ROUNDABOUT_LEFT, 600)))
        assertEquals(32, code(nav(Maneuver.ROUNDABOUT_RIGHT, 600)))
        // A turn over 1 km away and plain straight both show "keep straight" (arrow 57, stage 5); 7 is keep-left.
        nav(Maneuver.RIGHT, 1_500).let { assertEquals(5, stage(it)); assertEquals(57, code(it)); assertEquals("STAY SAME RD", text(it)) }
        nav(Maneuver.STRAIGHT, 200).let { assertEquals(5, stage(it)); assertEquals(57, code(it)) }
        // Arrived: distance 0, stage 8. A cancelled route sends nothing, like stock.
        nav(Maneuver.DESTINATION, 20, stopped = true).let {
            assertEquals(8, stage(it)); assertEquals(0, it[0][2].toInt()); assertEquals(0, it[0][3].toInt()); assertEquals("You Arrived", text(it))
        }
        assertTrue(nav(Maneuver.LEFT, 200, stopped = true).isEmpty())
        // A 0 m straight step shows the route left, not a blank distance (2000 m here).
        nav(Maneuver.STRAIGHT, 0).let { assertEquals(2_000, ((it[0][2].toInt() and 0xFF) shl 8) or (it[0][3].toInt() and 0xFF)) }
    }

    @Test fun smartXonnectDistanceIsRoundedLikeStock() {
        assertEquals(10, ClusterProtocol.smartXonnectDistance(12))
        assertEquals(25, ClusterProtocol.smartXonnectDistance(23))
        assertEquals(100, ClusterProtocol.smartXonnectDistance(120))
        assertEquals(150, ClusterProtocol.smartXonnectDistance(130))
    }

    /** Frames from the rider's 2026 Race XP log: race stats 5A 16 and the display MAC 5B 42. */
    @Test fun detectsSmartXonnectFromRaceStatsOrMacFrame() {
        fun bytes(h: String) = h.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(ClusterModel.NTORQ_SMARTXONNECT, ClusterModel.detect(bytes("5a 16 00 00 00 00 07 17 1e 00 45 00 00 42 96 23 00 00 00 ff")))
        assertEquals(ClusterModel.NTORQ_SMARTXONNECT, ClusterModel.detect(bytes("5b 42 00 00 00 00 00 00 a4 b0 f5 3a 2b b8 00 00 00 00 00 ff")))
        assertNull(ClusterModel.detect(bytes("5a 10 00 00 07 f5 05 00 00 00 09 69 00 00 00 00 00 00 00 ff")))
        assertEquals(false, ClusterModel.NTORQ_SMARTXONNECT.hasMusic)
        assertEquals(true, ClusterModel.NTORQ_SMARTXONNECT.hasLocation)
    }

    /** sendMobileDataNotificationAndAlert(getWhatsappByte(), getFBByte(), getInstaByte(), 0, 0, 0): no checksum. */
    @Test fun smartXonnectSocialFrame() {
        assertEquals(
            "5b 9c 28 30 09 00 00 00 00 00 00 00 00 00 00 00 00 00 00 ff",
            hex(ClusterProtocol.socialCounts(ClusterModel.NTORQ_SMARTXONNECT, ClusterProtocol.SocialCounts(5, 2, 3, 9))),
        )
        // Other families keep the stock checksummed layout.
        assertEquals(
            hex(ClusterProtocol.socialCounts(ClusterProtocol.SocialCounts(5, 2, 3, 9))),
            hex(ClusterProtocol.socialCounts(ClusterModel.NTORQ_U812, ClusterProtocol.SocialCounts(5, 2, 3, 9))),
        )
    }

    @Test fun arrowTestLabelsFitTheDisplay() {
        Maneuver.entries.forEach { m ->
            val label = "${ClusterProtocol.shortName(m)} ${ClusterModel.NTORQ_SMARTXONNECT.pictogram(m)}"
            assertTrue(label, label.length <= 17)
        }
        val (control, text) = ClusterProtocol.arrowProbe(ClusterModel.NTORQ_SMARTXONNECT, 10, "RIGHT 10")
        assertEquals(10, control[9].toInt())
        assertEquals(1, control[14].toInt())
        assertEquals("RIGHT 10", String(text, 2, 8, Charsets.UTF_8))
    }

    /** Stock onVehicleDataForU458 / createSendSMSNameArray / sendBirthDate layouts. */
    @Test fun smartXonnectExtras() {
        val day = java.time.LocalDate.of(2026, 11, 6)
        assertEquals("5a f1 04 00 00 00 00 00 00 06 0b 1a 00 00 01 00 00 00 00 ff", hex(ClusterProtocol.vehicleData(4, day)))
        assertEquals("5a f1 05 00 00 00 00 00 00 06 0b 1a 00 00 01 00 00 00 00 ff", hex(ClusterProtocol.vehicleData(9, day)))
        assertEquals("5b 53 39 38 37 36 35 34 33 32 31 30 00 00 00 00 00 00 00 ff", hex(ClusterProtocol.smsSender("+91 98765 43210")))
        assertEquals(
            "5a 7e 02 0f 08 62 00 00 00 00 00 00 00 00 00 00 00 00 00 ff",
            hex(ClusterProtocol.anniversary(ClusterProtocol.Anniversary.BIRTHDAY, java.time.LocalDate.of(1998, 8, 15))),
        )
    }

    @Test fun raceStatsFromTheRidersDisplay() {
        fun bytes(h: String) = h.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        val t = DisplayTelemetry().merge(bytes("5a 16 00 00 00 00 07 17 1e 00 45 00 00 42 96 23 00 00 00 ff"))
        assertEquals(7 * 6000 + 23 * 100 + 30, t.raceBestLapCs)
        assertEquals(69, t.raceTopSpeedKmh)
        assertEquals(150, t.raceZeroTo60Tenths)
        assertEquals(35, t.raceAverageKmh)
        assertEquals(66, t.raceLapTenths)
    }

    /** Stock onClearCall / onClearSMS and the delivery + carbon bytes of sendMobileDataNotificationAndAlert. */
    @Test fun smartXonnectExperimentalFrames() {
        val day = java.time.LocalDate.of(2026, 11, 6)
        assertEquals("5a f1 05 00 00 00 00 00 00 06 0b 1a 01 00 01 00 00 00 00 ff", hex(ClusterProtocol.clearCallLed(day)))
        assertEquals("5a f1 04 00 00 02 aa 00 00 06 0b 1a 00 01 01 00 00 00 00 ff", hex(ClusterProtocol.clearSmsLed(4, day)))
        assertEquals(
            "5b 9c 28 30 09 03 0c 00 00 00 00 00 00 00 00 00 00 00 00 ff",
            hex(ClusterProtocol.socialCounts(ClusterModel.NTORQ_SMARTXONNECT, ClusterProtocol.SocialCounts(5, 2, 3, 9), delivery = 3, carbon = 12)),
        )
    }
}
