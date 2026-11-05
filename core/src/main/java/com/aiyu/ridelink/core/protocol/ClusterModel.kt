package com.aiyu.ridelink.core.protocol

/**
 * Which stock code path builds frames for a model (see PROTOCOL.md, "Bike families").
 * SMARTXONNECT is the original Ntorq SmartXonnect LCD (stock legacy app, vehicle type 9 "nTorq251", themes such as
 * Race XP "Racing 2.0" and Marvel): its own nav control and status layouts in TVSMMapActivity and
 * N109U347N251DataSenderToCluster.
 */
enum class ClusterFamily { NTORQ, JUPITER, SMARTXONNECT }

/** Turn types, named after the Mappls TurnTypeConstants the stock tables are keyed on. */
enum class Maneuver(val mappls: Int) {
    LEFT(0), SHARP_LEFT(1), SLIGHT_LEFT(2),
    RIGHT(3), SHARP_RIGHT(4), SLIGHT_RIGHT(5),
    U_TURN(6), STRAIGHT(7), DESTINATION(8),
    FORK_LEFT(15), FORK_RIGHT(16), ROUNDABOUT(68),
}

/** Weather groups RideLink can tell apart; the display picks the icon from them. */
enum class WeatherKind { CLEAR, CLEAR_NIGHT, CLOUDY, FOG, RAIN, SNOW, THUNDERSTORM, HAIL;

    companion object {
        /** WMO weather interpretation code, as returned by Open-Meteo `weather_code`. */
        fun fromWmo(code: Int, isDay: Boolean): WeatherKind = when (code) {
            0, 1 -> if (isDay) CLEAR else CLEAR_NIGHT
            2, 3 -> CLOUDY
            45, 48 -> FOG
            in 51..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            96, 99 -> HAIL
            95 -> THUNDERSTORM
            else -> CLOUDY
        }
    }
}

/** Supported scooters. [vehicleId] is the TVS account VehicleId. */
enum class ClusterModel(val vehicleId: Int, val family: ClusterFamily) {
    NTORQ_SMARTXONNECT(9, ClusterFamily.SMARTXONNECT),
    NTORQ_U716(34, ClusterFamily.NTORQ),
    NTORQ_U577_BASE(37, ClusterFamily.NTORQ),
    NTORQ_U577_PREMIUM(38, ClusterFamily.NTORQ),
    NTORQ_U812(39, ClusterFamily.JUPITER),
    NTORQ_N251(43, ClusterFamily.JUPITER),
    NTORQ_U458(44, ClusterFamily.JUPITER),
    NTORQ_U377(45, ClusterFamily.JUPITER);

    /** Mappls turn id to cluster arrow id, per BleNavigationDataSourceImp. */
    fun pictogram(m: Maneuver): Int = when (this) {
        // TVS's own res/raw/en_tvs_short_instuctions.json "$code", sent unmapped (TVSMMapActivity.getPictogramCode).
        NTORQ_SMARTXONNECT -> SMARTXONNECT_CODES.getValue(m)
        NTORQ_U577_BASE -> BASE_U577[m.mappls] ?: 5
        // Stock app parses the premium table value as hex: "10" becomes 0x10.
        NTORQ_U577_PREMIUM -> (PREMIUM_U577[m.mappls] ?: 64).toString().toInt(16)
        // U716 is not in any stock nav table; it falls back to the U812 one.
        else -> NEW_NTORQ[m.mappls] ?: 0
    }

