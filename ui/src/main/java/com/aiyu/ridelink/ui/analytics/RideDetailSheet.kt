package com.aiyu.ridelink.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiyu.ridelink.core.analytics.RideMath
import com.aiyu.ridelink.core.analytics.RideRecord
import com.aiyu.ridelink.core.analytics.RideSample
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.ride.Tile
import com.aiyu.ridelink.ui.theme.Dimens

/** One ride: summary tiles and its speed, RPM and range over time (separate charts, never a second y-axis). */
@Composable
internal fun RideDetailSheet(ride: RideRecord, vm: AnalyticsViewModel, onDismiss: () -> Unit) {
    val samples by produceState<List<RideSample>?>(null, ride.id) { value = vm.samples(ride.id) }
    val s = ride.summary
    val c = MaterialTheme.colorScheme.onSurface
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
    val kmh = stringResource(R.string.dash_kmh)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetColor,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            Text(Fmt.dateTime(s.start), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.detail_span, Fmt.time(s.start), Fmt.time(s.end)) +
                    if (ride.demo) " · " + stringResource(R.string.analytics_sample) else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Tile(stringResource(R.string.detail_distance), Fmt.km(s.distanceKm), stringResource(R.string.unit_km), c, Modifier.weight(1f))
                Tile(stringResource(R.string.detail_moving), Fmt.duration(s.movingS.toLong()), "", c, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Tile(stringResource(R.string.stat_avg_speed), Fmt.whole(s.avgMovingSpeedKmh), kmh, c, Modifier.weight(1f))
                Tile(stringResource(R.string.stat_top_speed), s.maxSpeedKmh.toString(), kmh, c, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Tile(stringResource(R.string.stat_mileage), s.avgEconomy?.let(Fmt::one) ?: "--", stringResource(R.string.unit_kmpl), c, Modifier.weight(1f))
                Tile(stringResource(R.string.detail_stopped), Fmt.duration((s.durationS - s.movingS).toLong().coerceAtLeast(0)), "", c, Modifier.weight(1f))
            }
            // Copied to locals: properties from the core module can't be smart-cast after a null check.
            val odoStart = s.odometerStartTenths
            val odoEnd = s.odometerEndTenths
            if (odoStart != null && odoEnd != null) {
                Text(
                    stringResource(R.string.detail_odometer, Fmt.one(odoStart / 10.0), Fmt.one(odoEnd / 10.0)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val list = samples
            if (list == null) {
                LoadingIndicator(Modifier.size(48.dp).align(Alignment.CenterHorizontally))
            } else if (list.size < 2) {
                Text(stringResource(R.string.detail_no_samples), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Series(list, R.string.chart_ride_speed, kmh, sheetColor) { it.speedKmh }
                Series(list, R.string.chart_ride_rpm, "", sheetColor) { it.rpm }
                Series(list, R.string.chart_ride_range, stringResource(R.string.unit_km), sheetColor) { it.rangeKm }
            }

            TextButton(onClick = { vm.delete(ride.id); onDismiss() }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.detail_delete), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}

/** One measure over the ride; skipped when the display never sent it. */
@Composable
private fun Series(samples: List<RideSample>, title: Int, unit: String, ring: androidx.compose.ui.graphics.Color, pick: (RideSample) -> Int?) {
    val raw = samples.mapNotNull { s -> pick(s)?.let { s.t to it.toDouble() } }
    if (raw.size < 2) return
    val points = RideMath.downsample(raw, 160)
    val name = stringResource(title)
    Column {
        Text(name, style = MaterialTheme.typography.titleMediumEmphasized)
        Spacer(Modifier.height(Dimens.xs))
        LineChart(
            points = points,
            xLabel = Fmt::time,
            describe = { t, v -> stringResource(R.string.readout_series, Fmt.time(t), Fmt.whole(v), unit).trim() },
            idle = stringResource(R.string.readout_series_idle, Fmt.whole(points.maxOf { it.second }), unit).trim(),
            axisFormat = Fmt::tick,
            description = stringResource(R.string.chart_series_desc, name, Fmt.whole(points.maxOf { it.second }), unit),
            ring = ring,
            modifier = Modifier.fillMaxWidth(),
            height = 160.dp,
        )
    }
}
