package com.aiyu.ridelink.ui.ride

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.ForkRight
import androidx.compose.material.icons.rounded.TurnSlightRight
import androidx.compose.material.icons.rounded.TurnSharpRight
import androidx.compose.material.icons.rounded.ForkLeft
import androidx.compose.material.icons.rounded.TurnSlightLeft
import androidx.compose.material.icons.rounded.TurnSharpLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RoundaboutRight
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Straight
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.core.protocol.ClusterProtocol.NavStep
import com.aiyu.ridelink.core.protocol.Maneuver
import com.aiyu.ridelink.ui.MainViewModel
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.bluetoothPermissions
import com.aiyu.ridelink.ui.hasBluetooth
import com.aiyu.ridelink.ui.hasListener
import com.aiyu.ridelink.ui.hasPhoneState
import com.aiyu.ridelink.ui.phonePermissions
import com.aiyu.ridelink.ui.theme.Dimens

@Composable
fun RideScreen(vm: MainViewModel, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val link by vm.linkState.collectAsStateWithLifecycle()
    val nav by vm.nav.collectAsStateWithLifecycle()
    val caller by vm.caller.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val demo by vm.demo.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val place by vm.place.collectAsStateWithLifecycle()
    val music by vm.music.collectAsStateWithLifecycle()
    val telemetry by vm.telemetry.collectAsStateWithLifecycle()

    // Re-check grants each time we come back from system settings.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val bluetoothOk = remember(refresh) { context.hasBluetooth() }
    val listenerOk = remember(refresh) { context.hasListener() }
    val phoneOk = remember(refresh) { context.hasPhoneState() }
    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    var pairing by remember { mutableStateOf(false) }
    val paired = settings.deviceAddress != null
    val rideOn = link !is LinkState.Idle

    Scaffold(topBar = { MediumFlexibleTopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter)
                .widthIn(max = Dimens.contentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            if (!bluetoothOk) {
                PermissionCard(Icons.Rounded.Bluetooth, R.string.perm_bluetooth_title, R.string.perm_bluetooth_body, R.string.perm_grant) {
                    askBluetooth.launch(bluetoothPermissions)
                }
            }
            if (!listenerOk) {
                PermissionCard(Icons.Rounded.NotificationsActive, R.string.perm_listener_title, R.string.perm_listener_body, R.string.perm_open_settings) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }

            if (bluetoothOk && !phoneOk) {
                PermissionCard(Icons.Rounded.SignalCellularAlt, R.string.perm_phone_title, R.string.perm_phone_body, R.string.perm_grant, optional = true) {
                    askBluetooth.launch(phonePermissions)
                }
            }

            HeroCard(link, paired, settings.deviceName, demo)
            AnimatedVisibility(nav != null || speed != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                RideDashboard(nav, speed)
            }
            AnimatedVisibility(settings.showRideData && telemetry.hasRideData, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                RideDataCard(telemetry)
            }
            val showWeather = (weather != null && settings.showWeather) || (place != null && settings.showLocation)
            AnimatedVisibility(showWeather, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                WeatherCard(weather?.takeIf { settings.showWeather }, place?.takeIf { settings.showLocation })
            }
            AnimatedVisibility(music != null && settings.showMusic, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                music?.let { MusicCard(it) }
            }
            AnimatedVisibility(caller != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                InfoCard(Icons.Rounded.Call, stringResource(R.string.call_now), caller.orEmpty())
            }

            if (!paired) {
                Button(onClick = { pairing = true }, enabled = bluetoothOk, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.pair_scooter))
                }
            } else if (rideOn) {
                // The session starts on its own once paired; this only stops it until the next launch.
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.disconnect))
                }
            } else {
                Button(onClick = onConnect, enabled = bluetoothOk, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.connect))
                }
            }

            Spacer(Modifier.height(Dimens.xl))
        }
    }

    if (pairing) PairSheet(vm) { pairing = false }
}

