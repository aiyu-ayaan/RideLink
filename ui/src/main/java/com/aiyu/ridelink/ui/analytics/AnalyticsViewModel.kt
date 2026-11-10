package com.aiyu.ridelink.ui.analytics

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.analytics.Period
import com.aiyu.ridelink.core.analytics.PeriodStats
import com.aiyu.ridelink.core.analytics.RideMath
import com.aiyu.ridelink.core.analytics.RideRecord
import com.aiyu.ridelink.core.analytics.RideRecorder
import com.aiyu.ridelink.core.analytics.RideSample
import com.aiyu.ridelink.core.analytics.RideStore
import com.aiyu.ridelink.core.analytics.RideTurn
import com.aiyu.ridelink.core.settings.GarageStore
import com.aiyu.ridelink.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Everything the Analytics tab shows for the chosen period. */
data class AnalyticsState(
    val period: Period,
    val stats: PeriodStats,
    /** Rides in the period, newest first. */
    val rides: List<RideRecord>,
    val hasAnyRide: Boolean,
    val hasSamples: Boolean,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val store: RideStore,
    private val recorder: RideRecorder,
    controller: ClusterController,
    garage: GarageStore,
    settings: SettingsRepository,
) : ViewModel() {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val period = MutableStateFlow(Period.WEEK)

    val bikes = garage.bikes

    /** Picked on the Analytics tab; null follows the scooter selected on the Ride tab. */
    private val picked = MutableStateFlow<String?>(null)

    /** The scooter whose rides are shown; null when none is paired (then every ride shows). */
    val bike: StateFlow<String?> = combine(settings.settings.map { it.deviceAddress }, picked) { selected, p -> p ?: selected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun pickBike(address: String) { picked.value = address }

    private fun RideRecord.shown(bike: String?) = bike == null || this.bike == null || this.bike == bike

    val telemetry = controller.telemetry
    val recording = recorder.recording

    val state: StateFlow<AnalyticsState> = combine(store.rides, period, bike) { all, p, b -> build(all.filter { it.shown(b) }, p) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(emptyList(), Period.WEEK))

    private fun build(all: List<RideRecord>, p: Period): AnalyticsState {
        val today = LocalDate.now(zone)
        val first = p.days?.let { today.minusDays(it - 1L) }
        val inPeriod = all.filter { r ->
            first == null || !Instant.ofEpochMilli(r.summary.start).atZone(zone).toLocalDate().isBefore(first)
        }
        return AnalyticsState(p, RideMath.stats(all, p, today, zone), inPeriod, all.isNotEmpty(), all.any { it.demo })
    }

    fun setPeriod(p: Period) { period.value = p }
    fun addSampleRides() = recorder.addSampleRides()
    fun deleteSampleRides() = viewModelScope.launch { store.deleteDemo() }
    fun delete(id: Long) = viewModelScope.launch { store.delete(id) }
    fun rename(id: Long, name: String?) = viewModelScope.launch { store.rename(id, name) }
    fun setStarred(id: Long, starred: Boolean) = viewModelScope.launch { store.setStarred(id, starred) }

    /** The newest rides of the shown scooter, for the "Recent rides" list under the stats. */
    val recent: StateFlow<List<RideRecord>> = combine(store.rides, bike) { all, b -> all.filter { it.shown(b) }.take(RECENT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    suspend fun samples(id: Long): List<RideSample> = store.samples(id)
    suspend fun turns(id: Long): List<RideTurn> = store.turns(id)

    /** Every stored ride, unfiltered by period: a ride opened from the records may be older than the period. */
    val allRides = store.rides

    private companion object {
        const val RECENT = 5
    }

    fun exportCsv(uri: Uri, resolver: ContentResolver) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val b = bike.value
            resolver.openOutputStream(uri)?.bufferedWriter()?.use { out -> store.exportCsv(out, zone) { it.shown(b) } }
        }
    }
}
