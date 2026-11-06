package com.aiyu.ridelink.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aiyu.ridelink.core.protocol.ClusterFamily
import com.aiyu.ridelink.core.protocol.ClusterProtocol
import com.aiyu.ridelink.core.protocol.DisplayTelemetry
import com.aiyu.ridelink.core.protocol.WeatherKind
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.theme.Dimens

private fun WeatherKind.icon(): ImageVector = when (this) {
    WeatherKind.CLEAR -> Icons.Rounded.WbSunny
    WeatherKind.CLEAR_NIGHT -> Icons.Rounded.Bedtime
    WeatherKind.CLOUDY -> Icons.Rounded.Cloud
    WeatherKind.FOG -> Icons.Rounded.Dehaze
    WeatherKind.RAIN -> Icons.Rounded.Umbrella
    WeatherKind.SNOW -> Icons.Rounded.AcUnit
    WeatherKind.THUNDERSTORM -> Icons.Rounded.FlashOn
    WeatherKind.HAIL -> Icons.Rounded.Grain
}

/** Small labelled value tile used on the dashboard cards. [content] is the card's on-color. */
@Composable
internal fun Tile(label: String, value: String, unit: String, content: Color, modifier: Modifier = Modifier) {
    Column(
        // Tonal tile on the card: works in light and dark without a raw color.
        modifier.clip(MaterialTheme.shapes.large).background(content.copy(alpha = 0.08f)).padding(Dimens.md),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLargeEmphasized, color = content, maxLines = 1)
            if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
        }
    }
}

/** What the display's weather screen shows: icon, temperature, AQI, rain chance and where you are. */
@Composable
internal fun WeatherCard(weather: ClusterProtocol.Weather?, place: String?) {
    val content = MaterialTheme.colorScheme.onSecondaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(Dimens.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (weather != null) {
                    Box(
                        Modifier.size(72.dp).clip(MaterialShapes.Sunny.toShape()).background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(weather.kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(40.dp))
                    }
                }
                Column(Modifier.padding(start = if (weather != null) Dimens.lg else 0.dp).weight(1f)) {
                    if (weather != null) {
                        Text(
                            stringResource(R.string.weather_temp, weather.temperatureC),
                            style = MaterialTheme.typography.displaySmallEmphasized,
                            color = content,
                        )
                        Text(weather.description, style = MaterialTheme.typography.titleMedium, color = content)
                    }
                    if (place != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Place, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                            Text(place, style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.padding(start = Dimens.xs))
                        }
                    }
                }
            }
            if (weather != null) {
                Spacer(Modifier.height(Dimens.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    Tile(stringResource(R.string.weather_aqi), weather.aqi.toString(), "", content, Modifier.weight(1f))
                    Tile(stringResource(R.string.weather_rain), weather.rainPercent.toString(), "%", content, Modifier.weight(1f))
                }
            }
        }
    }
}


