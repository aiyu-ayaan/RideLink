package com.aiyu.ridelink.core.analytics

import java.time.LocalDate
import java.time.ZoneId

/** Ride history order; [sql] is the ORDER BY of [RideStore.page] (id breaks ties so pages never overlap). */
enum class RideSort(val sql: String) {
    NEWEST("start DESC, id DESC"),
    OLDEST("start ASC, id ASC"),
    LONGEST("distance_m DESC, id DESC"),
    FASTEST("max_speed DESC, id DESC"),
    LONGEST_TIME("moving_s DESC, id DESC"),
}

/**
 * What the ride history shows: quick chips (period, starred, with a Maps route) plus the advanced sheet
 * (distance, top speed, sample rides, sort). [where] turns it into a SQLite WHERE clause for [RideStore.page].
 */
data class RideFilter(
    /** Scooter address; rides with no scooter (null) show for every bike, as on the Analytics tab. */
    val bike: String? = null,
    val period: Period = Period.ALL,
    val starred: Boolean = false,
    val navigated: Boolean = false,
    /** Name or start / end place contains this, ignoring case. */
    val query: String = "",
    val minKm: Double? = null,
    val maxKm: Double? = null,
    val minTopSpeedKmh: Int? = null,
    val samples: Boolean = true,
    val sort: RideSort = RideSort.NEWEST,
) {
    /** How many advanced-sheet settings differ from the default, for the "Filters" chip badge. */
    val advancedCount: Int
        get() = listOf(minKm != null || maxKm != null, minTopSpeedKmh != null, !samples, sort != RideSort.NEWEST).count { it }

    fun where(today: LocalDate, zone: ZoneId): Pair<String?, Array<String>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        bike?.let { clauses += "(bike IS NULL OR bike = ?)"; args += it }
        period.days?.let { days ->
            clauses += "start >= ?"
            args += today.minusDays(days - 1L).atStartOfDay(zone).toInstant().toEpochMilli().toString()
        }
        if (starred) clauses += "starred = 1"
        if (navigated) clauses += "nav_s > 0"
        query.trim().takeIf { it.isNotEmpty() }?.let { q ->
            clauses += "(name LIKE ? ESCAPE '\\' OR from_place LIKE ? ESCAPE '\\' OR to_place LIKE ? ESCAPE '\\')"
            val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            repeat(3) { args += like }
        }
        minKm?.let { clauses += "distance_m >= ?"; args += (it * 1000).toInt().toString() }
        maxKm?.let { clauses += "distance_m <= ?"; args += (it * 1000).toInt().toString() }
        minTopSpeedKmh?.let { clauses += "max_speed >= ?"; args += it.toString() }
        if (!samples) clauses += "demo = 0"
        return clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ") to args.toTypedArray()
    }
}
