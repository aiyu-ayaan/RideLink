package com.aiyu.ridelink.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class RideMathTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val t0 = LocalDate.of(2026, 11, 3).atTime(8, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** 10 minutes at a steady [kmh], one sample every 5 s, odometer advancing to match. */
    private fun steady(kmh: Int, start: Long = t0, odo: Int = 17_180): List<RideSample> =
        (0..120).map { i ->
            RideSample(start + i * 5_000L, speedKmh = kmh, odometerTenths = odo + (kmh * i * 5 / 360), fuel = 6 - i / 100, instantEconomy = 45)
        }

    @Test fun steadyRideSummary() {
        val r = RideMath.summarize(steady(36), zone)!!
        // 36 km/h for 600 s = 6.0 km; the odometer agrees within 0.1 km.
        assertEquals(6.0, r.distanceKm, 0.15)
        assertEquals(600, r.durationS)
        assertEquals(600, r.movingS)
        assertEquals(36, r.maxSpeedKmh)
        assertEquals(36.0, r.avgMovingSpeedKmh, 2.0)
        assertEquals(45.0, r.avgEconomy!!, 0.01)
        assertEquals(1, r.fuelUsed)
        // All moving time in the 20-39 band, all at 08:00.
        assertEquals(listOf(0, 600, 0, 0, 0), r.speedBandS)
        assertEquals(600, r.hourS[8])
    }

    @Test fun standingStillAndGapsAreNotCounted() {
        val stopped = (0..12).map { RideSample(t0 + it * 5_000L, speedKmh = 0) }
        val afterGap = (0..12).map { RideSample(t0 + 600_000 + it * 5_000L, speedKmh = 40) }
        val r = RideMath.summarize(stopped + afterGap, zone)!!
        assertEquals(60, r.movingS)
        assertEquals(660, r.durationS)
    }

    @Test fun badOdometerFallsBackToSpeed() {
        val backwards = steady(36).mapIndexed { i, s -> s.copy(odometerTenths = 20_000 - i) }
        assertEquals(6.0, RideMath.summarize(backwards, zone)!!.distanceKm, 0.1)
        val stalled = steady(36).map { it.copy(odometerTenths = 17_180) }
        assertEquals(6.0, RideMath.summarize(stalled, zone)!!.distanceKm, 0.1)
        val missing = steady(36).map { it.copy(odometerTenths = null) }
        assertEquals(6.0, RideMath.summarize(missing, zone)!!.distanceKm, 0.1)
    }

    @Test fun emptyAndTinyRides() {
        assertNull(RideMath.summarize(emptyList(), zone))
        val tiny = RideMath.summarize(listOf(RideSample(t0, 0), RideSample(t0 + 5_000, 0)), zone)!!
        assertEquals(false, RideMath.worthKeeping(tiny))
        assertEquals(true, RideMath.worthKeeping(RideMath.summarize(steady(30), zone)!!))
    }

    private fun record(id: Long, daysAgo: Long, kmh: Int) =
        RideRecord(id, false, RideMath.summarize(steady(kmh, t0 - daysAgo * 86_400_000L), zone)!!)

    @Test fun weekStatsBucketsAndChange() {
        val today = LocalDate.of(2026, 11, 3)
        val rides = listOf(record(1, 0, 36), record(2, 1, 60), record(3, 3, 36), record(4, 9, 36))
        val s = RideMath.stats(rides, Period.WEEK, today, zone)
        assertEquals(3, s.rides)
        assertEquals(7, s.buckets.size)
        assertEquals(today, s.buckets.last().start)
        assertEquals(22.0, s.totalKm, 0.5)
        // Ride 4 (9 days ago) is in the previous week: 6 km -> change about +267%.
        assertEquals(6.0, s.previousKm!!, 0.2)
        assertTrue(s.change!! > 2.5)
        assertEquals(60, s.topSpeedKmh)
        assertEquals(2L, s.fastestRide!!.id)
        assertEquals(3, s.activeDays)
        assertEquals(2, s.longestStreakDays)
        assertEquals(7, s.weekdayKm.size)
        assertEquals(24, s.hourMinutes.size)
        assertEquals(30.0, s.hourMinutes[8], 0.1)
    }

    @Test fun yearAndAllAreMonthly() {
        val today = LocalDate.of(2026, 11, 3)
        val rides = listOf(record(1, 0, 36), record(2, 70, 36))
        val year = RideMath.stats(rides, Period.YEAR, today, zone)
        assertEquals(12, year.buckets.size)
        assertTrue(year.buckets.all { it.monthly })
        val all = RideMath.stats(rides, Period.ALL, today, zone)
        assertNull(all.previousKm)
        assertEquals(4, all.buckets.size) // first ride 25 Aug: Aug, Sep, Oct, Nov
        assertEquals(12.0, all.totalKm, 0.3)
    }

    @Test fun streaks() {
        val d = LocalDate.of(2026, 1, 1)
        assertEquals(0, RideMath.longestStreak(emptyList()))
        assertEquals(3, RideMath.longestStreak(listOf(d, d.plusDays(1), d.plusDays(2), d.plusDays(5))))
    }

    @Test fun downsampleKeepsShape() {
        val pts = (0 until 1_000).map { it.toLong() to it.toDouble() }
        val out = RideMath.downsample(pts, 100)
        assertEquals(100, out.size)
        assertTrue(out.first().second < 10)
        assertTrue(out.last().second > 990)
        assertEquals(pts.take(5), RideMath.downsample(pts.take(5), 100))
    }

    @Test fun sampleHistoryIsPlausible() {
        val today = LocalDate.of(2026, 11, 3)
        val rides = SampleRides.generate(today, zone).mapIndexedNotNull { i, s ->
            RideMath.summarize(s, zone)?.let { RideRecord(i.toLong(), true, it) }
        }
        val month = RideMath.stats(rides, Period.MONTH, today, zone)
        val info = "rides=${rides.size} km=${month.totalKm} top=${month.topSpeedKmh} avg=${month.avgSpeedKmh}"
        assertTrue(info, rides.size > 40)
        assertTrue(info, month.totalKm > 100)
        assertTrue(info, month.topSpeedKmh in 60..90)
    }
}
