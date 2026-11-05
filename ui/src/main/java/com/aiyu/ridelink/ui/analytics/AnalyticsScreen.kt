package com.aiyu.ridelink.ui.analytics

import com.aiyu.ridelink.ui.nav.BottomBarSpacer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.aiyu.ridelink.core.settings.Bike
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.analytics.Bucket
import com.aiyu.ridelink.core.analytics.Period
import com.aiyu.ridelink.core.analytics.PeriodStats
import com.aiyu.ridelink.core.analytics.RideMath
import com.aiyu.ridelink.core.analytics.RideRecord
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.ride.Tile
import com.aiyu.ridelink.ui.theme.Dimens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val Period.label
    get() = when (this) {
        Period.WEEK -> R.string.period_week
        Period.MONTH -> R.string.period_month
        Period.YEAR -> R.string.period_year
        Period.ALL -> R.string.period_all
    }

/** "in the last 7 days" etc., used under the hero number and in the change line. */
private val Period.span
    get() = when (this) {
        Period.WEEK -> R.string.span_week
        Period.MONTH -> R.string.span_month
        Period.YEAR -> R.string.span_year
        Period.ALL -> R.string.span_all
    }

@Composable
fun AnalyticsScreen(onOpenRide: (Long) -> Unit, vm: AnalyticsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val telemetry by vm.telemetry.collectAsStateWithLifecycle()
    val bikes by vm.bikes.collectAsStateWithLifecycle()
    val bike by vm.bike.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri, context.contentResolver)
    }
    val s = state.stats

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.tab_analytics)) },
                // Stats are per scooter; name the one shown.
                subtitle = { bikes.firstOrNull { it.address == bike }?.let { Text(it.name ?: it.address) } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.analytics_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.analytics_export)) },
                            enabled = state.hasAnyRide,
                            onClick = { menu = false; export.launch("ridelink-rides.csv") },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.analytics_add_samples)) }, onClick = { menu = false; vm.addSampleRides() })
                        if (state.hasSamples) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.analytics_remove_samples)) }, onClick = { menu = false; vm.deleteSampleRides() })
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).widthIn(max = Dimens.contentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            if (bikes.size > 1) item { BikePicker(bikes, bike, vm::pickBike) }
            item { PeriodPicker(state.period, vm::setPeriod) }
            if (recording) item { RecordingRow() }
            if (!state.hasAnyRide) {
                item { EmptyState(vm::addSampleRides) }
                return@LazyColumn
            }
            item { Hero(s, state.period, telemetry.odometerKm) }
            item { StatTiles(s) }
            item {
                ChartCard(stringResource(R.string.chart_distance)) {
                    DistanceChart(s.buckets, state.period)
                }
            }
            item {
                ChartCard(stringResource(R.string.chart_hours), stringResource(R.string.chart_hours_sub)) {
                    BarChart(
                        values = s.hourMinutes,
                        xLabels = (0 until 24).map { if (it % 6 == 0) hourLabel(it) else "" },
                        describe = { h -> stringResource(R.string.readout_hour, hourLabel(h), Fmt.minutes(s.hourMinutes[h])) },
                        idle = stringResource(R.string.readout_hour_idle, peakHour(s.hourMinutes)),
                        axisFormat = Fmt::tick,
                        description = stringResource(R.string.chart_hours_desc, peakHour(s.hourMinutes)),
                    )
                }
            }
            item {
                ChartCard(stringResource(R.string.chart_weekdays)) {
                    val days = DayOfWeek.entries
                    val monday = LocalDate.now().with(DayOfWeek.MONDAY)
                    BarChart(
                        values = s.weekdayKm,
                        xLabels = days.map { Fmt.weekday(monday.plusDays(it.ordinal.toLong())) },
                        describe = { i -> stringResource(R.string.readout_weekday, Fmt.weekday(monday.plusDays(i.toLong())), Fmt.km(s.weekdayKm[i])) },
                        idle = stringResource(R.string.readout_tap),
                        axisFormat = Fmt::tick,
                        description = stringResource(R.string.chart_weekdays_desc),
                    )
                }
            }
            item {
                ChartCard(stringResource(R.string.chart_speed), stringResource(R.string.chart_speed_sub)) {
                    val labels = RideMath.SPEED_BANDS.mapIndexed { i, lo ->
                        RideMath.SPEED_BANDS.getOrNull(i + 1)?.let { "$lo-${it - 1}" } ?: "$lo+"
                    }
                    BarChart(
                        values = s.speedBandMinutes,
                        xLabels = labels,
                        describe = { i -> stringResource(R.string.readout_speed, labels[i], Fmt.minutes(s.speedBandMinutes[i])) },
                        idle = stringResource(R.string.readout_tap),
                        axisFormat = Fmt::tick,
                        description = stringResource(R.string.chart_speed_desc),
                    )
                }
            }
            item { Records(s, onOpenRide) }
            item {
                Text(
                    stringResource(R.string.analytics_history, state.rides.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.sm),
                )
            }
            items(state.rides, key = { it.id }) { r -> RideRow(r) { onOpenRide(r.id) } }
            item { Spacer(Modifier.height(Dimens.xl)) }
            item { BottomBarSpacer() }
        }
    }
}

