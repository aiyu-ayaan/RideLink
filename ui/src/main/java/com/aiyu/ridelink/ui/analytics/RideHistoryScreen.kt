package com.aiyu.ridelink.ui.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.analytics.Period
import com.aiyu.ridelink.core.analytics.RideFilter
import com.aiyu.ridelink.core.analytics.RideRecord
import com.aiyu.ridelink.core.analytics.RideSort
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.nav.appBarColors
import com.aiyu.ridelink.ui.theme.Dimens
import com.aiyu.ridelink.ui.update.BackButton
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The rider's name for the ride, else "From A to B" / "Around A" from where it started and ended, else the date. */
@Composable
internal fun rideTitle(r: RideRecord): String {
    r.name?.let { return it }
    val from = r.fromPlace
    val to = r.toPlace
    return when {
        from != null && to != null && from != to -> stringResource(R.string.ride_from_to, from, to)
        from != null && to != null -> stringResource(R.string.ride_around, from)
        from != null -> stringResource(R.string.ride_from, from)
        to != null -> stringResource(R.string.ride_around, to)
        else -> Fmt.dateTime(r.summary.start)
    }
}

/** One ride in a list: title, date when the title is not the date, stats, sample tag and a star toggle. */
@Composable
internal fun RideRow(r: RideRecord, onStar: ((Boolean) -> Unit)?, onClick: () -> Unit) {
    val s = r.summary
    val title = rideTitle(r)
    val date = Fmt.dateTime(s.start)
    ListItem(
        overlineContent = if (title != date) ({ Text(date) }) else null,
        headlineContent = { Text(title, maxLines = 2) },
        supportingContent = {
            Text(stringResource(R.string.ride_row, Fmt.km(s.distanceKm), Fmt.duration(s.movingS.toLong()), Fmt.whole(s.avgMovingSpeedKmh), s.maxSpeedKmh))
        },
        leadingContent = { Icon(Icons.Rounded.TwoWheeler, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r.demo) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                        Text(
                            stringResource(R.string.analytics_sample),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs),
                        )
                    }
                }
                if (onStar != null) StarToggle(r.starred, onStar)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
internal fun StarToggle(starred: Boolean, onChange: (Boolean) -> Unit) {
    IconToggleButton(checked = starred, onCheckedChange = onChange) {
        Icon(
            if (starred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription = stringResource(if (starred) R.string.ride_unstar else R.string.ride_star),
            tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Every ride of the selected scooter, 20 at a time: search, quick filter chips (period, starred, with a Maps
 * route) and a Filters sheet for distance, top speed, sample rides and sort order.
 */
@Composable
fun RideHistoryScreen(onBack: () -> Unit, onOpenRide: (Long) -> Unit, vm: RideHistoryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Next page when the last few rows come into view.
    val current by rememberUpdatedState(state)
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (last >= list.layoutInfo.totalItemsCount - PREFETCH && !current.endReached) vm.loadMore()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).widthIn(max = Dimens.contentMaxWidth), state = list) {
            item {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = { vm.setFilter(filter.copy(query = it)) },
                    placeholder = { Text(stringResource(R.string.history_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filter.query.isNotEmpty()) IconButton(onClick = { vm.setFilter(filter.copy(query = "")) }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.history_clear_search))
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = Dimens.sm),
                )
            }
            item { FilterChips(filter, vm::setFilter) { sheet = true } }
            items(state.rides, key = { it.id }) { r ->
                RideRow(r, onStar = { vm.setStarred(r.id, it) }) { onOpenRide(r.id) }
            }
            when {
                state.loading -> item {
                    Row(Modifier.fillMaxWidth().padding(Dimens.xl), horizontalArrangement = Arrangement.Center) {
                        LoadingIndicator(Modifier.size(48.dp))
                    }
                }
                state.rides.isEmpty() -> item {
                    Column(Modifier.fillMaxWidth().padding(Dimens.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { vm.setFilter(RideFilter(bike = filter.bike)) }) { Text(stringResource(R.string.history_clear_filters)) }
                    }
                }
            }
            item { Spacer(Modifier.height(Dimens.xxl)) }
        }
    }

    if (sheet) FilterSheet(filter, state.maxKm, onApply = { vm.setFilter(it); sheet = false }, onDismiss = { sheet = false })
}

