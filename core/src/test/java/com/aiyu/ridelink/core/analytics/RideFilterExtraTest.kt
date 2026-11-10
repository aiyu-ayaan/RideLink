package com.aiyu.ridelink.core.analytics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class RideFilterExtraTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val zone = ZoneOffset.UTC

    private fun since(d: LocalDate, z: ZoneId = zone) = d.atStartOfDay(z).toInstant().toEpochMilli().toString()

    @Test fun sortSql() {
        assertEquals("start DESC, id DESC", RideSort.NEWEST.sql)
        assertEquals("start ASC, id ASC", RideSort.OLDEST.sql)
        assertEquals("distance_m DESC, id DESC", RideSort.LONGEST.sql)
        assertEquals("max_speed DESC, id DESC", RideSort.FASTEST.sql)
        assertEquals("moving_s DESC, id DESC", RideSort.LONGEST_TIME.sql)
        RideSort.values().forEach { assert(it.sql.endsWith("id ${if (it == RideSort.OLDEST) "ASC" else "DESC"}")) }
    }

    @Test fun monthAndYearPeriods() {
        val (w1, a1) = RideFilter(period = Period.MONTH).where(today, zone)
        assertEquals("start >= ?", w1)
        assertArrayEquals(arrayOf(since(LocalDate.of(2026, 10, 12))), a1)
        val (_, a2) = RideFilter(period = Period.YEAR).where(today, zone)
        assertArrayEquals(arrayOf(since(today.minusDays(364))), a2)
    }

    @Test fun periodUsesZoneMidnight() {
        val z = ZoneId.of("Asia/Kolkata")
        val (_, a) = RideFilter(period = Period.WEEK).where(today, z)
        assertArrayEquals(arrayOf(since(today.minusDays(6), z)), a)
    }

    @Test fun minKmOnly() {
        val f = RideFilter(minKm = 1.0)
        val (w, a) = f.where(today, zone)
        assertEquals("distance_m >= ?", w)
        assertArrayEquals(arrayOf("1000"), a)
        assertEquals(1, f.advancedCount)
    }

    @Test fun maxKmOnlyAndZero() {
        val (w, a) = RideFilter(maxKm = 0.0).where(today, zone)
        assertEquals("distance_m <= ?", w)
        assertArrayEquals(arrayOf("0"), a)
    }

    @Test fun queryEscapingAndBlank() {
        val (w0, a0) = RideFilter(query = "   ").where(today, zone)
        assertNull(w0); assertEquals(0, a0.size)
        val (_, a) = RideFilter(query = "  a\\b%c_d ").where(today, zone)
        assertArrayEquals(Array(3) { "%a\\\\b\\%c\\_d%" }, a)
    }

    @Test fun samplesOffAndSpeedOnly() {
        val (w, a) = RideFilter(samples = false, minTopSpeedKmh = 80).where(today, zone)
        assertEquals("max_speed >= ? AND demo = 0", w)
        assertArrayEquals(arrayOf("80"), a)
    }

    @Test fun advancedCountSortAndRangeCountOnce() {
        assertEquals(1, RideFilter(sort = RideSort.FASTEST).advancedCount)
        assertEquals(1, RideFilter(minKm = 1.0, maxKm = 5.0).advancedCount)
        assertEquals(0, RideFilter(starred = true, navigated = true, period = Period.WEEK).advancedCount)
    }

    @Test fun argCountMatchesPlaceholders() {
        val f = RideFilter("b", Period.MONTH, true, true, "x", 1.0, 2.0, 50, false, RideSort.LONGEST)
        val (w, a) = f.where(today, zone)
        assertEquals(w!!.count { it == '?' }, a.size)
    }
}