    /**
     * Weather icon id. 2025+ family: BluetoothUtil.getWeatherIcon (clouds 1, clear 2, thunderstorm 3, snow 4, rain 5,
     * fog 9, night 11, hailstorm 16). Older Ntorq: getWeatherIconForPremium (clouds 1, clear 2, thunderstorm 3,
     * snow 4, rain 5, fog 9, else 0).
     */
    fun weatherIcon(kind: WeatherKind): Int = when (family) {
        ClusterFamily.JUPITER -> when (kind) {
            WeatherKind.CLOUDY -> 1
            WeatherKind.CLEAR -> 2
            WeatherKind.THUNDERSTORM -> 3
            WeatherKind.SNOW -> 4
            WeatherKind.RAIN -> 5
            WeatherKind.FOG -> 9
            WeatherKind.CLEAR_NIGHT -> 11
            WeatherKind.HAIL -> 16
        }
        ClusterFamily.NTORQ, ClusterFamily.SMARTXONNECT -> when (kind) {
            WeatherKind.CLOUDY -> 1
            WeatherKind.CLEAR, WeatherKind.CLEAR_NIGHT -> 2
            WeatherKind.THUNDERSTORM, WeatherKind.HAIL -> 3
            WeatherKind.SNOW -> 4
            WeatherKind.RAIN -> 5
            WeatherKind.FOG -> 9
        }
    }

    /** TFT displays (2025+ family and the U577 premium) have the weather, location and music screens. */
    val hasTft: Boolean get() = family == ClusterFamily.JUPITER || this == NTORQ_U577_PREMIUM

    /** The SmartXonnect LCD takes the place name (stock sendCurrentLocation) but has no weather screen. */
    val hasLocation: Boolean get() = hasTft || family == ClusterFamily.SMARTXONNECT

    /** Stock sends no music to the SmartXonnect LCD, where 5B 63 is the second text line, not the artist. */
    val hasMusic: Boolean get() = family != ClusterFamily.SMARTXONNECT

    companion object {
        /** Used in Auto mode until the display proves it is an older Ntorq. */
        val DEFAULT = NTORQ_U812

        /**
         * Infers the model from a frame the display notifies; null when the frame proves nothing.
         * - `5A 16` (race stats: best lap, top speed, 0-60) and `5B 42` (the display's own Bluetooth MAC) are parsed
         *   only by the legacy N251ClusterDataReceiver; the 2025+ and U577 frame identifiers know neither. The 2026
         *   Race XP sends both, every second.
         * - Only the older Ntorq displays (U716/U577) send music-control `5A 6B` or Wi-Fi `5A 29` (NtorqFrameIdentifier).
         * shortcut: U716 and U577 base cannot be told apart from frames yet; refine from real captures.
         */
        fun detect(frame: ByteArray): ClusterModel? {
            if (frame.size < 2) return null
            val start = frame[0].toInt() and 0xFF
            val did = frame[1].toInt() and 0xFF
            return when {
                start == 0x5A && did == 0x16 || start == 0x5B && did == 0x42 -> NTORQ_SMARTXONNECT
                start == 0x5A && (did == 0x6B || did == 0x29) -> NTORQ_U577_PREMIUM
                else -> null
            }
        }

        private val SMARTXONNECT_CODES = mapOf(
            Maneuver.LEFT to 0, Maneuver.SHARP_LEFT to 1, Maneuver.FORK_LEFT to 2, Maneuver.RIGHT to 3,
            Maneuver.SHARP_RIGHT to 4, Maneuver.FORK_RIGHT to 5, Maneuver.STRAIGHT to 7, Maneuver.DESTINATION to 8,
            Maneuver.SLIGHT_LEFT to 15, Maneuver.SLIGHT_RIGHT to 16, Maneuver.U_TURN to 41, Maneuver.ROUNDABOUT to 72,
        )
        private val NEW_NTORQ = mapOf(0 to 0, 1 to 1, 2 to 2, 3 to 10, 4 to 11, 5 to 12, 6 to 19, 7 to 57, 8 to 22, 15 to 7, 16 to 17, 68 to 48)
        private val PREMIUM_U577 = mapOf(0 to 0, 1 to 1, 2 to 2, 3 to 10, 4 to 11, 5 to 12, 6 to 9, 7 to 57, 8 to 22, 15 to 7, 16 to 17, 68 to 48)
        private val BASE_U577 = mapOf(0 to 6, 1 to 12, 2 to 10, 3 to 7, 4 to 13, 5 to 11, 6 to 15, 7 to 5, 8 to 2, 15 to 19, 16 to 20, 68 to 45)
    }
}
