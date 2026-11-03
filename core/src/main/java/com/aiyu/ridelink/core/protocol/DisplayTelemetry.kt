package com.aiyu.ridelink.core.protocol

/**
 * Ride data the display notifies on `5354` (JupiterClusterDataReceiver / NtorqClusterDataReceiver).
 * Stock `@ParsingMeta(start = N)` is 1-based; the indexes below are 0-based into the frame.
 * Values are raw: the stock parser applies no scaling. shortcut: confirm units on the scooter.
 */
data class DisplayTelemetry(
    val speedKmh: Int? = null,
    val odometerKm: Int? = null,
    val fuelLevel: Int? = null,
    val averageSpeedKmh: Int? = null,
    val topSpeedKmh: Int? = null,
    val tripKm: Int? = null,
    val rpm: Int? = null,
    /** 5A 10 [12]: scooter buttons and ignition, see [CallButton.from]. */
    val switchStatus: Int? = null,
    val serviceReminder: Int? = null,
    val turnIndicators: Int? = null,
    /** 5A 11 [9]; may identify the model (not used for Auto detection until confirmed). */
    val vehicleModel: Int? = null,
    /** km/l, 5A 19 [8]. */
    val averageEconomy: Int? = null,
    val instantEconomy: Int? = null,
    /** Distance to empty, 5A 19 [11..12]. */
    val rangeKm: Int? = null,
) {
    /** Folds one notified frame in; unknown frames return this unchanged. */
    fun merge(frame: ByteArray): DisplayTelemetry {
        if (frame.size < 3 || frame[0] != 0x5A.toByte()) return this
        fun u8(i: Int) = frame.getOrNull(i)?.toInt()?.and(0xFF)
        fun be(from: Int, len: Int): Int? {
            if (frame.size < from + len) return null
            var v = 0
            for (i in from until from + len) v = (v shl 8) or (frame[i].toInt() and 0xFF)
            return v
        }
        return when (frame[1].toInt() and 0xFF) {
            // Speedometer 1.
            0x10 -> copy(
                speedKmh = u8(2) ?: speedKmh,
                odometerKm = be(3, 3) ?: odometerKm,
                fuelLevel = u8(6) ?: fuelLevel,
                averageSpeedKmh = u8(7) ?: averageSpeedKmh,
                topSpeedKmh = u8(9) ?: topSpeedKmh,
                switchStatus = u8(12) ?: switchStatus,
                tripKm = be(14, 3) ?: tripKm,
                rpm = be(17, 2) ?: rpm,
            )
            // Speedometer 2.
            0x11 -> copy(
                serviceReminder = u8(4) ?: serviceReminder,
                vehicleModel = u8(9) ?: vehicleModel,
                turnIndicators = u8(12) ?: turnIndicators,
            )
            // Speedometer 3.
            0x19 -> copy(
                averageEconomy = u8(8) ?: averageEconomy,
                instantEconomy = u8(9) ?: instantEconomy,
                rangeKm = be(11, 2) ?: rangeKm,
            )
            else -> this
        }
    }
}

/** Scooter button presses carried in 5A 10 [12] (TelephonyUtils.handleCallAcceptRejectAction). */
enum class CallButton(val code: Int) {
    ACCEPT(1), REJECT(2), REJECT_WITH_SMS(3), VOICE_ASSISTANT(4);

    companion object {
        /** 64 (ignition change) and 128 (vehicle off) are flags in the same byte; the action is in the low bits. */
        fun from(switchStatus: Int?): CallButton? {
            val action = (switchStatus ?: return null) and 0x3F
            return entries.firstOrNull { it.code == action }
        }
    }
}
