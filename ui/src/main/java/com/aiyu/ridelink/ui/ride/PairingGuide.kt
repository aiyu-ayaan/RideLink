package com.aiyu.ridelink.ui.ride

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.theme.Dimens
import kotlinx.coroutines.delay

/** How long the demo finger holds MODE; the real display needs a few seconds (issue #15). */
private const val HOLD_DEMO_MS = 3_000L
private const val BLINKS = 3
private const val BLINK_MS = 350L
private const val LOOP_PAUSE_MS = 1_200L

/**
 * Pairing steps with a looping picture: a finger presses and holds the scooter's MODE button while a ring fills, then
 * the Bluetooth icon blinks on the display. Riders who never put the display in pairing mode saw it searching forever.
 */
@Composable
internal fun PairingGuide(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(Dimens.lg), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
            Text(
                stringResource(R.string.pair_guide_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            HoldModeAnimation(Modifier.align(Alignment.CenterHorizontally))
            listOf(R.string.pair_guide_step1, R.string.pair_guide_step2, R.string.pair_guide_step3).forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Text(
                        stringResource(step),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldModeAnimation(modifier: Modifier = Modifier) {
    val press = remember { Animatable(0f) }
    var hold by remember { mutableFloatStateOf(0f) }
    var bluetooth by remember { mutableFloatStateOf(0.25f) }
    val pressSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val description = stringResource(R.string.pair_guide_animation)

    LaunchedEffect(Unit) {
        while (true) {
            press.animateTo(1f, pressSpec)
            // The ring is a countdown of the hold, not a motion style, so it follows the clock.
            val start = withFrameMillis { it }
            do {
                val now = withFrameMillis { it }
                hold = ((now - start) / HOLD_DEMO_MS.toFloat()).coerceAtMost(1f)
            } while (hold < 1f)
            press.animateTo(0f, pressSpec)
            repeat(BLINKS) {
                bluetooth = 1f; delay(BLINK_MS)
                bluetooth = 0.25f; delay(BLINK_MS)
            }
            bluetooth = 1f
            delay(LOOP_PAUSE_MS)
            hold = 0f
            bluetooth = 0.25f
        }
    }

    Row(
        modifier = modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
    ) {
        // The MODE switch with the hold ring and a pressing finger.
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { hold },
                modifier = Modifier.size(96.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Box(
                Modifier.size(64.dp).scale(1f - 0.12f * press.value).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.pair_guide_mode), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            Icon(
                Icons.Rounded.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp).offset(x = 22.dp, y = (34 - 14 * press.value).dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        // The scooter display, with the Bluetooth icon blinking once pairing mode is on.
        Box(
            Modifier.size(width = 88.dp, height = 60.dp).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.inverseSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inversePrimary,
                modifier = Modifier.size(32.dp).alpha(bluetooth),
            )
        }
    }
}
