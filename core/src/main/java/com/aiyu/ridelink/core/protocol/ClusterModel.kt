package com.aiyu.ridelink.core.protocol

/** Which stock code path builds frames for a model (see PROTOCOL.md, "Bike families"). */
enum class ClusterFamily { NTORQ, JUPITER }

/** Turn types, named after the Mappls TurnTypeConstants the stock tables are keyed on. */
enum class Maneuver(val mappls: Int) {
    LEFT(0), SHARP_LEFT(1), SLIGHT_LEFT(2),
    RIGHT(3), SHARP_RIGHT(4), SLIGHT_RIGHT(5),
    U_TURN(6), STRAIGHT(7), DESTINATION(8),
    FORK_LEFT(15), FORK_RIGHT(16), ROUNDABOUT(68),
}

/** Supported scooters. [vehicleId] is the TVS account VehicleId. */
enum class ClusterModel(val vehicleId: Int, val family: ClusterFamily) {
    NTORQ_U716(34, ClusterFamily.NTORQ),
    NTORQ_U577_BASE(37, ClusterFamily.NTORQ),
    NTORQ_U577_PREMIUM(38, ClusterFamily.NTORQ),
    NTORQ_U812(39, ClusterFamily.JUPITER),
    NTORQ_N251(43, ClusterFamily.JUPITER),
    NTORQ_U458(44, ClusterFamily.JUPITER),
    NTORQ_U377(45, ClusterFamily.JUPITER);

    /** Mappls turn id to cluster arrow id, per BleNavigationDataSourceImp. */
    fun pictogram(m: Maneuver): Int = when (this) {
        NTORQ_U577_BASE -> BASE_U577[m.mappls] ?: 5
        // Stock app parses the premium table value as hex: "10" becomes 0x10.
        NTORQ_U577_PREMIUM -> (PREMIUM_U577[m.mappls] ?: 64).toString().toInt(16)
        // U716 is not in any stock nav table; it falls back to the U812 one.
        else -> NEW_NTORQ[m.mappls] ?: 0
    }

    companion object {
        /** Used in Auto mode until the display proves it is an older Ntorq. */
        val DEFAULT = NTORQ_U812

        /**
         * Infers the model from a frame the display notifies. Only the older Ntorq displays
         * (U716/U577) send music-control `5A 6B` or Wi-Fi `5A 29` frames (NtorqFrameIdentifier),
         * so either one means the older format. Returns null when the frame proves nothing.
         * shortcut: U716 and U577 base cannot be told apart from frames yet; refine from real captures.
         */
        fun detect(frame: ByteArray): ClusterModel? =
            if (frame.size >= 2 && frame[0] == 0x5A.toByte() && (frame[1] == 0x6B.toByte() || frame[1] == 0x29.toByte())) NTORQ_U577_PREMIUM else null

        private val NEW_NTORQ = mapOf(0 to 0, 1 to 1, 2 to 2, 3 to 10, 4 to 11, 5 to 12, 6 to 19, 7 to 57, 8 to 22, 15 to 7, 16 to 17, 68 to 48)
        private val PREMIUM_U577 = mapOf(0 to 0, 1 to 1, 2 to 2, 3 to 10, 4 to 11, 5 to 12, 6 to 9, 7 to 57, 8 to 22, 15 to 7, 16 to 17, 68 to 48)
        private val BASE_U577 = mapOf(0 to 6, 1 to 12, 2 to 10, 3 to 7, 4 to 13, 5 to 11, 6 to 15, 7 to 5, 8 to 2, 15 to 19, 16 to 20, 68 to 45)
    }
}
