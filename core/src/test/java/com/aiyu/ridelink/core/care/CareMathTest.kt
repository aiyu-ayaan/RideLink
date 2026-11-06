package com.aiyu.ridelink.core.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CareMathTest {
    private fun fill(odoKm: Int, litres: Double?, full: Boolean = true, cost: Double? = null, id: Long = odoKm.toLong()) =
        Fill(id, "bike", id, odoKm * 10, litres, cost, full, auto = false)

    @Test fun fullToFullMileage() {
        // 0 km full, 150 km partial 2 l, 300 km full 4 l: 300 km on 6 l = 50 km/l.
        val spans = CareMath.spans(listOf(fill(300, 4.0), fill(0, 5.0), fill(150, 2.0, full = false)))
        assertEquals(1, spans.size)
        assertEquals(300.0, spans[0].km, 0.001)
        assertEquals(6.0, spans[0].litres, 0.001)
        assertEquals(50.0, spans[0].kmpl, 0.001)
        assertEquals(50.0, CareMath.averageKmpl(spans)!!, 0.001)
    }

    @Test fun spanWithUnknownLitresIsSkipped() {
        val spans = CareMath.spans(listOf(fill(0, 5.0), fill(100, null), fill(250, 3.0)))
        // 0 -> 100 has an unknown fill; 100 -> 250 is measured from the auto-detected full fill.
        assertEquals(1, spans.size)
        assertEquals(150.0, spans[0].km, 0.001)
        assertEquals(50.0, spans[0].kmpl, 0.001)
    }

    @Test fun noMileageWithOneFill() {
        assertTrue(CareMath.spans(listOf(fill(0, 5.0))).isEmpty())
        assertNull(CareMath.averageKmpl(emptyList()))
    }

    @Test fun costPerKm() {
        val spans = CareMath.spans(listOf(fill(0, 5.0, cost = 500.0), fill(200, 4.0, cost = 420.0)))
        assertEquals(2.1, CareMath.costPerKm(spans)!!, 0.001)
        // A fill without a price makes the span unpriced.
        assertNull(CareMath.costPerKm(CareMath.spans(listOf(fill(0, 5.0), fill(200, 4.0)))))
    }

    @Test fun distanceItemStatus() {
        val oil = CareRecord(CareItem.ENGINE_OIL, everyKm = 3000, doneOdometerTenths = 10_000)
        val today = LocalDate.of(2026, 11, 6)
        assertEquals(Urgency.OK, CareMath.status(oil, 20_000, today).urgency)
        assertEquals(2000, CareMath.status(oil, 20_000, today).kmLeft)
        assertEquals(Urgency.SOON, CareMath.status(oil, 38_500, today).urgency)
        assertEquals(Urgency.OVERDUE, CareMath.status(oil, 41_000, today).urgency)
        assertEquals(Urgency.UNKNOWN, CareMath.status(CareRecord(CareItem.ENGINE_OIL), 41_000, today).urgency)
        assertEquals(Urgency.UNKNOWN, CareMath.status(oil, null, today).urgency)
    }

    @Test fun paperStatus() {
        val today = LocalDate.of(2026, 11, 6)
        fun puc(d: LocalDate) = CareMath.status(CareRecord(CareItem.PUC, due = d), null, today)
        assertEquals(Urgency.OK, puc(today.plusDays(40)).urgency)
        assertEquals(Urgency.SOON, puc(today.plusDays(15)).urgency)
        assertEquals(Urgency.OVERDUE, puc(today.minusDays(1)).urgency)
        assertEquals(-1L, puc(today.minusDays(1)).daysLeft)
    }

    @Test fun refuelNeedsAJumpHeldWhileStanding() {
        val d = RefuelDetector(level = 2)
        assertFalse(d.feed(5, standing = true, now = 0))
        assertFalse(d.feed(5, standing = true, now = 5_000))
        assertTrue(d.feed(5, standing = true, now = 10_000))
        // Reported once.
        assertFalse(d.feed(5, standing = true, now = 20_000))
        assertEquals(5, d.baseline)
    }

    @Test fun refuelIgnoresWobbleAndShortSpikes() {
        val d = RefuelDetector(level = 3)
        assertFalse(d.feed(4, standing = true, now = 0))
        assertFalse(d.feed(4, standing = true, now = 60_000))
        // A short spike falls back before the hold ends.
        assertFalse(d.feed(6, standing = true, now = 61_000))
        assertFalse(d.feed(3, standing = true, now = 65_000))
        assertFalse(d.feed(6, standing = true, now = 66_000))
        assertFalse(d.feed(3, standing = true, now = 80_000))
        assertEquals(3, d.baseline)
    }

    @Test fun refuelWaitsUntilStanding() {
        val d = RefuelDetector(level = 1)
        assertFalse(d.feed(5, standing = false, now = 0))
        assertFalse(d.feed(5, standing = false, now = 20_000))
        assertTrue(d.feed(5, standing = true, now = 25_000))
    }

    @Test fun fuelDropSettlesAndAShortZeroIsIgnored() {
        val d = RefuelDetector(level = 5)
        assertFalse(d.feed(0, standing = true, now = 0))
        assertFalse(d.feed(5, standing = true, now = 2_000))
        assertEquals(5, d.baseline)
        assertFalse(d.feed(3, standing = false, now = 3_000))
        assertFalse(d.feed(3, standing = false, now = 14_000))
        assertEquals(3, d.baseline)
    }

    @Test fun firstReadingSetsTheBaseline() {
        val d = RefuelDetector(level = null)
        assertFalse(d.feed(4, standing = true, now = 0))
        assertEquals(4, d.baseline)
    }

    @Test fun lowFuelByRangeOnceThenRearms() {
        val w = LowFuelWatch(limitKm = 25)
        assertFalse(w.feed(30, null, 0))
        assertFalse(w.feed(24, null, 1_000))
        assertTrue(w.feed(24, null, 31_000))
        assertFalse(w.feed(20, null, 90_000))
        // Refuelled, then low again.
        assertFalse(w.feed(150, null, 100_000))
        assertFalse(w.feed(22, null, 200_000))
        assertTrue(w.feed(22, null, 230_000))
    }

    @Test fun lowFuelFallsBackToStockBarRule() {
        val w = LowFuelWatch(limitKm = 25)
        assertFalse(w.feed(null, 2, 0))
        assertFalse(w.feed(0, 1, 1_000))
        assertTrue(w.feed(0, 1, 31_000))
    }

    @Test fun displayTextFitsTheTextFrame() {
        val today = LocalDate.of(2026, 11, 6)
        val oil = CareMath.status(CareRecord(CareItem.ENGINE_OIL, 3000, 10_000), 38_800, today)
        assertEquals("OIL IN 120 KM", careDisplayText(oil))
        val ins = CareMath.status(CareRecord(CareItem.INSURANCE, due = today.minusDays(2)), null, today)
        assertEquals("INSURANCE EXPIRED", careDisplayText(ins))
        // Only SOON / OVERDUE items reach the display: at most SOON_KM km or SOON_DAYS days left.
        CareItem.entries.forEach { item ->
            val soon = CareMath.status(CareRecord(item, CareMath.SOON_KM, 0, today.plusDays(CareMath.SOON_DAYS)), 0, today)
            val late = CareMath.status(CareRecord(item, 1, 0, today.minusDays(1)), 100, today)
            assertTrue(careDisplayText(soon).length <= 17)
            assertTrue(careDisplayText(late).length <= 17)
        }
    }
}