/** Song the display shows, with the play state it was told. */
@Composable
internal fun MusicCard(music: ClusterProtocol.Music) {
    val state = when (music.state) {
        ClusterProtocol.PlayState.PLAYING -> Icons.Rounded.PlayArrow to R.string.music_playing
        ClusterProtocol.PlayState.PAUSED -> Icons.Rounded.Pause to R.string.music_paused
        ClusterProtocol.PlayState.STOPPED -> Icons.Rounded.Stop to R.string.music_stopped
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text(music.title, maxLines = 1) },
            supportingContent = { if (music.artist.isNotBlank()) Text(music.artist, maxLines = 1) },
            leadingContent = {
                Box(
                    Modifier.size(48.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            },
            trailingContent = { Icon(state.first, contentDescription = stringResource(state.second)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

internal val DisplayTelemetry.hasRideData
    get() = listOf(odometerTenths, tripTenths, fuelLevel, rangeKm, averageSpeedKmh, topSpeedKmh, averageEconomy).any { it != null }

/**
 * Odometer, trip, fuel, range, average/top speed and mileage as the display reports them. Displays that never
 * send `5A 19` (the 2026 Race XP LCD sends only 5A 10/11/16/18; stock reads mileage from 5A 19 [8] only for the
 * U566 / U445B themes) get the fuel-log mileage [logKmpl] instead, and no range tile.
 */
@Composable
internal fun RideDataCard(t: DisplayTelemetry, family: ClusterFamily, logKmpl: Double?) {
    val content = MaterialTheme.colorScheme.onSurface
    fun v(x: Int?) = x?.toString() ?: "--"
    fun km(x: Double?) = x?.let { "%.1f".format(it) } ?: "--"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.xl), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Text(stringResource(R.string.ride_data), style = MaterialTheme.typography.titleMediumEmphasized, color = content)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Tile(stringResource(R.string.ride_odometer), km(t.odometerKm), stringResource(R.string.unit_km), content, Modifier.weight(1f))
                Tile(stringResource(R.string.ride_trip), km(t.tripKm), stringResource(R.string.unit_km), content, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Tile(stringResource(R.string.ride_fuel), v(t.fuelBars(family)), stringResource(R.string.detail_fuel_unit), content, Modifier.weight(1f))
                if (t.rangeKm != null) Tile(stringResource(R.string.ride_range), v(t.rangeKm), stringResource(R.string.unit_km), content, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Tile(stringResource(R.string.ride_avg_speed), v(t.averageSpeedKmh), stringResource(R.string.dash_kmh), content, Modifier.weight(1f))
                Tile(stringResource(R.string.ride_top_speed), v(t.topSpeedKmh), stringResource(R.string.dash_kmh), content, Modifier.weight(1f))
                val display = t.averageEconomy?.takeIf { it > 0 }
                Tile(
                    stringResource(if (display == null && logKmpl != null) R.string.ride_mileage_log else R.string.ride_mileage),
                    display?.toString() ?: logKmpl?.let { "%.1f".format(it) } ?: "--",
                    stringResource(R.string.unit_kmpl),
                    content,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Engine and electrics as the display reports them. Raw numbers; the engine-light row turns to the error role
 * when the MIL code is non-zero.
 */
@Composable
internal fun VehicleCard(t: DisplayTelemetry) {
    val content = MaterialTheme.colorScheme.onSurface
    fun v(x: Int?) = x?.toString() ?: "--"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.xl), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Text(stringResource(R.string.vehicle_title), style = MaterialTheme.typography.titleMediumEmphasized, color = content)
            val mil = t.milCode
            if (mil != null && mil != 0) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.errorContainer).padding(Dimens.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        stringResource(R.string.vehicle_mil, mil),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(start = Dimens.sm),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Tile(stringResource(R.string.vehicle_rpm), v(t.rpm), "", content, Modifier.weight(1f))
                Tile(stringResource(R.string.vehicle_instant), v(t.instantEconomy), stringResource(R.string.unit_kmpl), content, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Tile(stringResource(R.string.vehicle_battery), v(t.batteryVoltage), "", content, Modifier.weight(1f))
                Tile(stringResource(R.string.vehicle_engine_temp), v(t.engineTemp), "", content, Modifier.weight(1f))
                Tile(stringResource(R.string.vehicle_service), v(t.serviceReminder), "", content, Modifier.weight(1f))
            }
        }
    }
}

/** SmartXonnect race stats from the display's `5A 16` frame (best lap, speeds, 0-60). */
@Composable
internal fun RaceStatsCard(t: DisplayTelemetry) {
    val content = MaterialTheme.colorScheme.onSurface
    val lap = t.raceBestLapCs?.let { "%d:%02d.%02d".format(it / 6000, it / 100 % 60, it % 100) } ?: "--"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.xl), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Text(stringResource(R.string.race_title), style = MaterialTheme.typography.titleMediumEmphasized, color = content)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Tile(stringResource(R.string.race_best_lap), lap, "", content, Modifier.weight(1f))
                Tile(stringResource(R.string.race_zero_60), t.raceZeroTo60Tenths?.let { "%.1f".format(it / 10.0) } ?: "--", stringResource(R.string.unit_s), content, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Tile(stringResource(R.string.race_top), t.raceTopSpeedKmh?.toString() ?: "--", stringResource(R.string.dash_kmh), content, Modifier.weight(1f))
                Tile(stringResource(R.string.race_avg), t.raceAverageKmh?.toString() ?: "--", stringResource(R.string.dash_kmh), content, Modifier.weight(1f))
            }
        }
    }
}

/** Where the phone was when the scooter last disconnected; opens the spot in a maps app. */
@Composable
internal fun ParkedCard(lat: Double, lng: Double, at: Long, precise: Boolean, onAskPrecise: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val content = MaterialTheme.colorScheme.onSurface
    val time = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.xl), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Text(stringResource(R.string.parked_title), style = MaterialTheme.typography.titleMediumEmphasized, color = content)
            // Without precise location the spot is the ~2 km coarse fix.
            Text(stringResource(if (precise) R.string.parked_when_precise else R.string.parked_when, time), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                androidx.compose.material3.FilledTonalButton(onClick = {
                    val uri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                }) { Text(stringResource(R.string.parked_open)) }
                if (!precise) androidx.compose.material3.TextButton(onClick = onAskPrecise) { Text(stringResource(R.string.parked_precise)) }
            }
        }
    }
}