private fun hourLabel(h: Int): String = LocalTime.of(h, 0).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private fun peakHour(minutes: List<Double>): String =
    minutes.indices.maxByOrNull { minutes[it] }?.takeIf { minutes[it] > 0 }?.let(::hourLabel) ?: "--"

@Composable
private fun PeriodPicker(current: Period, onPick: (Period) -> Unit) {
    Row(
        Modifier.padding(horizontal = Dimens.gutter).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        Period.entries.forEachIndexed { i, p ->
            ToggleButton(
                checked = p == current,
                onCheckedChange = { onPick(p) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    Period.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(p.label)) }
        }
    }
}

@Composable
private fun BikePicker(bikes: List<Bike>, current: String?, onPick: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = Dimens.gutter).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        bikes.forEach { b ->
            FilterChip(
                selected = b.address == current,
                onClick = { onPick(b.address) },
                label = { Text(b.name ?: b.address) },
                leadingIcon = { Icon(Icons.Rounded.TwoWheeler, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
    }
}

@Composable
private fun RecordingRow() {
    Row(Modifier.padding(horizontal = Dimens.gutter), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.FiberManualRecord, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
        Text(stringResource(R.string.analytics_recording), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Dimens.sm))
    }
}

@Composable
private fun EmptyState(onSamples: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.padding(horizontal = Dimens.gutter).fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.xl), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Icon(Icons.Rounded.QueryStats, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.analytics_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.analytics_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onSamples, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.analytics_add_samples)) }
        }
    }
}

/** The one hero number per view: distance in the period, with the change against the period before. */
@Composable
private fun Hero(s: PeriodStats, period: Period, odometerKm: Double?) {
    val on = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.padding(horizontal = Dimens.gutter).fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.xl)) {
            Text(stringResource(R.string.hero_distance, stringResource(period.span)), style = MaterialTheme.typography.labelLarge, color = on)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.km(s.totalKm), style = MaterialTheme.typography.displayMedium, color = on)
                Text(" " + stringResource(R.string.unit_km), style = MaterialTheme.typography.titleMedium, color = on, modifier = Modifier.padding(bottom = Dimens.sm))
            }
            s.change?.let { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Direction icon carries the sign; distance up is neither good nor bad, so no status color.
                    val icon = when {
                        c > 0.02 -> Icons.AutoMirrored.Rounded.TrendingUp
                        c < -0.02 -> Icons.AutoMirrored.Rounded.TrendingDown
                        else -> Icons.AutoMirrored.Rounded.TrendingFlat
                    }
                    Icon(icon, contentDescription = null, tint = on, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.hero_change, Fmt.percent(c), stringResource(period.span)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = on,
                        modifier = Modifier.padding(start = Dimens.xs),
                    )
                }
            }
            if (odometerKm != null) {
                Text(stringResource(R.string.hero_odometer, Fmt.one(odometerKm)), style = MaterialTheme.typography.bodyMedium, color = on)
            }
        }
    }
}

