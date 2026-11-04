package com.aiyu.ridelink.ui.nav

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiyu.ridelink.ui.theme.Dimens

/** One bottom-bar destination. */
data class NavTab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

/** Height the floating pill covers at the bottom; tab screens end their scroll content with this much space. */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** Space at the end of a tab screen's scroll content so the floating pill never covers the last item. */
@Composable
fun BottomBarSpacer() = Spacer(Modifier.height(LocalBottomBarInset.current))

/**
 * Floating pill (same design as Rewire and Domus). The selected tab grows into icon + label;
 * the others stay icon-only.
 */
@Composable
fun FloatingNavBar(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Dimens.md), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
            Row(Modifier.padding(6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                tabs.forEachIndexed { i, t -> PillItem(t, i == selected) { onSelect(i) } }
            }
        }
    }
}

@Composable
private fun PillItem(t: NavTab, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val bg by animateColorAsState(if (selected) c.primaryContainer else Color.Transparent, effects, label = "pillBg")
    val fg by animateColorAsState(if (selected) c.onPrimaryContainer else c.onSurfaceVariant, effects, label = "pillFg")
    val label = stringResource(t.label)
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .selectable(selected, onClick = onClick, role = Role.Tab)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .heightIn(min = Dimens.minTouch)
            .padding(horizontal = Dimens.lg)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavIcon(t, selected, fg)
        if (selected) {
            Spacer(Modifier.width(Dimens.sm))
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
        }
    }
}

/** Tab icon that pops in with a small bouncy twist when its tab gets selected (not on first show, not with animations off). */
@Composable
private fun NavIcon(t: NavTab, selected: Boolean, tint: Color) {
    val context = LocalContext.current
    val reduced = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val pop = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (selected && shown && !reduced) {
            pop.snapTo(0.6f)
            // shortcut: a spring by design (the bounce is the point); motionScheme has no high-bounce spec.
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow))
        } else {
            pop.snapTo(1f) // a fast tab switch cancels the pop mid-way; never leave the icon shrunk
        }
        shown = true
    }
    Icon(
        if (selected) t.selectedIcon else t.icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            rotationZ = (1f - pop.value) * -40f
        },
    )
}
