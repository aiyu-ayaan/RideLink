package com.aiyu.ridelink.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiyu.ridelink.ui.theme.Dimens

private val TwoPaneMinWidth = 600.dp

/** True in a landscape window wide enough for two panes: a phone on a handlebar mount, a tablet, an open foldable. */
@Composable
fun isTwoPane(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.width > size.height && with(LocalDensity.current) { size.width.toDp() } >= TwoPaneMinWidth
}

/**
 * Collapsing on the flexible bars. The compact bar is pinned: a collapsing behavior with no bar to measure swallows
 * every scroll, so the panes would never move.
 */
@Composable
fun adaptiveScrollBehavior(): TopAppBarScrollBehavior =
    if (isTwoPane()) TopAppBarDefaults.pinnedScrollBehavior() else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

/** The screen's flexible app bar; two-pane windows get the compact one, because landscape has little height to spare. */
@Composable
fun AdaptiveTopAppBar(
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    large: Boolean = false,
) {
    when {
        isTwoPane() && subtitle != null ->
            TopAppBar(title = title, subtitle = subtitle, navigationIcon = navigationIcon, actions = actions, colors = appBarColors())
        isTwoPane() -> TopAppBar(title = title, navigationIcon = navigationIcon, actions = actions, colors = appBarColors())
        large -> LargeFlexibleTopAppBar(
            title = title, subtitle = subtitle, navigationIcon = navigationIcon, actions = actions,
            colors = appBarColors(), scrollBehavior = scrollBehavior,
        )
        else -> MediumFlexibleTopAppBar(
            title = title, subtitle = subtitle, navigationIcon = navigationIcon, actions = actions,
            colors = appBarColors(), scrollBehavior = scrollBehavior,
        )
    }
}

/** Side-by-side panes that clear a landscape camera cutout. For panes whose items bring their own gutters. */
@Composable
fun PaneRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)), content = content)
}

/** Two equal panes that scroll on their own; each ends clear of the floating pill. */
@Composable
fun TwoPane(
    modifier: Modifier = Modifier,
    spacing: Dp = Dimens.md,
    start: @Composable ColumnScope.() -> Unit,
    end: @Composable ColumnScope.() -> Unit,
) {
    PaneRow(modifier.padding(horizontal = Dimens.gutter)) {
        listOf(start, end).forEachIndexed { i, pane ->
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = if (i == 0) 0.dp else Dimens.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                pane()
                Spacer(Modifier.height(Dimens.xl))
                BottomBarSpacer()
            }
        }
    }
}
