package com.aiyu.ridelink.core.protocol

/**
 * Ride data the display notifies on `5354` (JupiterClusterDataReceiver / NtorqClusterDataReceiver).
 * Stock `@ParsingMeta(start = N)` is 1-based; the indexes below are 0-based into the frame.
 * Odometer and trip come in 0.1 km steps (confirmed on the rider's Ntorq XT: raw 1718 = 171.8 km).
 * Other values are raw: the stock parser applies no scaling. shortcut: confirm the remaining units on the scooter.
 */
data class DisplayTelemetry(
    val speedKmh: Int? = null,
    /** 0.1 km steps; see [odometerKm]. */
    val odometerTenths: Int? = null,
    val fuelLevel: Int? = null,
    val averageSpeedKmh: Int? = null,
    val topSpeedKmh: Int? = null,
    /** 0.1 km steps; see [tripKm]. */
    val tripTenths: Int? = null,
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
    /** 5A 11 [7]. */
    val softwareVersion: Int? = null,
    /** Malfunction indicator code, 5A 11 [8]; non-zero means the engine light is on. */
    val milCode: Int? = null,
    /** 5A 18 [2]. */
    val engineLoad: Int? = null,
    /** 5A 18 [7], raw. */
    val intakeTemp: Int? = null,
    /** 5A 18 [8], raw. */
    val engineTemp: Int? = null,
    /** 5A 18 [11], raw (likely tenths of a volt; confirm). */
    val batteryVoltage: Int? = null,
    /** 5A 18 [12..13]. */
    val engineRunTime: Int? = null,
) {
    val odometerKm: Double? get() = odometerTenths?.let { it / 10.0 }
    val tripKm: Double? get() = tripTenths?.let { it / 10.0 }

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
                odometerTenths = be(3, 3) ?: odometerTenths,
                fuelLevel = u8(6) ?: fuelLevel,
                averageSpeedKmh = u8(7) ?: averageSpeedKmh,
                topSpeedKmh = u8(9) ?: topSpeedKmh,
                switchStatus = u8(12) ?: switchStatus,
                tripTenths = be(14, 3) ?: tripTenths,
                rpm = be(17, 2) ?: rpm,
            )
            // Speedometer 2.
            0x11 -> copy(
                serviceReminder = u8(4) ?: serviceReminder,
                softwareVersion = u8(7) ?: softwareVersion,
                milCode = u8(8) ?: milCode,
                vehicleModel = u8(9) ?: vehicleModel,
                turnIndicators = u8(12) ?: turnIndicators,
            )
            // Speedometer 3.
            0x19 -> copy(
                averageEconomy = u8(8) ?: averageEconomy,
                instantEconomy = u8(9) ?: instantEconomy,
                rangeKm = be(11, 2) ?: rangeKm,
            )
            // Speedometer 4.
            0x18 -> copy(
                engineLoad = u8(2) ?: engineLoad,
                intakeTemp = u8(7) ?: intakeTemp,
                engineTemp = u8(8) ?: engineTemp,
                batteryVoltage = u8(11) ?: batteryVoltage,
                engineRunTime = be(12, 2) ?: engineRunTime,
            )
            else -> this
        }
    }
}

/** True once any vehicle-health field has arrived (5A 10 RPM, 5A 11, 5A 18, 5A 19 instant economy). */
val DisplayTelemetry.hasHealth: Boolean
    get() = listOf(rpm, batteryVoltage, engineTemp, instantEconomy, serviceReminder, milCode, turnIndicators).any { it != null }

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
