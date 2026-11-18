package com.aiyu.ridelink.ui.panel

import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.ui.MainViewModel
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.askBatteryExemption
import com.aiyu.ridelink.ui.hasBluetooth
import com.aiyu.ridelink.ui.theme.Dimens

/**
 * The quick settings tile's panel, laid out like Android's own Internet panel: live status, the connection switch,
 * the saved scooters and a way into the app. Floats over whatever the rider was doing.
 */
@Composable
fun ConnectionPanel(
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
    vm: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val link by vm.linkState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bikes by vm.bikes.collectAsStateWithLifecycle()
    val c = MaterialTheme.colorScheme
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val batteryOk = remember(resumes) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) != false
    }
    val paired = settings.deviceAddress != null
    val sessionOn = link !is LinkState.Idle
    val status = when (val l = link) {
        is LinkState.Ready -> stringResource(R.string.status_connected, l.name ?: settings.deviceName ?: l.address)
        is LinkState.Connecting -> stringResource(R.string.status_waiting)
        is LinkState.Failed -> l.reason
        LinkState.Idle -> stringResource(R.string.status_idle)
    }
    // Without Bluetooth access the service cannot run; the app asks for it.
    val setSession = { on: Boolean -> if (!context.hasBluetooth()) onOpenApp() else if (on) onConnect() else onDisconnect() }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim.copy(alpha = 0.32f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .navigationBarsPadding()
            .padding(Dimens.gutter),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = Dimens.contentMaxWidth).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = c.surfaceContainerHigh,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(Dimens.xl),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = c.onSurface)
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
                if (link is LinkState.Connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!paired) {
                    Text(stringResource(R.string.panel_no_scooter), style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
                } else {
                    Surface(shape = MaterialTheme.shapes.large, color = if (sessionOn) c.primaryContainer else c.surfaceContainerHighest) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = sessionOn, role = Role.Switch, onValueChange = setSession)
                                .heightIn(min = 72.dp)
                                .padding(horizontal = Dimens.lg, vertical = Dimens.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
                        ) {
                            val on = if (sessionOn) c.onPrimaryContainer else c.onSurface
                            Icon(Icons.Rounded.TwoWheeler, contentDescription = null, tint = on)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.panel_connection), style = MaterialTheme.typography.titleMedium, color = on)
                                settings.deviceName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = on) }
                            }
                            Switch(checked = sessionOn, onCheckedChange = null)
                        }
                    }
                    if (bikes.size > 1) {
                        Text(stringResource(R.string.panel_saved), style = MaterialTheme.typography.titleSmall, color = c.primary)
                        bikes.forEach { bike ->
                            val active = bike.address == settings.deviceAddress
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = active, role = Role.RadioButton) {
                                        if (!active) vm.select(bike).invokeOnCompletion { onConnect() }
                                    }
                                    .heightIn(min = Dimens.minTouch),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                            ) {
                                RadioButton(selected = active, onClick = null)
                                Text(bike.name ?: bike.address, style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
                            }
                        }
                    }
                    if (!batteryOk) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                        ) {
                            Icon(Icons.Rounded.BatteryAlert, contentDescription = null, tint = c.onSurfaceVariant)
                            Text(
                                stringResource(R.string.panel_battery),
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { context.askBatteryExemption() }) { Text(stringResource(R.string.perm_grant)) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm, Alignment.End)) {
                    TextButton(onClick = onOpenApp) { Text(stringResource(R.string.panel_open)) }
                    FilledTonalButton(onClick = onDismiss) { Text(stringResource(R.string.panel_done)) }
                }
            }
        }
    }
}
