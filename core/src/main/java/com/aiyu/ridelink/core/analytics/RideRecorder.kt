package com.aiyu.ridelink.core.analytics

import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.core.protocol.DisplayTelemetry
import com.aiyu.ridelink.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the display's live data into stored rides, without any button: a ride starts at the first movement
 * while connected (or during Demo ride) and ends when the link drops, the demo stops, or the scooter stands
 * still for [IDLE_END_MS]. Started once from the Application; runs for the process lifetime.
 */
@Singleton
class RideRecorder @Inject constructor(
    private val controller: ClusterController,
    private val store: RideStore,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    @Volatile private var telemetryAt = 0L
    private var current: MutableList<RideSample>? = null
    private val turns = mutableListOf<RideTurn>()
    private var currentIsDemo = false
    private var lastMovingAt = 0L

    private val _recording = MutableStateFlow(false)
    /** True while a ride is being recorded; the Ride screen shows a small indicator. */
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    fun start() {
        if (started) return
        started = true
        scope.launch { controller.telemetry.collect { telemetryAt = System.currentTimeMillis() } }
        scope.launch {
            while (isActive) {
                tick(System.currentTimeMillis())
                delay(RideMath.SAMPLE_MS)
            }
        }
    }

    private suspend fun tick(now: Long) {
        val demo = controller.demo.value
        val connected = controller.linkState.value is LinkState.Ready
        val active = (connected || demo) && settings.current().recordRides
        // Ignore readings the display stopped refreshing; a frozen speed would add phantom distance.
        val fresh = now - telemetryAt < STALE_MS
        val t = controller.telemetry.value
        val speed = if (fresh) t.speedKmh else null
        val ride = current
        when {
            !active -> if (ride != null) finish()
            ride == null -> if ((speed ?: 0) >= RideMath.MOVING_KMH) {
                current = mutableListOf(sample(now, t, speed))
                turns.clear()
                noteTurn(now)
                currentIsDemo = demo
                lastMovingAt = now
                _recording.value = true
            }
            else -> {
                ride += sample(now, t, speed)
                noteTurn(now)
                if ((speed ?: 0) >= RideMath.MOVING_KMH) lastMovingAt = now
                else if (now - lastMovingAt > IDLE_END_MS) finish()
            }
        }
    }

    private fun activeRoute() = controller.nav.value?.takeIf { !it.stopped }

    private fun sample(now: Long, t: DisplayTelemetry, speed: Int?) = RideSample(
        t = now, speedKmh = speed, odometerTenths = t.odometerTenths, tripTenths = t.tripTenths, fuel = t.fuelLevel,
        rangeKm = t.rangeKm, rpm = t.rpm, instantEconomy = t.instantEconomy, averageEconomy = t.averageEconomy,
        navLeftM = activeRoute()?.distanceLeftM?.takeIf { it > 0 },
    )

    /** A new Google Maps instruction since the last one recorded becomes a timeline entry. */
    private fun noteTurn(now: Long) {
        val step = activeRoute() ?: return
        val last = turns.lastOrNull()
        if (last != null && last.instruction == step.instruction && last.maneuver == step.maneuver.name) return
        turns += RideTurn(now, step.instruction, step.maneuver.name, step.distanceToTurnM, step.distanceLeftM)
    }

    private suspend fun finish() {
        val samples = current ?: return
        current = null
        _recording.value = false
        // Drop the standing-still tail so the ride ends when the wheels stopped.
        val trimmed = samples.filter { it.t <= lastMovingAt + RideMath.SAMPLE_MS }
        val summary = RideMath.summarize(trimmed, ZoneId.systemDefault()) ?: return
        val kept = turns.filter { it.t <= lastMovingAt + RideMath.SAMPLE_MS }
        turns.clear()
        if (RideMath.worthKeeping(summary)) store.save(summary, trimmed, currentIsDemo, kept)
    }

    /** Fills Analytics with labelled sample rides (demo = true) so it can be previewed without riding. */
    fun addSampleRides() = scope.launch {
        val zone = ZoneId.systemDefault()
        SampleRides.generate(LocalDate.now(zone), zone).forEach { samples ->
            RideMath.summarize(samples, zone)?.let { store.save(it, samples, demo = true, turns = SampleRides.turnsFor(samples)) }
        }
    }

    private companion object {
        const val STALE_MS = 10_000L
        const val IDLE_END_MS = 10 * 60_000L
    }
}
