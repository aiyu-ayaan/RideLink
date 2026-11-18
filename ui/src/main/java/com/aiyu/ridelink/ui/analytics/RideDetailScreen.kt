package com.aiyu.ridelink.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Map
import com.aiyu.ridelink.ui.nav.appBarColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.analytics.DistanceSource
import com.aiyu.ridelink.core.analytics.RideMath
import com.aiyu.ridelink.core.analytics.RideRecord
import com.aiyu.ridelink.core.analytics.RideSample
import com.aiyu.ridelink.core.analytics.RideTurn
import com.aiyu.ridelink.core.protocol.Maneuver
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.ride.Tile
import com.aiyu.ridelink.ui.ride.icon
import com.aiyu.ridelink.ui.theme.Dimens
import com.aiyu.ridelink.ui.update.BackButton

/**
 * One ride, everything recorded about it: distance and where it came from (Google Maps route, odometer, speed),
 * time, speeds, engine, fuel, the Maps route with its turn timeline, and every measure over time
 * (one chart per measure, never a second y-axis).
 */
@Composable
fun RideDetailScreen(rideId: Long, onBack: () -> Unit, vm: AnalyticsViewModel = hiltViewModel()) {
    val rides by vm.allRides.collectAsStateWithLifecycle()
    val ride = rides.firstOrNull { it.id == rideId }
    val samples by produceState<List<RideSample>?>(null, rideId) { value = vm.samples(rideId) }
    val turns by produceState<List<RideTurn>>(emptyList(), rideId) { value = vm.turns(rideId) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                colors = appBarColors(),
                title = { Text(ride?.let { rideTitle(it) } ?: stringResource(R.string.detail_title), maxLines = 2) },
                subtitle = {
                    ride?.let { r ->
                        // The date moves here when the title is a name or the places.
                        val date = Fmt.dateTime(r.summary.start)
                        Text(
                            (if (rideTitle(r) != date) Fmt.day(java.time.Instant.ofEpochMilli(r.summary.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()) + " · " else "") +
                                stringResource(R.string.detail_span, Fmt.time(r.summary.start), Fmt.time(r.summary.end)) +
                                if (r.demo) " · " + stringResource(R.string.analytics_sample) else "",
                        )
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (ride != null) {
                        StarToggle(ride.starred) { vm.setStarred(ride.id, it) }
                        IconButton(onClick = { renaming = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.ride_rename))
                        }
                    }
                    if (ride != null) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.detail_delete))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        if (ride == null) {
            // Deleted, or the list is still loading.
            LoadingIndicator(Modifier.padding(padding).padding(Dimens.xl).size(48.dp))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter).padding(bottom = Dimens.xxl),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            DistanceHero(ride)
            Facts(ride, samples)
            val list = samples
            if (ride.summary.navigatedS > 0 || turns.isNotEmpty()) RouteSection(ride, list, turns)
            when {
                list == null -> LoadingIndicator(Modifier.size(48.dp).align(Alignment.CenterHorizontally))
                list.size < 2 -> Text(stringResource(R.string.detail_no_samples), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Charts(ride, list)
            }
        }
    }

    if (renaming && ride != null) {
        RenameDialog(ride.name, onSave = { vm.rename(ride.id, it); renaming = false }, onDismiss = { renaming = false })
    }

    if (confirmDelete && ride != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(ride.id); onBack() }) {
                    Text(stringResource(R.string.detail_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The one number that matters, and honestly where it came from. */
@Composable
private fun DistanceHero(ride: RideRecord) {
    val s = ride.summary
    val c = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = c.primaryContainer)) {
        Column(Modifier.padding(Dimens.xl), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            Text(stringResource(R.string.detail_distance), style = MaterialTheme.typography.labelLarge, color = c.onPrimaryContainer)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.km(s.distanceKm), style = MaterialTheme.typography.displayLargeEmphasized, color = c.onPrimaryContainer)
                Spacer(Modifier.width(Dimens.sm))
                Text(stringResource(R.string.unit_km), style = MaterialTheme.typography.titleLarge, color = c.onPrimaryContainer, modifier = Modifier.padding(bottom = Dimens.sm))
            }
            Text(
                stringResource(
                    when (s.source) {
                        DistanceSource.MAPS -> R.string.detail_source_maps
                        DistanceSource.ODOMETER -> R.string.detail_source_odometer
                        DistanceSource.SPEED -> R.string.detail_source_speed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onPrimaryContainer,
            )
            val parts = listOfNotNull(
                s.mapsM.takeIf { it > 0 }?.let { stringResource(R.string.detail_part_maps, Fmt.km(it / 1000.0)) },
                s.odometerM.takeIf { it > 0 }?.let { stringResource(R.string.detail_part_odometer, Fmt.km(it / 1000.0)) },
                s.speedM.takeIf { it > 0 }?.let { stringResource(R.string.detail_part_speed, Fmt.km(it / 1000.0)) },
            )
            if (parts.size > 1) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.onPrimaryContainer)
        }
    }
}

