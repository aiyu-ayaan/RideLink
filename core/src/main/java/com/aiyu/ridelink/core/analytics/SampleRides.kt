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
                rides += if (weekend) withRoute(samples) else samples
            }
        }
        return rides
    }

    /** Weekend rides follow a Google Maps route: distance-left counts down in Maps' 100 m steps. */
    private fun withRoute(samples: List<RideSample>): List<RideSample> {
        var covered = 0.0
        val steps = samples.map { s -> covered += (s.speedKmh ?: 0) / 3.6 * RideMath.SAMPLE_MS / 1000; covered }
        val total = covered
        return samples.mapIndexed { i, s -> s.copy(navLeftM = ((total - steps[i]) / 100).toInt() * 100) }
    }

    private val instructions = listOf(
        "LEFT" to "Turn left onto MG Road", "RIGHT" to "Turn right onto Ring Rd", "STRAIGHT" to "Continue on NH 48",
        "SLIGHT_RIGHT" to "Slight right toward Hosur Rd", "ROUNDABOUT" to "At the roundabout, take the 2nd exit",
        "LEFT" to "Turn left onto 100 Feet Rd", "SLIGHT_LEFT" to "Slight left onto Outer Ring Rd",
    )

    /** A turn about every 1.5 km of a sample route, and the arrival. */
    fun turnsFor(samples: List<RideSample>): List<RideTurn> {
        val routed = samples.filter { it.navLeftM != null }
        if (routed.isEmpty()) return emptyList()
        val turns = mutableListOf<RideTurn>()
        var nextAt = routed.first().navLeftM!!
        var k = 0
        for (s in routed) {
            val left = s.navLeftM!!
            if (left > nextAt) continue
            val (maneuver, text) = instructions[k++ % instructions.size]
            turns += RideTurn(s.t, text, maneuver, 400, left)
            nextAt = left - 1_500
        }
        routed.last().let { turns += RideTurn(it.t, "Arrive at destination", "DESTINATION", 0, 0) }
        return turns
    }
}
