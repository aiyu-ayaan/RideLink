package com.aiyu.ridelink.core.analytics

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import kotlin.math.max

/** One reading of the display, taken every [RideMath.SAMPLE_MS] while a ride is recorded. */
data class RideSample(
    val t: Long,
    val speedKmh: Int? = null,
    val odometerTenths: Int? = null,
    val tripTenths: Int? = null,
    val fuel: Int? = null,
    val rangeKm: Int? = null,
    val rpm: Int? = null,
    val instantEconomy: Int? = null,
    val averageEconomy: Int? = null,
)

/** Everything Analytics needs about one ride, computed once when it ends. */
data class RideSummary(
    val start: Long,
    val end: Long,
    val distanceM: Int,
    val durationS: Int,
    val movingS: Int,
    val maxSpeedKmh: Int,
    val avgMovingSpeedKmh: Double,
    /** km/l as the display reports it; null when the display sent none. */
    val avgEconomy: Double?,
    /** Drop in the display's raw fuel level over the ride; null when unknown. */
    val fuelUsed: Int?,
    val odometerStartTenths: Int?,
    val odometerEndTenths: Int?,
    /** Moving seconds in [RideMath.SPEED_BANDS]. */
    val speedBandS: List<Int>,
    /** Moving seconds per local hour of day, 24 entries. */
    val hourS: List<Int>,
) {
    val distanceKm get() = distanceM / 1000.0
}

/** A stored ride: id, whether it is a demo/sample ride, and its summary. */
data class RideRecord(val id: Long, val demo: Boolean, val summary: RideSummary)

enum class Period(val days: Int?) { WEEK(7), MONTH(30), YEAR(365), ALL(null) }

/** One bar of the distance chart: a day, or a month for YEAR/ALL. */
data class Bucket(val start: LocalDate, val monthly: Boolean, val km: Double)

data class PeriodStats(
    val rides: Int,
    val totalKm: Double,
    /** Same-length period just before this one; null for ALL. */
    val previousKm: Double?,
    val durationS: Long,
    val movingS: Long,
    val avgSpeedKmh: Double,
    val topSpeedKmh: Int,
    val avgEconomy: Double?,
    val fuelUsed: Int?,
    val buckets: List<Bucket>,
    /** km per weekday, Monday first. */
    val weekdayKm: List<Double>,
    /** Riding minutes per hour of day. */
    val hourMinutes: List<Double>,
    /** Riding minutes per speed band. */
    val speedBandMinutes: List<Double>,
    val longestRide: RideRecord?,
    val fastestRide: RideRecord?,
    val bestEconomyRide: RideRecord?,
    val activeDays: Int,
    /** Longest run of consecutive days with a ride, within the period. */
    val longestStreakDays: Int,
) {
    /** Change against the previous period, e.g. 0.12 = +12%; null when there is nothing to compare. */
    val change: Double? get() = previousKm?.takeIf { it > 0 }?.let { (totalKm - it) / it }
}

object RideMath {
    const val SAMPLE_MS = 5_000L
    /** Below this the scooter counts as standing still. */
    const val MOVING_KMH = 3
    /** A gap longer than this between samples is not counted (app killed, display asleep). */
    const val MAX_GAP_MS = 30_000L
    /** Lower bounds of the speed bands, km/h: 0-19, 20-39, 40-59, 60-79, 80+. */
    val SPEED_BANDS = listOf(0, 20, 40, 60, 80)

    fun band(speed: Int) = SPEED_BANDS.indexOfLast { speed >= it }.coerceAtLeast(0)

    /**
     * Distance comes from the odometer (0.1 km steps) when it moved sensibly; otherwise it is integrated from
     * speed. Moving time, bands and hours only count gaps up to [MAX_GAP_MS].
     */
    fun summarize(samples: List<RideSample>, zone: ZoneId): RideSummary? {
        val s = samples.sortedBy { it.t }
        if (s.isEmpty()) return null
        var integratedM = 0.0
        var movingS = 0.0
        val bands = DoubleArray(SPEED_BANDS.size)
        val hours = DoubleArray(24)
        for (i in 1 until s.size) {
            val dtMs = s[i].t - s[i - 1].t
            if (dtMs <= 0 || dtMs > MAX_GAP_MS) continue
            val v = s[i - 1].speedKmh ?: continue
            if (v < MOVING_KMH) continue
            val dt = dtMs / 1000.0
            integratedM += v / 3.6 * dt
            movingS += dt
            bands[band(v)] += dt
            hours[Instant.ofEpochMilli(s[i - 1].t).atZone(zone).hour] += dt
        }
        val odoStart = s.firstNotNullOfOrNull { it.odometerTenths }
        val odoEnd = s.lastOrNull { it.odometerTenths != null }?.odometerTenths
        val odoM = if (odoStart != null && odoEnd != null) (odoEnd - odoStart) * 100 else null
        // Trust the odometer unless it went backwards or disagrees with speed by more than 2x either way
        // (1 km slack covers its 0.1 km steps on short rides and a stalled reading).
        val plausible = odoM != null && odoM >= 0 && odoM <= integratedM * 2 + 1_000 && odoM >= integratedM / 2 - 1_000
        val distanceM = if (plausible) odoM!! else integratedM.toInt()
        val eco = s.mapNotNull { it.instantEconomy?.takeIf { e -> e > 0 } }.takeIf { it.isNotEmpty() }?.average()
            ?: s.lastOrNull { (it.averageEconomy ?: 0) > 0 }?.averageEconomy?.toDouble()
        val fuelStart = s.firstNotNullOfOrNull { it.fuel }
        val fuelEnd = s.lastOrNull { it.fuel != null }?.fuel
        return RideSummary(
            start = s.first().t,
            end = s.last().t,
            distanceM = distanceM,
            durationS = ((s.last().t - s.first().t) / 1000).toInt(),
            movingS = movingS.toInt(),
            maxSpeedKmh = s.maxOf { it.speedKmh ?: 0 },
            avgMovingSpeedKmh = if (movingS > 0) distanceM / 1000.0 / (movingS / 3600.0) else 0.0,
            avgEconomy = eco,
            fuelUsed = if (fuelStart != null && fuelEnd != null && fuelStart >= fuelEnd) fuelStart - fuelEnd else null,
            odometerStartTenths = odoStart,
            odometerEndTenths = odoEnd,
            speedBandS = bands.map { it.toInt() },
            hourS = hours.map { it.toInt() },
        )
    }

