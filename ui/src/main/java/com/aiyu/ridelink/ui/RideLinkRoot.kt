package com.aiyu.ridelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aiyu.ridelink.ui.console.ConsoleScreen
import com.aiyu.ridelink.ui.ride.RideScreen
import com.aiyu.ridelink.ui.settings.SettingsScreen

private enum class Tab(val label: Int, val selected: androidx.compose.ui.graphics.vector.ImageVector, val unselected: androidx.compose.ui.graphics.vector.ImageVector) {
    Ride(R.string.tab_ride, Icons.Rounded.TwoWheeler, Icons.Outlined.TwoWheeler),
    Settings(R.string.tab_forwarding, Icons.Rounded.Tune, Icons.Outlined.Tune),
    Console(R.string.tab_console, Icons.Rounded.Terminal, Icons.Outlined.Terminal),
}

@Composable
fun RideLinkRoot(onStartRide: () -> Unit, onEndRide: () -> Unit) {
    val vm: MainViewModel = hiltViewModel()
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
                Tab.Ride -> RideScreen(vm, onStartRide, onEndRide)
                Tab.Settings -> SettingsScreen(vm)
                Tab.Console -> ConsoleScreen(vm)
            }
        }
    }
}
