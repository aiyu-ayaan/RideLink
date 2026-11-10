package com.aiyu.ridelink.core.analytics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RideFilterTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val zone = ZoneOffset.UTC

    @Test fun defaultFilterHasNoWhere() {
        val (where, args) = RideFilter().where(today, zone)
        assertNull(where)
        assertEquals(0, args.size)
        assertEquals(0, RideFilter().advancedCount)
    }

    @Test fun everyClauseWithItsArgs() {
        val f = RideFilter(
            bike = "AA:BB", period = Period.WEEK, starred = true, navigated = true, query = "50%_off",
            minKm = 2.5, maxKm = 10.0, minTopSpeedKmh = 60, samples = false,
        )
        val (where, args) = f.where(today, zone)
        assertEquals(
            "(bike IS NULL OR bike = ?) AND start >= ? AND starred = 1 AND nav_s > 0 AND " +
                "(name LIKE ? ESCAPE '\\' OR from_place LIKE ? ESCAPE '\\' OR to_place LIKE ? ESCAPE '\\') AND " +
                "distance_m >= ? AND distance_m <= ? AND max_speed >= ? AND demo = 0",
            where,
        )
        // Week = today and the 6 days before, from local midnight.
        val since = LocalDate.of(2026, 11, 4).atStartOfDay(zone).toInstant().toEpochMilli().toString()
        val like = "%50\\%\\_off%"
        assertArrayEquals(arrayOf("AA:BB", since, like, like, like, "2500", "10000", "60"), args)
        assertEquals(3, f.advancedCount)
    }
}
