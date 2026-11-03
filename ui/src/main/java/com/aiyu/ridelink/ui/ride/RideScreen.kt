package com.aiyu.ridelink.ui.ride

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RoundaboutRight
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.core.protocol.ClusterProtocol.NavStep
import com.aiyu.ridelink.core.protocol.Maneuver
import com.aiyu.ridelink.ui.MainViewModel
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.theme.Dimens

private val bluetoothPermissions = buildList {
    if (Build.VERSION.SDK_INT >= 31) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun Context.hasBluetooth() = bluetoothPermissions.all {
    // Notification permission is nice-to-have; Bluetooth ones gate everything.
    it == Manifest.permission.POST_NOTIFICATIONS || ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

private fun Context.hasListener() = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)

@Composable
fun RideScreen(vm: MainViewModel, onStartRide: () -> Unit, onEndRide: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val link by vm.linkState.collectAsStateWithLifecycle()
    val nav by vm.nav.collectAsStateWithLifecycle()
    val caller by vm.caller.collectAsStateWithLifecycle()

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

            HeroCard(link, paired, settings.deviceName)

            if (!paired) {
                Button(onClick = { pairing = true }, enabled = bluetoothOk, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.pair_scooter))
                }
            } else if (rideOn) {
                OutlinedButton(onClick = onEndRide, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.end_ride))
                }
            } else {
                Button(onClick = onStartRide, enabled = bluetoothOk, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.start_ride))
                }
            }

            AnimatedVisibility(caller != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                InfoCard(Icons.Rounded.Call, stringResource(R.string.call_now), caller.orEmpty())
            }
            AnimatedVisibility(nav != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                nav?.let { NavCard(it) }
            }
            Spacer(Modifier.height(Dimens.xl))
        }
    }

    if (pairing) PairSheet(vm) { pairing = false }
}

@Composable
private fun HeroCard(link: LinkState, paired: Boolean, deviceName: String?) {
    val connected = link is LinkState.Ready
    val title = when {
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
                if (link is LinkState.Connecting) {
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

@Composable
private fun NavCard(step: NavStep) {
    val icon = when (step.maneuver) {
        Maneuver.LEFT, Maneuver.SHARP_LEFT, Maneuver.SLIGHT_LEFT, Maneuver.FORK_LEFT -> Icons.Rounded.TurnLeft
        Maneuver.RIGHT, Maneuver.SHARP_RIGHT, Maneuver.SLIGHT_RIGHT, Maneuver.FORK_RIGHT -> Icons.Rounded.TurnRight
        Maneuver.U_TURN -> Icons.Rounded.UTurnLeft
        Maneuver.ROUNDABOUT -> Icons.Rounded.RoundaboutRight
        Maneuver.DESTINATION -> Icons.Rounded.Flag
        Maneuver.STRAIGHT -> Icons.Rounded.Straight
    }
    val distance = if (step.distanceToTurnM >= 1000) "%.1f km".format(step.distanceToTurnM / 1000f) else "${step.distanceToTurnM} m"
    InfoCard(icon, distance, step.instruction, emphasized = true)
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
private fun PermissionCard(icon: ImageVector, title: Int, body: Int, action: Int, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = Dimens.md),
                )
            }
            Text(stringResource(body), color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(vertical = Dimens.sm))
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