    /** Rides shorter than this are dropped (ignition on in the parking lot). */
    fun worthKeeping(r: RideSummary) = r.distanceM >= 200 || r.movingS >= 60

    fun stats(all: List<RideRecord>, period: Period, today: LocalDate, zone: ZoneId): PeriodStats {
        fun day(r: RideRecord) = Instant.ofEpochMilli(r.summary.start).atZone(zone).toLocalDate()
        val firstDay = when (period.days) {
            null -> all.minOfOrNull { day(it) } ?: today
            else -> today.minusDays(period.days - 1L)
        }
        val rides = all.filter { day(it) in firstDay..today }
        val previousKm = period.days?.let { d ->
            val prevStart = firstDay.minusDays(d.toLong())
            all.filter { day(it) >= prevStart && day(it) < firstDay }.sumOf { it.summary.distanceKm }
        }
        val monthly = period == Period.YEAR || period == Period.ALL
        val buckets = if (monthly) {
            val from = if (period == Period.YEAR) YearMonth.from(today).minusMonths(11) else YearMonth.from(firstDay)
            val months = generateSequence(from) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(today)) }.toList()
            val byMonth = rides.groupBy { YearMonth.from(day(it)) }
            months.map { m -> Bucket(m.atDay(1), true, byMonth[m].orEmpty().sumOf { it.summary.distanceKm }) }
        } else {
            val byDay = rides.groupBy { day(it) }
            generateSequence(firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }
                .map { d -> Bucket(d, false, byDay[d].orEmpty().sumOf { it.summary.distanceKm }) }.toList()
        }
        val moving = rides.sumOf { it.summary.movingS.toLong() }
        val km = rides.sumOf { it.summary.distanceKm }
        val ecoRides = rides.filter { it.summary.avgEconomy != null && it.summary.distanceM > 0 }
        val days = rides.map { day(it) }.toSortedSet()
        return PeriodStats(
            rides = rides.size,
            totalKm = km,
            previousKm = previousKm,
            durationS = rides.sumOf { it.summary.durationS.toLong() },
            movingS = moving,
            avgSpeedKmh = if (moving > 0) km / (moving / 3600.0) else 0.0,
            topSpeedKmh = rides.maxOfOrNull { it.summary.maxSpeedKmh } ?: 0,
            // Distance-weighted, so a long ride counts more than a 500 m hop.
            avgEconomy = ecoRides.takeIf { it.isNotEmpty() }?.let { list ->
                list.sumOf { it.summary.avgEconomy!! * it.summary.distanceM } / list.sumOf { it.summary.distanceM.toDouble() }
            },
            fuelUsed = rides.mapNotNull { it.summary.fuelUsed }.takeIf { it.isNotEmpty() }?.sum(),
            buckets = buckets,
            weekdayKm = DayOfWeek.entries.map { dow -> rides.filter { day(it).dayOfWeek == dow }.sumOf { it.summary.distanceKm } },
            hourMinutes = (0 until 24).map { h -> rides.sumOf { it.summary.hourS.getOrElse(h) { 0 }.toDouble() } / 60.0 },
            speedBandMinutes = SPEED_BANDS.indices.map { b -> rides.sumOf { it.summary.speedBandS.getOrElse(b) { 0 }.toDouble() } / 60.0 },
            longestRide = rides.maxByOrNull { it.summary.distanceM },
            fastestRide = rides.maxByOrNull { it.summary.maxSpeedKmh },
            bestEconomyRide = ecoRides.filter { it.summary.distanceM >= 1_000 }.maxByOrNull { it.summary.avgEconomy!! },
            activeDays = days.size,
            longestStreakDays = longestStreak(days.toList()),
        )
    }

    fun longestStreak(sortedDays: List<LocalDate>): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in sortedDays) {
            run = if (prev != null && d == prev.plusDays(1)) run + 1 else 1
            best = max(best, run)
            prev = d
        }
        return best
    }

    /** At most [maxPoints] (time, value) points for a line chart; each is the mean of its slice. */
    fun downsample(points: List<Pair<Long, Double>>, maxPoints: Int): List<Pair<Long, Double>> {
        if (points.size <= maxPoints || maxPoints < 2) return points
        val size = points.size.toDouble() / maxPoints
        return (0 until maxPoints).map { i ->
            val slice = points.subList((i * size).toInt(), ((i + 1) * size).toInt().coerceAtMost(points.size).coerceAtLeast((i * size).toInt() + 1))
            slice[slice.size / 2].first to slice.sumOf { it.second } / slice.size
        }
    }
}
