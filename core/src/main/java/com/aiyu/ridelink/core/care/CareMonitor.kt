package com.aiyu.ridelink.core.care

import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the display's odometer, fuel bars and range while connected: logs refuels, raises the low-fuel prompt
 * and, once per connect, checks service items and papers. The app module turns [events] into notifications
 * (core holds no user-facing strings). Started once from the Application.
 */
@Singleton
class CareMonitor @Inject constructor(
    private val controller: ClusterController,
    private val store: CareStore,
    private val settings: SettingsRepository,
) {
    sealed interface Event {
        data class Refuel(val odometerKm: Double, val fromBars: Int, val toBars: Int) : Event
        data class LowFuel(val rangeKm: Int?, val bars: Int?) : Event
        data class Due(val items: List<CareStatus>) : Event
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var started = false
    private var session: Session? = null

    /** State for one connection to one bike. */
    private class Session(val bike: String, val refuel: RefuelDetector, val lowFuel: LowFuelWatch) {
        var dueChecked = false
        var saved: BikeReading? = null
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(controller.linkState, controller.telemetry) { l, t -> l to t }.collect { (link, t) ->
                if (link !is LinkState.Ready || controller.demo.value) {
                    session = null
                    return@collect
                }
                val s = settings.current()
                val bike = s.deviceAddress ?: return@collect
                val current = session?.takeIf { it.bike == bike } ?: Session(
                    bike,
                    RefuelDetector(store.readingOf(bike)?.fuelBars),
                    LowFuelWatch(s.lowFuelKm),
                ).also { session = it }
                val now = System.currentTimeMillis()
                val bars = t.fuelBars(controller.currentModel().family)
                val odo = t.odometerTenths

                val before = current.refuel.baseline
                if (current.refuel.feed(bars, standing = (t.speedKmh ?: 0) == 0, now) && s.refuelDetect && odo != null && before != null) {
                    store.addFill(odo, litres = null, cost = null, full = true, auto = true)
                    controller.note("Refuel detected at ${odo / 10.0} km (fuel $before -> $bars bars)")
                    _events.tryEmit(Event.Refuel(odo / 10.0, before, bars ?: 0))
                }
                if (s.lowFuelAlert && current.lowFuel.feed(t.rangeKm, bars, now)) {
                    controller.note("Low fuel: range ${t.rangeKm} km, ${bars} bars")
                    _events.tryEmit(Event.LowFuel(t.rangeKm?.takeIf { it > 0 }, bars))
                }
                // The refuel baseline, not the raw bars, is what the next connect compares against.
                val reading = BikeReading(odo, current.refuel.baseline, now)
                val last = current.saved
                if (last == null || last.fuelBars != reading.fuelBars || (odo != null && (last.odometerTenths ?: 0) / 10 != odo / 10)) {
                    store.saveReading(bike, reading)
                    current.saved = reading
                }
                if (!current.dueChecked && odo != null) {
                    current.dueChecked = true
                    checkDue(odo)
                }
            }
        }
    }

    private fun dueNow(odometerTenths: Int?): List<CareStatus> {
        val today = LocalDate.now()
        return store.records.value.map { CareMath.status(it, odometerTenths, today) }
            .filter { it.urgency == Urgency.SOON || it.urgency == Urgency.OVERDUE }
    }

    // Developer tests (Console): the same store writes, display text and notifications as the real triggers.

    /** A fuel stop at the current (or last saved) odometer, as a detected refuel adds it. */
    fun testRefuel() = scope.launch {
        val odo = controller.telemetry.value.odometerTenths ?: store.reading.value?.odometerTenths ?: 0
        store.addFill(odo, litres = null, cost = null, full = true, auto = true)
        controller.note("Test: refuel at ${odo / 10.0} km")
        _events.tryEmit(Event.Refuel(odo / 10.0, 1, 5))
    }

    fun testLowFuel() = scope.launch {
        controller.note("Test: low fuel")
        _events.tryEmit(Event.LowFuel(rangeKm = 20, bars = 1))
    }

    /** What is due now (or a sample oil change when nothing is), on the display and as a notification. */
    fun testDue() = scope.launch {
        val odo = controller.telemetry.value.odometerTenths ?: store.reading.value?.odometerTenths
        val due = dueNow(odo).ifEmpty {
            listOf(CareStatus(CareRecord(CareItem.ENGINE_OIL, 3000, 0), Urgency.SOON, kmLeft = 120))
        }
        controller.note("Test: ${careDisplayText(due.first())}")
        controller.flashText(careDisplayText(due.first()))
        _events.tryEmit(Event.Due(due))
    }

    private suspend fun checkDue(odometerTenths: Int) {
        val s = settings.current()
        if (!s.careReminders) return
        val today = LocalDate.now()
        val due = dueNow(odometerTenths)
        if (due.isEmpty()) return
        // The display gets it on every connect; the phone notification at most once a day.
        controller.flashText(careDisplayText(due.first()))
        if (s.careNotifiedOn == today.toString()) return
        settings.setCareNotifiedOn(today.toString())
        _events.tryEmit(Event.Due(due))
    }
}

/** At most 17 letters, digits and spaces (the text frame), e.g. "OIL IN 120 KM", "INSURANCE EXPIRED". */
fun careDisplayText(st: CareStatus): String {
    val name = when (st.record.item) {
        CareItem.ENGINE_OIL -> "OIL"
        CareItem.AIR_FILTER -> "FILTER"
        CareItem.BRAKES -> "BRAKES"
        CareItem.SPARK_PLUG -> "PLUG"
        CareItem.INSURANCE -> "INSURANCE"
        CareItem.PUC -> "PUC"
    }
    val left = st.kmLeft?.let { if (it < 0) "OVERDUE" else "IN $it KM" }
        ?: st.daysLeft?.let { if (it < 0) "EXPIRED" else "IN $it D" }
        ?: "DUE"
    return "$name $left"
}