/** Every summary number, two to a row. */
@Composable
private fun Facts(ride: RideRecord, samples: List<RideSample>?) {
    val s = ride.summary
    val c = MaterialTheme.colorScheme.onSurface
    val kmh = stringResource(R.string.dash_kmh)
    val km = stringResource(R.string.unit_km)
    val rpm = samples?.mapNotNull { it.rpm?.takeIf { r -> r > 0 } }.orEmpty()
    val range = samples?.mapNotNull { it.rangeKm }.orEmpty()
    val fuelUsed = samples?.let(RideMath::fuelDrop) ?: s.fuelUsed
    // The display's trip meter when it counted this ride, else the odometer, which measures the same distance.
    val tripTenths = samples?.let(RideMath::tripCoveredTenths)
        ?: s.odometerStartTenths?.let { from -> s.odometerEndTenths?.minus(from) }?.takeIf { it > 0 }
    val tiles = buildList {
        add(Triple(stringResource(R.string.detail_duration), Fmt.duration(s.durationS.toLong()), ""))
        add(Triple(stringResource(R.string.detail_moving), Fmt.duration(s.movingS.toLong()), ""))
        add(Triple(stringResource(R.string.stat_avg_speed), Fmt.whole(s.avgMovingSpeedKmh), kmh))
        add(Triple(stringResource(R.string.stat_top_speed), s.maxSpeedKmh.toString(), kmh))
        add(Triple(stringResource(R.string.detail_stopped), Fmt.duration((s.durationS - s.movingS).toLong().coerceAtLeast(0)), ""))
        s.avgEconomy?.let { add(Triple(stringResource(R.string.stat_mileage), Fmt.one(it), stringResource(R.string.unit_kmpl))) }
        fuelUsed?.let { add(Triple(stringResource(R.string.detail_fuel_used), it.toString(), stringResource(R.string.detail_fuel_unit))) }
        if (rpm.isNotEmpty()) {
            add(Triple(stringResource(R.string.detail_avg_rpm), Fmt.whole(rpm.average()), ""))
            add(Triple(stringResource(R.string.detail_max_rpm), Fmt.whole(rpm.max().toDouble()), ""))
        }
        if (range.size >= 2) add(Triple(stringResource(R.string.detail_range), "${range.first()} → ${range.last()}", km))
        tripTenths?.let { add(Triple(stringResource(R.string.detail_trip), Fmt.one(it / 10.0), km)) }
    }
    tiles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            row.forEach { (label, value, unit) -> Tile(label, value, unit, c, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    // Start → end of a five-digit odometer does not fit a half-width tile (the end value was cut off): full row.
    val odoStart = s.odometerStartTenths
    val odoEnd = s.odometerEndTenths
    if (odoStart != null && odoEnd != null) {
        Tile(stringResource(R.string.detail_odometer_tile), "${Fmt.one(odoStart / 10.0)} → ${Fmt.one(odoEnd / 10.0)}", km, c, Modifier.fillMaxWidth())
    }
}

/** Google Maps route: how much of the ride followed it, distance to destination over time, and each instruction. */
@Composable
private fun RouteSection(ride: RideRecord, samples: List<RideSample>?, turns: List<RideTurn>) {
    val s = ride.summary
    val c = MaterialTheme.colorScheme
    SectionTitle(R.string.detail_route)
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow)) {
        Column(Modifier.padding(Dimens.lg), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
                Icon(Icons.Rounded.Map, contentDescription = null, tint = c.primary)
                Text(
                    stringResource(R.string.detail_route_summary, Fmt.km(s.mapsM / 1000.0), Fmt.duration(s.navigatedS.toLong()), turns.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val left = samples?.mapNotNull { x -> x.navLeftM?.let { x.t to it / 1000.0 } }.orEmpty()
            if (left.size >= 2) ChartSection(R.string.chart_ride_route_left, stringResource(R.string.unit_km), RideMath.downsample(left, 160), c.surfaceContainerLow)
            if (turns.isNotEmpty()) {
                Text(stringResource(R.string.detail_turns), style = MaterialTheme.typography.titleSmall, color = c.primary)
                turns.forEach { TurnRow(it) }
            }
        }
    }
}

@Composable
private fun TurnRow(t: RideTurn) {
    val c = MaterialTheme.colorScheme
    val maneuver = runCatching { Maneuver.valueOf(t.maneuver) }.getOrDefault(Maneuver.STRAIGHT)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
        Icon(maneuver.icon(), contentDescription = null, tint = c.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(t.instruction, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (t.distanceLeftM > 0) stringResource(R.string.detail_turn_meta, Fmt.time(t.t), Fmt.km(t.distanceLeftM / 1000.0))
                else Fmt.time(t.t),
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
            )
        }
    }
}

/** Touching any chart moves the crosshair on all of them, so one moment of the ride can be read across every measure. */
@Composable
private fun Charts(ride: RideRecord, samples: List<RideSample>) {
    val bg = MaterialTheme.colorScheme.surface
    val kmh = stringResource(R.string.dash_kmh)
    val km = stringResource(R.string.unit_km)
    val kmpl = stringResource(R.string.unit_kmpl)
    var at by remember(samples) { mutableStateOf<Long?>(null) }
    val pickAt = { t: Long -> at = t }
    fun series(f: (RideSample) -> Int?) = RideMath.downsample(samples.mapNotNull { s -> f(s)?.let { s.t to it.toDouble() } }, 160)
    val distance = remember(samples) { RideMath.downsample(RideMath.distanceSeries(samples), 160) }
    val speed = remember(samples) { series { it.speedKmh } }
    val rpm = remember(samples) { series { it.rpm } }
    val economy = remember(samples) { series { it.instantEconomy } }
    val range = remember(samples) { series { it.rangeKm } }
    val fuel = remember(samples) { series { it.fuel } }
    val spans = remember(samples) { RideMath.motionSpans(samples) }
    val rpmMinutes = remember(samples) { RideMath.rpmBandMinutes(samples) }
    val economyBands = remember(samples) { RideMath.economyBySpeedBand(samples) }
    val speedLabels = remember { bandLabels(RideMath.SPEED_BANDS) }

    SectionTitle(R.string.detail_charts)
    if (spans.isNotEmpty()) {
        val name = stringResource(R.string.chart_motion)
        Column {
            Text(name, style = MaterialTheme.typography.titleMediumEmphasized)
            Spacer(Modifier.height(Dimens.xs))
            MotionTimeline(
                spans = spans,
                selectedTime = at,
                onSelectTime = pickAt,
                describe = { s ->
                    stringResource(
                        if (s.moving) R.string.readout_motion_moving else R.string.readout_motion_stopped,
                        Fmt.time(s.from), Fmt.time(s.to), Fmt.duration((s.to - s.from) / 1000),
                    )
                },
                idle = stringResource(R.string.readout_motion_idle),
                description = name,
                startLabel = Fmt.time(spans.first().from),
                endLabel = Fmt.time(spans.last().to),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    ChartSection(R.string.chart_ride_distance, km, distance, bg, at, pickAt)
    ChartSection(R.string.chart_ride_speed, kmh, speed, bg, at, pickAt)
    ChartSection(R.string.chart_ride_rpm, "", rpm, bg, at, pickAt)
    ChartSection(R.string.chart_ride_economy, kmpl, economy, bg, at, pickAt)
    ChartSection(R.string.chart_ride_range, km, range, bg, at, pickAt)
    ChartSection(R.string.chart_ride_fuel, stringResource(R.string.detail_fuel_unit), fuel, bg, at, pickAt)

    val bands = ride.summary.speedBandS
    if (bands.any { it > 0 }) {
        val minutes = bands.map { it / 60.0 }
        BandChart(
            title = stringResource(R.string.chart_ride_bands),
            values = minutes,
            labels = speedLabels,
            describe = { i -> stringResource(R.string.readout_band, speedLabels[i], Fmt.minutes(minutes[i])) },
            idle = stringResource(R.string.readout_band_idle, kmh),
            valueLabel = { i -> Fmt.one(minutes[i]) },
            unit = stringResource(R.string.unit_min),
        )
    }
    if (rpmMinutes.any { it > 0 }) {
        val labels = remember { bandLabels(RideMath.RPM_BANDS.map { it / 1000 }) }
        BandChart(
            title = stringResource(R.string.chart_rpm_bands),
            values = rpmMinutes,
            labels = labels,
            describe = { i -> stringResource(R.string.readout_rpm_band, labels[i], Fmt.minutes(rpmMinutes[i])) },
            idle = stringResource(R.string.readout_tap),
            valueLabel = { i -> Fmt.one(rpmMinutes[i]) },
            unit = stringResource(R.string.unit_min),
        )
    }
    if (economyBands.any { it > 0 }) {
        BandChart(
            title = stringResource(R.string.chart_economy_speed),
            values = economyBands,
            labels = speedLabels,
            describe = { i -> stringResource(R.string.readout_economy_speed, speedLabels[i], Fmt.one(economyBands[i]), kmpl) },
            idle = stringResource(R.string.readout_tap),
            valueLabel = { i -> Fmt.one(economyBands[i]) },
            unit = kmpl,
        )
    }
}

/** "0-19", "20-39" ... "80+" from the lower bounds of a band table. */
private fun bandLabels(bounds: List<Int>) = bounds.mapIndexed { i, lo -> bounds.getOrNull(i + 1)?.let { "$lo-${it - 1}" } ?: "$lo+" }

/** One value per band of a ride, under its own title. */
@Composable
private fun BandChart(
    title: String,
    values: List<Double>,
    labels: List<String>,
    describe: @Composable (Int) -> String,
    idle: String,
    valueLabel: (Int) -> String,
    unit: String,
) {
    Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
    BarChart(
        values = values,
        xLabels = labels,
        describe = describe,
        idle = idle,
        axisFormat = Fmt::tick,
        description = stringResource(R.string.chart_series_desc, title, Fmt.whole(values.max()), unit),
        modifier = Modifier.fillMaxWidth(),
        height = 160.dp,
        valueLabel = valueLabel,
    )
}

/** One measure over the ride; skipped when the display never sent it. */
@Composable
private fun ChartSection(
    title: Int,
    unit: String,
    points: List<Pair<Long, Double>>,
    ring: Color,
    selectedTime: Long? = null,
    onSelectTime: ((Long) -> Unit)? = null,
) {
    if (points.size < 2) return
    val name = stringResource(title)
    val max = points.maxOf { it.second }
    val fmt = remember(max) { { v: Double -> if (max < 20) Fmt.one(v) else Fmt.whole(v) } }
    Column {
        Text(name, style = MaterialTheme.typography.titleMediumEmphasized)
        Spacer(Modifier.height(Dimens.xs))
        LineChart(
            points = points,
            xLabel = Fmt::time,
            describe = { t, v -> stringResource(R.string.readout_series, Fmt.time(t), fmt(v), unit).trim() },
            idle = stringResource(R.string.readout_series_idle, fmt(max), unit).trim(),
            axisFormat = Fmt::tick,
            description = stringResource(R.string.chart_series_desc, name, fmt(max), unit),
            ring = ring,
            modifier = Modifier.fillMaxWidth(),
            height = 160.dp,
            selectedTime = selectedTime,
            onSelectTime = onSelectTime,
            valueLabel = { v -> "${fmt(v)} $unit".trim() },
        )
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Dimens.md),
    )
}
