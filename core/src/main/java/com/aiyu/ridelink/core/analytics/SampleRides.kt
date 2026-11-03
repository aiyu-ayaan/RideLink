package com.aiyu.ridelink.core.analytics

import java.time.LocalDate
import java.time.ZoneId
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * Made-up commute history for previewing Analytics before real rides exist. Stored with demo = true, labelled
 * "Sample" in the UI and removable in one tap. Deterministic for a given seed.
 */
object SampleRides {
    fun generate(today: LocalDate, zone: ZoneId, days: Int = 45, seed: Long = 7L): List<List<RideSample>> {
        val rnd = Random(seed)
        // Metres as a double; the display's odometer is the whole 0.1 km part of it.
        var meters = (17_180 - days * 150) * 100.0
        val rides = mutableListOf<List<RideSample>>()
        for (back in days downTo 1) {
            val day = today.minusDays(back.toLong())
            val weekend = day.dayOfWeek.value >= 6
            // Commute twice on weekdays; one longer ride on some weekends.
            val starts = if (weekend) (if (rnd.nextInt(3) == 0) listOf(10 + rnd.nextInt(5)) else emptyList()) else listOf(8, 18)
            for (hour in starts) {
                val minutes = if (weekend) 35 + rnd.nextInt(40) else 18 + rnd.nextInt(14)
                val top = if (weekend) 70 + rnd.nextInt(18) else 48 + rnd.nextInt(15)
                val t0 = day.atTime(hour, rnd.nextInt(50)).atZone(zone).toInstant().toEpochMilli()
                val n = minutes * 60_000 / RideMath.SAMPLE_MS.toInt()
                var fuel = 6
                val samples = (0 until n).map { i ->
                    val phase = i.toDouble() / n
                    val stop = rnd.nextInt(14) == 0
                    val v = if (stop) 0 else (top * (0.55 + 0.45 * sin(phase * PI)) - rnd.nextInt(10)).toInt().coerceAtLeast(0)
                    meters += v / 3.6 * RideMath.SAMPLE_MS / 1000
                    if (i == n / 2 && rnd.nextInt(4) == 0) fuel--
                    RideSample(
                        t = t0 + i * RideMath.SAMPLE_MS,
                        speedKmh = v,
                        odometerTenths = (meters / 100).toInt(),
                        fuel = fuel,
                        rangeKm = 180 - i / 60,
                        rpm = if (v == 0) 1_400 else 2_800 + v * 70,
                        instantEconomy = if (v == 0) null else 38 + rnd.nextInt(16),
                    )
                }
                rides += samples
            }
        }
        return rides
    }
}
