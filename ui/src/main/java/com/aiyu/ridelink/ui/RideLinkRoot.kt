package com.aiyu.ridelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.ble.LinkState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aiyu.ridelink.ui.analytics.AnalyticsScreen
import com.aiyu.ridelink.ui.console.ConsoleScreen
import com.aiyu.ridelink.ui.ride.RideScreen
import com.aiyu.ridelink.ui.settings.SettingsScreen

private enum class Tab(val label: Int, val selected: androidx.compose.ui.graphics.vector.ImageVector, val unselected: androidx.compose.ui.graphics.vector.ImageVector) {
    Ride(R.string.tab_ride, Icons.Rounded.TwoWheeler, Icons.Outlined.TwoWheeler),
    Analytics(R.string.tab_analytics, Icons.Rounded.Insights, Icons.Outlined.Insights),
    Settings(R.string.tab_forwarding, Icons.Rounded.Tune, Icons.Outlined.Tune),
    Console(R.string.tab_console, Icons.Rounded.Terminal, Icons.Outlined.Terminal),
}

@Composable
fun RideLinkRoot(onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val vm: MainViewModel = hiltViewModel()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    AutoConnect(vm, onConnect)
    val connect = { vm.userStopped = false; onConnect() }
    val disconnect = { vm.userStopped = true; onDisconnect() }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(if (tab == i) t.selected else t.unselected, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = Tab.entries[tab],
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            label = "tab",
        ) { current ->
            when (current) {
                Tab.Ride -> RideScreen(vm, connect, disconnect)
                Tab.Analytics -> AnalyticsScreen()
                Tab.Settings -> SettingsScreen(vm)
                Tab.Console -> ConsoleScreen(vm)
            }
        }
    }
}

/**
 * Connects as soon as a scooter is paired and Bluetooth access is granted: on app start and right
 * after pairing. No "Start ride" step; the foreground service keeps the session alive.
 */
@Composable
private fun AutoConnect(vm: MainViewModel, onConnect: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val resumed by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val address = settings.deviceAddress
    // Keyed on resume too, so granting Bluetooth in the permission dialog connects right away.
    LaunchedEffect(address, resumed.isAtLeast(Lifecycle.State.RESUMED)) {
        if (address != null && context.hasBluetooth() && vm.linkState.value is LinkState.Idle && !vm.userStopped) onConnect()
    }
}
