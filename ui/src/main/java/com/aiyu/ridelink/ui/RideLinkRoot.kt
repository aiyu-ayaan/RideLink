package com.aiyu.ridelink.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.aiyu.ridelink.ui.settings.SettingsSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.ui.about.AboutScreen
import com.aiyu.ridelink.ui.about.AcknowledgementsScreen
import com.aiyu.ridelink.ui.analytics.AnalyticsScreen
import com.aiyu.ridelink.ui.analytics.RideDetailScreen
import com.aiyu.ridelink.ui.care.CareScreen
import com.aiyu.ridelink.ui.console.ConsoleScreen
import com.aiyu.ridelink.ui.nav.FloatingNavBar
import com.aiyu.ridelink.ui.nav.LocalBottomBarInset
import com.aiyu.ridelink.ui.nav.NavTab
import com.aiyu.ridelink.ui.ride.RideScreen
import com.aiyu.ridelink.ui.settings.SettingsScreen
import com.aiyu.ridelink.ui.update.UpdateHost
import com.aiyu.ridelink.ui.update.UpdateScreen
import com.aiyu.ridelink.ui.update.UpdateViewModel

private val tabs = listOf(
    NavTab(R.string.tab_ride, Icons.Outlined.TwoWheeler, Icons.Rounded.TwoWheeler),
    NavTab(R.string.tab_analytics, Icons.Outlined.Insights, Icons.Rounded.Insights),
    NavTab(R.string.tab_forwarding, Icons.Outlined.Tune, Icons.Rounded.Tune),
)

/** Screens opened on top of the tabs. Console is a developer option (Settings → Developer options). */
private enum class Page { Console, Updates, About, Acknowledgements, Care }

/** Stack entry prefix for a Settings section page, e.g. "settings:SCOOTER". */
private const val SETTINGS = "settings:"

/** Stack entry prefix for a ride's detail page, e.g. "ride:42". */
private const val RIDE = "ride:"

@Composable
fun RideLinkRoot(onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val vm: MainViewModel = hiltViewModel()
    val updates: UpdateViewModel = hiltViewModel()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Page names, top last; a plain list survives rotation and process death through rememberSaveable.
    var stack by rememberSaveable { mutableStateOf(listOf<String>()) }
    val push = { p: String -> stack = stack + p }
    val pop = { stack = stack.dropLast(1) }

    // Predictive back: the system gesture seeks the page transition, so the page underneath shows while swiping;
    // release commits, a cancelled swipe springs back. Taps on Back buttons animate the same transition.
    val pages = remember { SeekableTransitionState(stack) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(stack) { if (pages.targetState != stack || pages.currentState != stack) pages.animateTo(stack) }
    PredictiveBackHandler(enabled = stack.isNotEmpty()) { events ->
        val back = stack.dropLast(1)
        try {
            events.collect { e -> pages.seekTo(e.progress, back) }
            // After the handler returns: turning it off (stack empty) from inside its own run breaks the next back.
            scope.launch { stack = back }
        } catch (c: CancellationException) {
            scope.launch { pages.animateTo(stack) }
            throw c
        }
    }

    // Back on Analytics or Settings goes to the Ride tab first; only Ride (the home tab) leaves the app.
    BackHandler(enabled = stack.isEmpty() && tab != 0) { tab = 0 }

    AutoConnect(vm, onConnect)
    LaunchedEffect(Unit) { updates.checkQuietly() }
    val connect = { vm.userStopped = false; onConnect() }
    val disconnect = { vm.userStopped = true; onDisconnect() }

    val holder = rememberSaveableStateHolder()
    val motion = MaterialTheme.motionScheme
    val spatial = motion.defaultSpatialSpec<IntOffset>()
    val effects = motion.defaultEffectsSpec<Float>()
    rememberTransition(pages, label = "page").AnimatedContent(
        // Forward slides in from the end, back slides out to it (navigation-direction rule).
        transitionSpec = {
            if (targetState.size >= initialState.size) {
                (slideInHorizontally(spatial) { it / 4 } + fadeIn(effects)) togetherWith (slideOutHorizontally(spatial) { -it / 8 } + fadeOut(effects))
            } else {
                (slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects)) togetherWith (slideOutHorizontally(spatial) { it / 4 } + fadeOut(effects))
            }
        },
        contentKey = { it.lastOrNull() },
    ) { shown ->
        val top = shown.lastOrNull()
        val settings = @Composable { section: SettingsSection? ->
            SettingsScreen(
                vm,
                section = section,
                onOpenSection = { push(SETTINGS + it.name) },
                onBack = pop,
                onOpenConsole = { push(Page.Console.name) },
                onOpenUpdates = { push(Page.Updates.name) },
                onOpenAbout = { push(Page.About.name) },
            )
        }
        // AnimatedContent drops a page's composition when it leaves; the holder keeps its scroll state for the way back.
        Surface(Modifier.fillMaxSize()) {
            when {
                top == null -> Tabs(tab, { tab = it }) { current ->
                    holder.SaveableStateProvider("tab:$current") {
                        when (current) {
                            0 -> RideScreen(vm, connect, disconnect, onOpenCare = { push(Page.Care.name) })
                            1 -> AnalyticsScreen(onOpenRide = { push(RIDE + it) })
                            else -> settings(null)
                        }
                    }
                }
                else -> holder.SaveableStateProvider(top) {
                    when {
                        top.startsWith(SETTINGS) -> settings(SettingsSection.valueOf(top.removePrefix(SETTINGS)))
                        top.startsWith(RIDE) -> RideDetailScreen(top.removePrefix(RIDE).toLong(), onBack = pop)
                        else -> when (Page.valueOf(top)) {
                            Page.Console -> ConsoleScreen(vm, onBack = pop)
                            Page.Updates -> UpdateScreen(onBack = pop)
                            Page.About -> AboutScreen(onBack = pop, onOpenUpdates = { push(Page.Updates.name) }, onOpenAcknowledgements = { push(Page.Acknowledgements.name) })
                            Page.Acknowledgements -> AcknowledgementsScreen(onBack = pop)
                            Page.Care -> CareScreen(onBack = pop)
                        }
                    }
                }
            }
        }
    }
    UpdateHost()
}

/**
 * The three tabs under a floating pill (Rewire / Domus design). The pill floats over content; scrolling down tucks
 * it away, scrolling up brings it back. Tab screens end with BottomBarSpacer so their last item clears it.
 */
@Composable
private fun Tabs(tab: Int, onSelect: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    val motion = MaterialTheme.motionScheme
    var scrolledAway by remember { mutableStateOf(false) }
    LaunchedEffect(tab) { scrolledAway = false }
    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y < -1f) scrolledAway = true else if (consumed.y > 1f) scrolledAway = false
                return Offset.Zero
            }
        }
    }
    val density = LocalDensity.current
    var pill by remember { mutableStateOf(0.dp) }
    Box(Modifier.fillMaxSize().nestedScroll(hideOnScroll)) {
        CompositionLocalProvider(LocalBottomBarInset provides pill) {
            AnimatedContent(
                targetState = tab,
                // M3 "fade through": outgoing fades fast, incoming fades and scales up slightly.
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
                        fadeOut(motion.fastEffectsSpec())
                },
                label = "tab",
            ) { content(it) }
        }
        AnimatedVisibility(
            !scrolledAway,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
            exit = slideOutVertically(motion.defaultSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
        ) {
            FloatingNavBar(tabs, tab, onSelect, Modifier.onSizeChanged { pill = with(density) { it.height.toDp() } })
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
