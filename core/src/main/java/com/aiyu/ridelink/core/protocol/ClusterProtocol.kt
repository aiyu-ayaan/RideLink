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

    /** `5B DID <text, 17 bytes max> <zero pad> FF`, same sanitising as the stock app. */
    fun text(did: Int, value: String): ByteArray {
        val cleaned = value.map { c -> if (c.isLetterOrDigit() && c.code < 128 || c == ' ') c else 'X' }
            .joinToString("").take(TEXT_MAX).trim()
        return frame(START, did, cleaned.toByteArray(Charsets.US_ASCII))
    }

    fun riderName(name: String) = text(DID_RIDER_NAME, name)

    /** Incoming call name or number. Stock app strips the +91 prefix. */
    fun caller(nameOrNumber: String) = text(DID_CALLER, nameOrNumber.replace("+91", ""))

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
            // BlePacketsProvider.sendJupiterMobileData; AM = 0, PM = 1; temp offset +40.
            ClusterFamily.JUPITER -> byteArrayOf(
                netBatt.toByte(), (s.overSpeedKmh ?: 120).toByte(), (s.temperatureC + 40).coerceIn(-128, 127).toByte(), 0,
                s.hour12.toByte(), s.minute.toByte(), s.second.toByte(), (if (s.isPm) 1 else 0).toByte(),
                0, s.networkType.toByte(), s.day.toByte(), s.month.toByte(), (s.year % 100).toByte(), 0, 0, 0,
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

    private fun frame(start: Byte, did: Int, payload: ByteArray): ByteArray {
        val out = ByteArray(FRAME_SIZE)
        out[0] = start
        out[1] = did.toByte()
        payload.copyInto(out, 2, 0, minOf(payload.size, FRAME_SIZE - 3))
        out[FRAME_SIZE - 1] = END
        return out
    }
}
