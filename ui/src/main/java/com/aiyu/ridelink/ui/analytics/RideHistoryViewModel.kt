package com.aiyu.ridelink.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.ridelink.core.analytics.RideFilter
import com.aiyu.ridelink.core.analytics.RideRecord
import com.aiyu.ridelink.core.analytics.RideStore
import com.aiyu.ridelink.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** What the history list shows: the loaded pages, and whether more are loading or left. */
data class HistoryState(
    val rides: List<RideRecord> = emptyList(),
    val loading: Boolean = true,
    val endReached: Boolean = false,
    /** Longest stored ride, km: the top of the distance slider. */
    val maxKm: Double = 0.0,
)

/**
 * Ride history, [PAGE] rides at a time from SQLite. A new filter starts again from the top; a change in the
 * store (rename, star, delete, a new ride) reloads the pages already shown so the list keeps its place.
 */
@HiltViewModel
class RideHistoryViewModel @Inject constructor(
    private val store: RideStore,
    settings: SettingsRepository,
) : ViewModel() {
    private val _filter = MutableStateFlow(RideFilter())
    val filter: StateFlow<RideFilter> = _filter.asStateFlow()

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        // The selected scooter's rides, as on the Analytics tab.
        viewModelScope.launch {
            settings.settings.map { it.deviceAddress }.distinctUntilChanged().collect { b -> _filter.update { it.copy(bike = b) } }
        }
        viewModelScope.launch {
            var last: RideFilter? = null
            combine(_filter, store.rides) { f, all -> f to all }.collect { (f, all) ->
                _state.update { it.copy(maxKm = all.maxOfOrNull { r -> r.summary.distanceKm } ?: 0.0) }
                load(f, reset = true, keep = if (f == last) _state.value.rides.size else 0)
                last = f
            }
        }
    }

    fun setFilter(f: RideFilter) { _filter.value = f }

    /** Next page; ignored while one is loading or when the last page came back short. */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached) return
        load(_filter.value, reset = false)
    }

    private fun load(f: RideFilter, reset: Boolean, keep: Int = 0) {
        job?.cancel()
        val offset = if (reset) 0 else _state.value.rides.size
        // A reload after a store change fetches what was on screen, at least one page.
        val limit = if (reset) maxOf(PAGE, keep) else PAGE
        _state.update { it.copy(loading = true) }
        job = viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val page = store.page(f, offset, limit, LocalDate.now(zone), zone)
            _state.update { s ->
                s.copy(rides = if (reset) page else s.rides + page, loading = false, endReached = page.size < limit)
            }
        }
    }

    fun setStarred(id: Long, starred: Boolean) = viewModelScope.launch { store.setStarred(id, starred) }

    private companion object {
        const val PAGE = 20
    }
}
