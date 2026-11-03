package com.aiyu.ridelink.core.protocol

/**
 * One-line, human-readable meaning of a frame for the Console (both directions). Mirrors
 * tools/fake_cluster.py's decoder plus the display's 5A 10 / 11 / 18 / 19 frames. Best effort: unknown frames
 * return null and are shown as hex only.
 */
object FrameDecoder {
    private val textDids = mapOf(
        0x52 to "rider", 0x4C to "line1", 0x63 to "line2/artist", 0x62 to "song", 0x43 to "caller", 0x4F to "nav",
    )

    fun decode(f: ByteArray): String? {
        if (f.size < 3) return null
        val start = f[0].toInt() and 0xFF
        val did = f[1].toInt() and 0xFF
        fun u(i: Int) = f.getOrNull(i)?.toInt()?.and(0xFF)
        fun be(from: Int, len: Int): Int? {
            if (f.size < from + len) return null
            var v = 0
            for (i in from until from + len) v = (v shl 8) or (f[i].toInt() and 0xFF)
            return v
        }
        fun text(from: Int) = f.copyOfRange(from, f.size - 1).takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8).trim()
        return when {
            start == 0x5B && did == 0x4A && f.size >= 18 -> {
                val nb = u(2)!!
                "STATUS signal=${nb shr 4} batt=${nb and 0xF} overspeed=${u(3)} temp=${u(4)} " +
                    "time=${u(6)}:${"%02d".format(u(7))}:${"%02d".format(u(8))} ampm=${u(9)} missed=${u(10)} net=${u(11)} " +
                    "date=${u(12)}/${u(13)}/${u(14)} tail=${(15 until 19).joinToString(" ") { "%02x".format(u(it)) }}"
            }
            start == 0x5B && did == 0x4C && u(2) == 0 -> "SCREEN id=0x%02x".format(u(3))
            start == 0x5B && did == 0x9C -> {
                val wa = u(2)!!; val fb = u(3)!!
                "SOCIAL whatsapp=${wa shr 3}/${wa and 7} facebook=${fb shr 4} instagram=${u(4)}"
            }
            start == 0x5B && did == 0x5D -> "WEATHER icon=${u(2)} aqi=${be(3, 2)} rain=${u(5)}% '${text(6)}'"
            start == 0x5B && did == 0x5C -> "LOCATION '${text(2)}'"
            start == 0x5B && did == 0x60 -> "PLAY state=${u(3)} vol=${u(7)}"
            start == 0x5B && did in textDids -> "TEXT ${textDids[did]} '${text(2)}'"
            start == 0x5A && did == 0x4E -> "NAV dist=${be(2, 2)}m eta=${be(4, 2)}min left=${be(6, 3)}m arrow=${u(9)} ${if (u(11) == 0xFF) "stopped" else "running"}"
            start == 0x5A && did == 0x10 ->
                "SPEEDO1 speed=${u(2)} odo=${be(3, 3)} fuel=${u(6)} avg=${u(7)} tripStatus=${u(8)} top=${u(9)} " +
                    "throttle=${u(10)} backlight=${u(11)} switch=${u(12)} zeroTo60=${u(13)} trip=${be(14, 3)} rpm=${be(17, 2)}"
            start == 0x5A && did == 0x11 ->
                "SPEEDO2 fuelFail=${u(2)} state=${u(3)} service=${u(4)} state1=${u(6)} sw=${u(7)} mil=${u(8)} model=${u(9)} " +
                    "diag=${u(10)} isg=${u(11)} indicators=${u(12)} connector=${u(13)}"
            start == 0x5A && did == 0x19 ->
                "SPEEDO3 issDur=${be(2, 3)} ota=${u(5)} avgEco=${u(8)} instEco=${u(9)} range=${be(11, 2)} issCount=${be(15, 3)}"
            start == 0x5A && did == 0x18 ->
                "SPEEDO4 load=${u(2)} map=${u(5)} intake=${u(7)} engine=${u(8)} battery=${u(11)} runTime=${be(12, 2)} " +
                    "distance=${be(14, 2)} injection=${be(16, 2)}"
            else -> null
        }
    }
}