@Composable
private fun HeroCard(link: LinkState, paired: Boolean, deviceName: String?, demo: Boolean) {
    val connected = link is LinkState.Ready || demo
    val title = when {
        demo -> R.string.hero_demo
        !paired -> R.string.hero_no_scooter
        link is LinkState.Ready -> R.string.hero_ready
        link is LinkState.Connecting -> R.string.hero_connecting
        link is LinkState.Failed -> R.string.hero_failed
        else -> R.string.hero_idle
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(Dimens.xl), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (link is LinkState.Connecting && !demo) {
                    LoadingIndicator(Modifier.size(56.dp))
                } else {
                    Icon(
                        Icons.Rounded.TwoWheeler,
                        contentDescription = null,
                        tint = if (connected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Column(Modifier.padding(start = Dimens.lg)) {
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmallEmphasized)
                val sub = (link as? LinkState.Failed)?.reason ?: deviceName
                if (sub != null) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun Maneuver.icon(): ImageVector = when (this) {
    Maneuver.LEFT -> Icons.Rounded.TurnLeft
    Maneuver.SHARP_LEFT -> Icons.Rounded.TurnSharpLeft
    Maneuver.SLIGHT_LEFT -> Icons.Rounded.TurnSlightLeft
    Maneuver.FORK_LEFT -> Icons.Rounded.ForkLeft
    Maneuver.RIGHT -> Icons.Rounded.TurnRight
    Maneuver.SHARP_RIGHT -> Icons.Rounded.TurnSharpRight
    Maneuver.SLIGHT_RIGHT -> Icons.Rounded.TurnSlightRight
    Maneuver.FORK_RIGHT -> Icons.Rounded.ForkRight
    Maneuver.U_TURN -> Icons.Rounded.UTurnLeft
    Maneuver.ROUNDABOUT -> Icons.Rounded.RoundaboutRight
    Maneuver.DESTINATION -> Icons.Rounded.Flag
    Maneuver.STRAIGHT -> Icons.Rounded.Straight
}

private fun meters(m: Int) = if (m >= 1000) "%.1f km".format(m / 1000f) else "$m m"

/** What the scooter display shows: big turn arrow, distance to the turn, ETA and speed. */
@Composable
private fun RideDashboard(step: NavStep?, speed: Int?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(Dimens.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step != null) {
                    val arrowSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                    AnimatedContent(step.maneuver, transitionSpec = { (scaleIn(arrowSpec) + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "arrow") { m ->
                        Box(
                            Modifier.size(112.dp).clip(MaterialShapes.Cookie7Sided.toShape()).background(MaterialTheme.colorScheme.onTertiaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(m.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(72.dp))
                        }
                    }
                    Column(Modifier.padding(start = Dimens.lg).weight(1f)) {
                        Text(meters(step.distanceToTurnM), style = MaterialTheme.typography.displayMediumEmphasized, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(step.instruction, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                } else {
                    Text(stringResource(R.string.dash_no_route), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Dimens.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Stat(stringResource(R.string.dash_speed), speed?.toString() ?: "--", stringResource(R.string.dash_kmh), Modifier.weight(1f))
                Stat(stringResource(R.string.dash_eta), step?.let { (it.etaSeconds / 60).toString() } ?: "--", stringResource(R.string.dash_min), Modifier.weight(1f))
                Stat(stringResource(R.string.dash_left), step?.let { meters(it.distanceLeftM) } ?: "--", "", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier) {
    Column(
        // Tonal tile on the tertiary card: works in light and dark without a raw color.
        modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.08f)).padding(Dimens.md),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onTertiaryContainer)
            if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String, emphasized: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        ListItem(
            headlineContent = { Text(title, style = if (emphasized) MaterialTheme.typography.headlineMediumEmphasized else MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(body) },
            leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(if (emphasized) 40.dp else 24.dp)) },
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: Int, body: Int, action: Int, optional: Boolean = false, onClick: () -> Unit) {
    // Required grants use the error role; nice-to-have ones use the calmer secondary role.
    val container = if (optional) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (optional) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = content)
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                    modifier = Modifier.padding(start = Dimens.md),
                )
            }
            Text(stringResource(body), color = content, modifier = Modifier.padding(vertical = Dimens.sm))
            FilledTonalButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text(stringResource(action)) }
        }
    }
}

@Composable
private fun PairSheet(vm: MainViewModel, onDone: () -> Unit) {
    val found by vm.found.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.startScan() }
    DisposableEffect(Unit) { onDispose { vm.stopScan() } }
    // Stop after 15 s to save battery; user can scan again.
    LaunchedEffect(scanning) { if (scanning) { kotlinx.coroutines.delay(15_000); vm.stopScan() } }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Dimens.gutter)) {
            Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.pair_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Dimens.sm))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
                if (scanning) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Text(stringResource(R.string.pair_scanning), modifier = Modifier.padding(start = Dimens.sm))
                } else {
                    TextButton(onClick = vm::startScan) { Text(stringResource(R.string.pair_rescan)) }
                }
            }
        }
        LazyColumn(Modifier.padding(bottom = Dimens.xl)) {
            if (found.isEmpty() && !scanning) {
                item { Text(stringResource(R.string.pair_none), modifier = Modifier.padding(Dimens.gutter)) }
            }
            items(found, key = { it.address }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text("${c.address} · ${c.rssi} dBm") },
                    leadingContent = { Icon(Icons.Rounded.TwoWheeler, contentDescription = null) },
                    modifier = Modifier.clickable {
                        vm.pair(c)
                        onDone()
                    },
                )
            }
        }
    }
}