/** Quick filters in one scrolling row; the first chip opens the sheet and shows how many of its settings are on. */
@Composable
private fun FilterChips(filter: RideFilter, onChange: (RideFilter) -> Unit, onOpenSheet: () -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.gutter),
        modifier = Modifier.padding(bottom = Dimens.sm),
    ) {
        item {
            val n = filter.advancedCount
            FilterChip(
                selected = n > 0,
                onClick = onOpenSheet,
                label = { Text(stringResource(R.string.filter_more)) },
                leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
                trailingIcon = if (n > 0) ({ Badge { Text(n.toString()) } }) else null,
            )
        }
        items(listOf(Period.WEEK, Period.MONTH, Period.YEAR)) { p ->
            Chip(
                stringResource(when (p) { Period.WEEK -> R.string.period_week; Period.MONTH -> R.string.period_month; else -> R.string.period_year }),
                filter.period == p,
            ) { onChange(filter.copy(period = if (filter.period == p) Period.ALL else p)) }
        }
        item { Chip(stringResource(R.string.filter_starred), filter.starred) { onChange(filter.copy(starred = !filter.starred)) } }
        item { Chip(stringResource(R.string.filter_route), filter.navigated) { onChange(filter.copy(navigated = !filter.navigated)) } }
    }
}

/** M3 filter chip: a check replaces the leading space when selected. */
@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
    )
}

/** Advanced filters on a draft; nothing changes in the list until "Show rides". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(filter: RideFilter, maxKm: Double, onApply: (RideFilter) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(filter) }
    // Slider top: the longest ride rounded up to 5 km, at least 5.
    val top = (ceil(maxKm / 5.0) * 5).coerceAtLeast(5.0).toFloat()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Dimens.xl).padding(bottom = Dimens.xl), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
            Text(stringResource(R.string.filter_more), style = MaterialTheme.typography.titleLargeEmphasized)

            val lo = (draft.minKm ?: 0.0).toFloat().coerceIn(0f, top)
            val hi = (draft.maxKm ?: top.toDouble()).toFloat().coerceIn(lo, top)
            Text(stringResource(R.string.filter_distance), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.filter_distance_value, Fmt.km(lo.toDouble()), Fmt.km(hi.toDouble())), color = MaterialTheme.colorScheme.onSurfaceVariant)
            RangeSlider(
                value = lo..hi,
                onValueChange = { r ->
                    draft = draft.copy(
                        minKm = r.start.toDouble().takeIf { it > 0.0 },
                        maxKm = r.endInclusive.toDouble().takeIf { it < top },
                    )
                },
                valueRange = 0f..top,
            )

            val speed = draft.minTopSpeedKmh ?: 0
            Text(stringResource(R.string.filter_top_speed), style = MaterialTheme.typography.titleSmall)
            Text(
                if (speed == 0) stringResource(R.string.filter_any) else "$speed ${stringResource(R.string.dash_kmh)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = speed.toFloat(),
                onValueChange = { draft = draft.copy(minTopSpeedKmh = (it / 10).roundToInt().times(10).takeIf { v -> v > 0 }) },
                valueRange = 0f..120f,
                steps = 11,
            )

            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Switch) { draft = draft.copy(samples = !draft.samples) }
                    .semantics { stateDescription = draft.samples.toString() }
                    .padding(vertical = Dimens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.filter_samples), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = draft.samples, onCheckedChange = null)
            }

            Text(stringResource(R.string.filter_sort), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                RideSort.entries.forEach { s ->
                    Chip(
                        stringResource(
                            when (s) {
                                RideSort.NEWEST -> R.string.sort_newest
                                RideSort.OLDEST -> R.string.sort_oldest
                                RideSort.LONGEST -> R.string.sort_longest
                                RideSort.FASTEST -> R.string.sort_fastest
                                RideSort.LONGEST_TIME -> R.string.sort_longest_time
                            },
                        ),
                        draft.sort == s,
                    ) { draft = draft.copy(sort = s) }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm, Alignment.End)) {
                TextButton(onClick = {
                    draft = draft.copy(minKm = null, maxKm = null, minTopSpeedKmh = null, samples = true, sort = RideSort.NEWEST)
                }) { Text(stringResource(R.string.filter_reset)) }
                Button(onClick = { onApply(draft) }) { Text(stringResource(R.string.filter_apply)) }
            }
        }
    }
}

/** Ride name dialog; an empty name goes back to the automatic one. */
@Composable
internal fun RenameDialog(current: String?, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current.orEmpty()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
        title = { Text(stringResource(R.string.ride_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(NAME_MAX) },
                label = { Text(stringResource(R.string.ride_name)) },
                supportingText = { Text(stringResource(R.string.ride_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim().ifEmpty { null }) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val PREFETCH = 5
private const val NAME_MAX = 60
