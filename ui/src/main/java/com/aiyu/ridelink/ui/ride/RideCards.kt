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
import com.aiyu.ridelink.core.protocol.ClusterProtocol
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