@Composable
private fun StatTiles(s: PeriodStats) {
    val c = MaterialTheme.colorScheme.onSurface
    val km = stringResource(R.string.unit_km)
    val kmh = stringResource(R.string.dash_kmh)
    Column(Modifier.padding(horizontal = Dimens.gutter), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Tile(stringResource(R.string.stat_rides), s.rides.toString(), "", c, Modifier.weight(1f))
            Tile(stringResource(R.string.stat_time), Fmt.duration(s.movingS), "", c, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Tile(stringResource(R.string.stat_avg_speed), Fmt.whole(s.avgSpeedKmh), kmh, c, Modifier.weight(1f))
            Tile(stringResource(R.string.stat_top_speed), s.topSpeedKmh.toString(), kmh, c, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Tile(stringResource(R.string.stat_mileage), s.avgEconomy?.let(Fmt::one) ?: "--", stringResource(R.string.unit_kmpl), c, Modifier.weight(1f))
            Tile(stringResource(R.string.stat_per_ride), if (s.rides > 0) Fmt.km(s.totalKm / s.rides) else "--", km, c, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.padding(horizontal = Dimens.gutter).fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.lg)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Dimens.md))
            content()
        }
    }
}

@Composable
private fun DistanceChart(buckets: List<Bucket>, period: Period) {
    val values = buckets.map { it.km }
    val labels = buckets.mapIndexed { i, b ->
        when {
            b.monthly -> if (buckets.size <= 12 || i % 3 == 0) Fmt.monthShort(b.start) else ""
            period == Period.WEEK -> Fmt.weekday(b.start)
            else -> if ((buckets.size - 1 - i) % 7 == 0) Fmt.dayNumber(b.start) else ""
        }
    }
    val best = buckets.maxByOrNull { it.km }?.takeIf { it.km > 0 }
    BarChart(
        values = values,
        xLabels = labels,
        describe = { i ->
            val b = buckets[i]
            stringResource(R.string.readout_distance, if (b.monthly) Fmt.monthYear(b.start) else Fmt.day(b.start), Fmt.km(b.km))
        },
        idle = best?.let {
            stringResource(R.string.readout_best, if (it.monthly) Fmt.monthYear(it.start) else Fmt.day(it.start), Fmt.km(it.km))
        } ?: stringResource(R.string.readout_tap),
        axisFormat = Fmt::tick,
        description = stringResource(R.string.chart_distance_desc, Fmt.km(values.sum())),
    )
}

@Composable
private fun Records(s: PeriodStats, onOpen: (Long) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.padding(horizontal = Dimens.gutter).fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = Dimens.sm)) {
            Text(
                stringResource(R.string.records_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm),
            )
            s.longestRide?.let { r ->
                RecordRow(Icons.Rounded.Straighten, stringResource(R.string.record_longest), "${Fmt.km(r.summary.distanceKm)} ${stringResource(R.string.unit_km)}", Fmt.dateTime(r.summary.start)) { onOpen(r.id) }
            }
            s.fastestRide?.let { r ->
                RecordRow(Icons.Rounded.Speed, stringResource(R.string.record_fastest), "${r.summary.maxSpeedKmh} ${stringResource(R.string.dash_kmh)}", Fmt.dateTime(r.summary.start)) { onOpen(r.id) }
            }
            s.bestEconomyRide?.let { r ->
                RecordRow(Icons.Rounded.LocalGasStation, stringResource(R.string.record_mileage), "${Fmt.one(r.summary.avgEconomy ?: 0.0)} ${stringResource(R.string.unit_kmpl)}", Fmt.dateTime(r.summary.start)) { onOpen(r.id) }
            }
            RecordRow(
                Icons.Rounded.LocalFireDepartment,
                stringResource(R.string.record_streak),
                stringResource(R.string.record_streak_value, s.longestStreakDays),
                stringResource(R.string.record_active_days, s.activeDays),
                null,
            )
        }
    }
}

@Composable
private fun RecordRow(icon: ImageVector, title: String, value: String, detail: String, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Text(value, style = MaterialTheme.typography.titleMedium) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun RideRow(r: RideRecord, onClick: () -> Unit) {
    val s = r.summary
    ListItem(
        headlineContent = { Text(Fmt.dateTime(s.start)) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.ride_row,
                    Fmt.km(s.distanceKm),
                    Fmt.duration(s.movingS.toLong()),
                    Fmt.whole(s.avgMovingSpeedKmh),
                    s.maxSpeedKmh,
                ),
            )
        },
        leadingContent = { Icon(Icons.Rounded.TwoWheeler, contentDescription = null) },
        trailingContent = {
            if (r.demo) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        stringResource(R.string.analytics_sample),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs),
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
