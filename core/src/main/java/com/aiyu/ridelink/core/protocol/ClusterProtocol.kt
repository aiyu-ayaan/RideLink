package com.aiyu.ridelink.core.protocol

import java.util.UUID

/**
 * Encoder for the TVS SmartXonnect cluster BLE protocol.
 * Every byte layout here is recovered from TVS Connect 8.13.2; see PROTOCOL.md for sources.
 */
object ClusterProtocol {
    val SERVICE: UUID = UUID.fromString("5456534d-5647-5341-5342-454e544f5251")
    val WRITE: UUID = UUID.fromString("00005352-0000-1000-8000-00805f9b34fb")
    val NOTIFY: UUID = UUID.fromString("00005354-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val MTU = 512
    const val FRAME_SIZE = 20
    const val STATUS_INTERVAL_MS = 2_000L
    const val CALLER_REPEAT_MS = 500L
    const val NAV_FRAME_GAP_MS = 400L

    private const val START = 0x5B.toByte()
    private const val START_NAV = 0x5A.toByte()
    private const val END = 0xFF.toByte()
    private const val TEXT_MAX = 17

    const val DID_STATUS = 0x4A
    const val DID_RIDER_NAME = 0x52
    const val DID_TEXT_LINE_1 = 0x4C
    const val DID_TEXT_LINE_2 = 0x63
    const val DID_CALLER = 0x43
    const val DID_NAV_TEXT = 0x4F
    const val DID_NAV_CONTROL = 0x4E
    const val DID_SOCIAL = 0x9C
    const val DID_SCREEN = 0x4C
    const val DID_WEATHER = 0x5D
    const val DID_LOCATION = 0x5C
    const val DID_SONG = 0x62
    const val DID_ARTIST = 0x63
    const val DID_PLAY_STATE = 0x60

    /** `5B DID <text, 17 bytes max> <zero pad> FF`, same sanitising as the stock app. */
    fun text(did: Int, value: String): ByteArray = frame(START, did, displayText(value).toByteArray(Charsets.US_ASCII))

    /** What a text frame will actually show: 17 chars, anything but `A-Z a-z 0-9 space` becomes `X`. */
    fun displayText(value: String): String =
        value.map { c -> if (c.isLetterOrDigit() && c.code < 128 || c == ' ') c else 'X' }.joinToString("").take(TEXT_MAX).trim()

    fun riderName(name: String) = text(DID_RIDER_NAME, name)

    /** Custom text line 1 (DID 0x4C), as the stock app's "custom text" setting writes it. */
    fun customLine1(value: String) = text(DID_TEXT_LINE_1, value)

    /** Custom text line 2 (DID 0x63). The same DID carries the music artist, so a playing song replaces it. */
    fun customLine2(value: String) = text(DID_TEXT_LINE_2, value)

    /**
     * Incoming call name or number. Stock app strips the +91 prefix. A number as dialers format it
     * ("(987) 654-3210", "+91 98765 43210") is sent as digits only; the display has no ( ) - +.
     */
    fun caller(nameOrNumber: String): ByteArray {
        // Dialers wrap numbers in invisible bidi marks (U+202A..U+202C); drop format characters first.
        val v = nameOrNumber.filterNot { Character.getType(it) == Character.FORMAT.toInt() }.replace("+91", "").trim()
        return text(DID_CALLER, if (v.matches(PHONE_NUMBER)) v.filter(Char::isDigit) else v)
    }

    private val PHONE_NUMBER = Regex("""[\d\s()+\-.]{3,}""")

    data class PhoneStatus(
        val batteryPercent: Int,
        /** 0..4, from SignalStrength.getLevel(). */
        val signalLevel: Int,
        /** 0 none, 3 = 3G, 4 = LTE, 5 = 5G; see [networkTypeCode]. */
        val networkType: Int,
        val hour12: Int,
        val minute: Int,
        val second: Int,
        val isPm: Boolean,
        val day: Int,
        val month: Int,
        val year: Int,
        val missedCalls: Int = 0,
        /** Celsius from weather; 0 when unknown. */
        val temperatureC: Int = 0,
        /** null = alert off. */
        val overSpeedKmh: Int? = null,
        val callState: CallState = CallState.IDLE,
    )

    enum class CallState(val code: Int) { RINGING(0), ACCEPTED(1), DISCONNECTED(2), ENDED(5), IDLE(0xFF) }

    /** Cyclic `5B 4A` status frame, sent every [STATUS_INTERVAL_MS]. */
    fun status(model: ClusterModel, s: PhoneStatus): ByteArray {
        val netBatt = (s.signalLevel.coerceIn(0, 9) shl 4) or batteryLevel(s.batteryPercent)
        val body = when (model.family) {
            // BlePacketsProvider.sendJupiterMobileData; AM = 0, PM = 1; temp offset +40; missed calls in body[8].
            ClusterFamily.JUPITER -> byteArrayOf(
                netBatt.toByte(), (s.overSpeedKmh ?: 120).toByte(), (s.temperatureC + 40).coerceIn(-128, 127).toByte(), 0,
                s.hour12.toByte(), s.minute.toByte(), s.second.toByte(), (if (s.isPm) 1 else 0).toByte(),
                // Stock sends 0 missed calls here; RideLink sends the real count so the call icon can show.
                s.missedCalls.coerceIn(0, 255).toByte(), s.networkType.toByte(), s.day.toByte(), s.month.toByte(), (s.year % 100).toByte(), 0, 0, 0,
            )
            // BlePacketsProvider.sendNtorqMobileData; AM = 1, PM = 0; 0xFF = no overspeed alert.
            ClusterFamily.NTORQ -> {
                val head = byteArrayOf(
                    netBatt.toByte(), (s.overSpeedKmh ?: 0xFF).toByte(), s.temperatureC.toByte(), 0,
                    s.hour12.toByte(), s.minute.toByte(), s.second.toByte(), (if (s.isPm) 0 else 1).toByte(),
                    s.missedCalls.coerceIn(0, 255).toByte(), s.networkType.toByte(),
                    s.day.toByte(), s.month.toByte(), (s.year % 100).toByte(), 0, 0,
                )
                // U577 base ends with find-my-vehicle flag; the others end with 0 + call state.
                if (model == ClusterModel.NTORQ_U577_BASE) head + byteArrayOf(0) else head + byteArrayOf(0, s.callState.code.toByte())
            }
        }
        return frame(START, DID_STATUS, body)
    }

    data class NavStep(
        val distanceToTurnM: Int,
        val etaSeconds: Long,
        val distanceLeftM: Int,
        val maneuver: Maneuver,
        val instruction: String,
        val stopped: Boolean = false,
    )

    /**
     * Two frames, sent text first, then control after [NAV_FRAME_GAP_MS]
     * (BleEngine.sendNavigationData writes pair.second, sleeps 400 ms, writes pair.first).
     */
    fun navigation(model: ClusterModel, step: NavStep): List<ByteArray> {
        val instruction = step.instruction.take(TEXT_MAX)
        val textFrame = frame(START, DID_NAV_TEXT, instruction.toByteArray(Charsets.UTF_8))
        val dist = if (step.distanceToTurnM >= 0xFFFF) 0xFFFF else step.distanceToTurnM.coerceAtLeast(0)
        val etaMin = (step.etaSeconds / 60).toInt().coerceIn(0, 0xFFFF)
        val left = step.distanceLeftM.coerceIn(0, 0xFFFFFF)
        val control = byteArrayOf(
            (dist shr 8).toByte(), dist.toByte(),
            (etaMin shr 8).toByte(), etaMin.toByte(),
            (left shr 16).toByte(), (left shr 8).toByte(), left.toByte(),
            model.pictogram(step.maneuver).toByte(),
            (if (step.instruction.length > TEXT_MAX) 2 else 1).toByte(),
            (if (step.stopped) 0xFF else 0).toByte(),
            0, 0, 0, 0, 0, 0, 0,
        )
        return listOf(textFrame, frame(START_NAV, DID_NAV_CONTROL, control))
    }

    /** Unread counts per app. Telegram is counted by the stock app but never sent. */
    data class SocialCounts(
        val whatsappMessages: Int = 0,
        val whatsappChats: Int = 0,
        val facebookMessages: Int = 0,
        val instagram: Int = 0,
    )

    /**
     * `5B 9C <WA> <FB> <IG> <zero pad> FF` (BleWriteSocialMediaNotificationDataSrcImpl,
     * BluetoothUtil.getSocialMediaDataToWriteToCluster, DID SampleGattAttributes.DATA_ID_MESSAGE5 = -100).
     * WA = msgs (5 bits) << 3 | chats (3 bits). FB = msgs (4 bits) << 4 | chats (4 bits); the stock app passes
     * the message count for both FB fields, so we do too.
     */
    fun socialCounts(c: SocialCounts): ByteArray {
        val wa = (c.whatsappMessages.coerceIn(0, 0x1F) shl 3) or c.whatsappChats.coerceIn(0, 0x07)
        val fbCount = c.facebookMessages.coerceIn(0, 0x0F)
        val fb = (fbCount shl 4) or fbCount
        return checksummed(frame(START, DID_SOCIAL, byteArrayOf(wa.toByte(), fb.toByte(), c.instagram.coerceIn(0, 0xFF).toByte())))
    }

    /** TrafficSequence ids for [screen]. Cricket/football need TVS's score API and are not used. */
    enum class Screen(val id: Int) { DEFAULT(0x00), WEATHER(0x78), CRICKET(0x79), FOOTBALL(0x80) }

    /** `5B 4C 00 <id> <15 zero> FF` (BluetoothUtil.createTrafficSequenceCustomFrame). */
    fun screen(screen: Screen) = frame(START, DID_SCREEN, byteArrayOf(0, screen.id.toByte()))

    data class Weather(
        val kind: WeatherKind,
        val temperatureC: Int,
        /** 0..65535; RideLink sends the US AQI from Open-Meteo. */
        val aqi: Int,
        val rainPercent: Int,
        val description: String,
    )

    private const val WEATHER_TEXT_MAX = 13

    /**
     * `5B 5D <icon> <AQI hi> <AQI lo> <rain %> <description, 13 bytes UTF-8, space padded> FF`
     * (BluetoothUtil.createClimateData). Send [screen] WEATHER first.
     */
    fun weather(model: ClusterModel, w: Weather): ByteArray {
        val aqi = w.aqi.coerceIn(0, 0xFFFF)
        val text = utf8Take(w.description, WEATHER_TEXT_MAX).copyOf(WEATHER_TEXT_MAX)
        for (i in text.indices) if (text[i] == 0.toByte()) text[i] = 0x20
        val head = byteArrayOf(model.weatherIcon(w.kind).toByte(), (aqi shr 8).toByte(), aqi.toByte(), w.rainPercent.coerceIn(0, 100).toByte())
        return frame(START, DID_WEATHER, head + text)
    }

    /** `5B 5C <place name, 17 bytes UTF-8> <zero pad> FF` (BluetoothUtil.createCurrentLocationArray). */
    fun location(placeName: String) = frame(START, DID_LOCATION, utf8Take(placeName.trim(), TEXT_MAX))

    /** UTF-8 bytes of [s], cut to [max] bytes without splitting a character. */
    private fun utf8Take(s: String, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)
            if (out.size() + bytes.size > max) break
            out.write(bytes)
            i += Character.charCount(cp)
        }
        return out.toByteArray()
    }

    /**
     * Song title (`BlePacketsProvider.returnMusicPacket(name, 98)`), checksummed like every music write
     * (`BleMusicDataSourceImpl`). The checksum takes byte 18, so 16 characters fit instead of 17.
     */
    fun song(title: String) = checksummed(text(DID_SONG, title.take(TEXT_MAX - 1)))

    /** Artist (`BleMusicDataSourceImpl`; Raider uses 0x65, Ntorq 0x63). Checksummed, 16 characters. */
    fun artist(name: String) = checksummed(text(DID_ARTIST, name.take(TEXT_MAX - 1)))

    enum class PlayState { PLAYING, PAUSED, STOPPED }

    data class Music(
        val title: String,
        val artist: String,
        val state: PlayState,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val volumePercent: Int = 0,
    )

    /**
     * Generic (`returnPlayPauseState`): `5B 60 00 <state> 00 00 00 <volume %> <pad> FF`, state pause 1, stop 4, else 0.
     * U577 (`returnPlayPauseStateU577`): `5B 60 00 <state> 01 <cur min> <cur sec> <volume> 00 00 00 00 <total min> <total sec>
     * <pad> FF`, stop 254.
     */
    fun playState(model: ClusterModel, m: Music): ByteArray {
        val vol = m.volumePercent.coerceIn(0, 100).toByte()
        val u577 = model == ClusterModel.NTORQ_U577_BASE || model == ClusterModel.NTORQ_U577_PREMIUM
        val state = when (m.state) {
            PlayState.PLAYING -> 0
            PlayState.PAUSED -> 1
            PlayState.STOPPED -> if (u577) 254 else 4
        }.toByte()
        if (!u577) return checksummed(frame(START, DID_PLAY_STATE, byteArrayOf(0, state, 0, 0, 0, vol)))
        fun minSec(ms: Long): Pair<Byte, Byte> {
            val total = (ms.coerceAtLeast(0) / 1000).coerceAtMost(255L * 60 + 59)
            return (total / 60).toInt().toByte() to (total % 60).toInt().toByte()
        }
        val (curMin, curSec) = minSec(m.positionMs)
        val (totMin, totSec) = minSec(m.durationMs)
        return checksummed(frame(START, DID_PLAY_STATE, byteArrayOf(0, state, 1, curMin, curSec, vol, 0, 0, 0, 0, totMin, totSec)))
    }

    /**
     * Speed in km/h from the display's `5A 10` speedometer frame, else null.
     * Stock `@ParsingMeta(start = 3)` is 1-based (DataParser.parseData does `start - 1`), so it is `frame[2]`.
     */
    fun speedKmh(frame: ByteArray): Int? =
        if (frame.size > 2 && frame[0] == START_NAV && frame[1] == 0x10.toByte()) frame[2].toInt() and 0xFF else null

    /** BluetoothUtil.getIceBatteryLevel: 0..5 bars. */
    fun batteryLevel(percent: Int) = when (percent) {
        in 0..9 -> 0
        in 10..19 -> 1
        in 20..39 -> 2
        in 40..59 -> 3
        in 60..79 -> 4
        in 80..100 -> 5
        else -> 0
    }

    /** BluetoothUtil.getNetworkType, input is TelephonyManager.NETWORK_TYPE_*. */
    fun networkTypeCode(telephonyNetworkType: Int) = when (telephonyNetworkType) {
        2, 3, 8, 10, 15, 16 -> 3
        13 -> 4
        20 -> 5
        else -> 0
    }

    /**
     * `BluetoothUtil.addChecksum`, used when the stock app writes with `isChecksumNeeded = true` (social counts and
     * music): byte 18 = 255 - (unsigned sum of bytes 0..17) mod 256, byte 19 stays FF. Displays drop these frames
     * without it.
     */
    fun checksummed(frame: ByteArray): ByteArray {
        val out = frame.copyOf()
        val sum = (0 until FRAME_SIZE - 2).sumOf { out[it].toInt() and 0xFF }
        out[FRAME_SIZE - 2] = (255 - sum % 256).toByte()
        out[FRAME_SIZE - 1] = END
        return out
    }

    private fun frame(start: Byte, did: Int, payload: ByteArray): ByteArray {
        val out = ByteArray(FRAME_SIZE)
        out[0] = start
        out[1] = did.toByte()
        payload.copyInto(out, 2, 0, minOf(payload.size, FRAME_SIZE - 3))
        out[FRAME_SIZE - 1] = END
        return out
    }
}
