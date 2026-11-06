package com.aiyu.ridelink.core.care

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One fuel stop. [odometerTenths] in 0.1 km like the display; [litres] null until the rider fills it in. */
data class Fill(
    val id: Long,
    val bike: String?,
    val t: Long,
    val odometerTenths: Int,
    val litres: Double?,
    val cost: Double?,
    /** Filled to the brim; mileage is only measured between two full fills. */
    val full: Boolean,
    /** Found by refuel detection (the fuel level jumped while standing), not typed in. */
    val auto: Boolean,
)

/** Things to service by distance, and papers that expire on a date. */
enum class CareItem(val defaultEveryKm: Int?) {
    ENGINE_OIL(3_000),
    AIR_FILTER(12_000),
    BRAKES(6_000),
    SPARK_PLUG(12_000),
    INSURANCE(null),
    PUC(null),
    ;

    val byDate get() = defaultEveryKm == null
}

/** A care item's saved state for one bike. [doneOdometerTenths] = odometer when it was last done. */
data class CareRecord(
    val item: CareItem,
    val everyKm: Int? = item.defaultEveryKm,
    val doneOdometerTenths: Int? = null,
    /** Expiry date for [CareItem.byDate] items. */
    val due: LocalDate? = null,
)

enum class Urgency { OK, SOON, OVERDUE, UNKNOWN }

data class CareStatus(val record: CareRecord, val urgency: Urgency, val kmLeft: Int? = null, val daysLeft: Long? = null)

object CareMath {
    /** Distance items turn SOON this many km before they are due. */
    const val SOON_KM = 200

    /** Papers turn SOON this many days before they expire. */
    const val SOON_DAYS = 15L

    /** Mileage of one span between two full fills. */
    data class Span(val fromTenths: Int, val toTenths: Int, val litres: Double, val cost: Double?) {
        val km get() = (toTenths - fromTenths) / 10.0
        val kmpl get() = km / litres
    }

    /**
     * Full-to-full method: the fuel burnt between two full fills is everything poured in after the first, up to
     * and including the second. A span with a fill whose litres are unknown is skipped, as is one that goes
     * backwards (odometer typo).
     */
    fun spans(fills: List<Fill>): List<Span> {
        val sorted = fills.sortedWith(compareBy({ it.odometerTenths }, { it.t }))
        val out = mutableListOf<Span>()
        var start: Fill? = null
        var litres = 0.0
        var cost: Double? = 0.0
        var known = true
        for (f in sorted) {
            if (start != null) {
                if (f.litres == null) known = false else litres += f.litres
                cost = if (cost != null && f.cost != null) cost + f.cost else null
            }
            if (!f.full) continue
            if (start != null && known && litres > 0 && f.odometerTenths > start.odometerTenths) {
                out += Span(start.odometerTenths, f.odometerTenths, litres, cost)
            }
            start = f
            litres = 0.0
            cost = 0.0
            known = true
        }
        return out
    }

    /** Average over every measured span (total km / total litres), or null with fewer than two full fills. */
    fun averageKmpl(spans: List<Span>): Double? {
        val litres = spans.sumOf { it.litres }
        return if (litres > 0) spans.sumOf { it.km } / litres else null
    }

    /** Money per km over the spans whose every fill has a cost. */
    fun costPerKm(spans: List<Span>): Double? {
        val priced = spans.filter { it.cost != null }
        val km = priced.sumOf { it.km }
        return if (km > 0) priced.sumOf { it.cost!! } / km else null
    }

    fun status(r: CareRecord, odometerTenths: Int?, today: LocalDate): CareStatus {
        if (r.item.byDate) {
            val due = r.due ?: return CareStatus(r, Urgency.UNKNOWN)
            val days = ChronoUnit.DAYS.between(today, due)
            val u = when {
                days < 0 -> Urgency.OVERDUE
                days <= SOON_DAYS -> Urgency.SOON
                else -> Urgency.OK
            }
            return CareStatus(r, u, daysLeft = days)
        }
        val every = r.everyKm ?: return CareStatus(r, Urgency.UNKNOWN)
        val done = r.doneOdometerTenths ?: return CareStatus(r, Urgency.UNKNOWN)
        val now = odometerTenths ?: return CareStatus(r, Urgency.UNKNOWN)
        val left = (done + every * 10 - now) / 10
        val u = when {
            left < 0 -> Urgency.OVERDUE
            left <= SOON_KM -> Urgency.SOON
            else -> Urgency.OK
        }
        return CareStatus(r, u, kmLeft = left)
    }
}
