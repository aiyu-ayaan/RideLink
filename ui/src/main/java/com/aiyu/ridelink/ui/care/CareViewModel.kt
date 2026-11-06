package com.aiyu.ridelink.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.care.CareItem
import com.aiyu.ridelink.core.care.CareMath
import com.aiyu.ridelink.core.care.CareRecord
import com.aiyu.ridelink.core.care.CareStatus
import com.aiyu.ridelink.core.care.CareStore
import com.aiyu.ridelink.core.care.Fill
import com.aiyu.ridelink.core.settings.Settings
import com.aiyu.ridelink.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Everything the Fuel and service page and the Ride screen card show. */
data class CareUi(
    val fills: List<Fill> = emptyList(),
    val spans: List<CareMath.Span> = emptyList(),
    val statuses: List<CareStatus> = emptyList(),
    /** Live from the display, else the last saved reading of this bike. */
    val odometerTenths: Int? = null,
) {
    val averageKmpl get() = CareMath.averageKmpl(spans)
    val lastKmpl get() = spans.lastOrNull()?.kmpl
    val costPerKm get() = CareMath.costPerKm(spans)
    /** A refuel RideLink found that still has no litres. */
    val pendingRefuel get() = fills.firstOrNull { it.auto && it.litres == null }
}

@HiltViewModel
class CareViewModel @Inject constructor(
    private val store: CareStore,
    controller: ClusterController,
    private val repo: SettingsRepository,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val ui: StateFlow<CareUi> = combine(store.fills, store.records, store.reading, controller.telemetry) { fills, records, reading, t ->
        val odo = t.odometerTenths ?: reading?.odometerTenths
        CareUi(
            fills = fills,
            spans = CareMath.spans(fills),
            statuses = records.map { CareMath.status(it, odo, LocalDate.now()) },
            odometerTenths = odo,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareUi())

    fun addFill(odometerTenths: Int, litres: Double?, cost: Double?, full: Boolean) =
        viewModelScope.launch { store.addFill(odometerTenths, litres, cost, full, auto = false) }

    fun updateFill(f: Fill) = viewModelScope.launch { store.updateFill(f) }
    fun deleteFill(id: Long) = viewModelScope.launch { store.deleteFill(id) }
    fun save(r: CareRecord) = viewModelScope.launch { store.saveRecord(r) }
    fun setDue(item: CareItem, date: LocalDate?) = viewModelScope.launch {
        val r = store.records.value.first { it.item == item }
        store.saveRecord(r.copy(due = date))
    }

    fun setRefuelDetect(on: Boolean) = viewModelScope.launch { repo.setRefuelDetect(on) }
    fun setLowFuelAlert(on: Boolean) = viewModelScope.launch { repo.setLowFuelAlert(on) }
    fun setLowFuelKm(km: Int) = viewModelScope.launch { repo.setLowFuelKm(km) }
    fun setCareReminders(on: Boolean) = viewModelScope.launch { repo.setCareReminders(on) }
}
