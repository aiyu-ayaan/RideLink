package com.aiyu.ridelink.ui.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhonelinkSetup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.ridelink.core.account.Account
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.nav.isTwoPane
import com.aiyu.ridelink.ui.theme.Dimens
import kotlinx.coroutines.delay

/** Pause between the entrance steps (mark, words, benefits, actions). */
private const val STAGGER_MS = 80L

/**
 * First launch only: optional Google sign-in for backups. "Skip for now" and "Start riding" both end it for good;
 * Settings -> Google account signs in later.
 */
@Composable
fun WelcomeScreen(vm: AccountViewModel) {
    val account by vm.account.collectAsStateWithLifecycle()
    val photo by vm.photo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // One orchestrated entrance: each block rises in after the one before.
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { repeat(4) { delay(STAGGER_MS); step++ } }

    val mark = @Composable { LoopMark(account, photo, Modifier.reveal(step >= 1)) }
    val words = @Composable { Words(account, Modifier.reveal(step >= 2)) }
    val benefits = @Composable { if (account == null) Benefits(Modifier.reveal(step >= 3)) }
    val actions = @Composable {
        Actions(
            account = account,
            busy = vm.busy,
            error = vm.error,
            onSignIn = { vm.signIn(context) },
            onSkip = { vm.finishWelcome() },
            onSwitch = { vm.signOut() },
            modifier = Modifier.reveal(step >= 4),
        )
    }

    Surface(Modifier.fillMaxSize()) {
        if (isTwoPane()) {
            Row(Modifier.fillMaxSize().safeDrawingPadding().padding(Dimens.xl), horizontalArrangement = Arrangement.spacedBy(Dimens.xxl)) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { mark() }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.xl, Alignment.CenterVertically),
                ) {
                    words()
                    benefits()
                    actions()
                }
            }
        } else BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Hero and words centred in the free space, actions in the thumb zone; scrolls on small or zoomed screens.
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.xl, vertical = Dimens.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(Dimens.lg))
                Column(
                    Modifier.widthIn(max = Dimens.contentMaxWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.xl),
                ) {
                    mark()
                    words()
                    benefits()
                }
                Box(Modifier.widthIn(max = Dimens.contentMaxWidth).padding(top = Dimens.xl)) { actions() }
            }
        }
    }
}

/** Fades and lifts a block in; transform only, so nothing below it moves. */
@Composable
private fun Modifier.reveal(visible: Boolean): Modifier {
    val motion = MaterialTheme.motionScheme
    val alpha by animateFloatAsState(if (visible) 1f else 0f, motion.defaultEffectsSpec(), label = "alpha")
    val lift by animateFloatAsState(if (visible) 0f else 1f, motion.defaultSpatialSpec(), label = "lift")
    return graphicsLayer {
        this.alpha = alpha
        translationY = lift * 24.dp.toPx()
    }
}

@Composable
private fun Words(account: Account?, modifier: Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        account,
        modifier = modifier.fillMaxWidth(),
        contentKey = { it != null },
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith fadeOut(motion.fastEffectsSpec())
        },
        label = "words",
    ) { a ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
            val first = a?.name?.substringBefore(' ')?.takeIf { it.isNotBlank() }
            Text(
                when {
                    a == null -> stringResource(R.string.welcome_title)
                    first != null -> stringResource(R.string.welcome_hi, first)
                    else -> stringResource(R.string.welcome_hi_plain)
                },
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                if (a == null) stringResource(R.string.welcome_body) else stringResource(R.string.welcome_signed_in_as, a.email ?: a.name.orEmpty()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Benefits(modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
        Benefit(Icons.Rounded.CloudUpload, R.string.welcome_point_backup)
        Benefit(Icons.Rounded.PhonelinkSetup, R.string.welcome_point_restore)
        Benefit(Icons.Rounded.Lock, R.string.welcome_point_private)
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: Int) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.lg)) {
        Surface(color = c.secondaryContainer, contentColor = c.onSecondaryContainer, shape = CircleShape, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
        }
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Actions(
    account: Account?,
    busy: Boolean,
    error: Int?,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    onSwitch: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        if (error != null) {
            Row(
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(stringResource(error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        if (account == null) {
            GoogleSignInButton(busy, onSignIn)
            TextButton(onClick = onSkip, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch)) {
                Text(stringResource(R.string.welcome_skip))
            }
            Text(
                stringResource(R.string.welcome_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Button(
                onClick = onSkip,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) {
                Text(stringResource(R.string.welcome_start), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
            TextButton(onClick = onSwitch, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch)) {
                Text(stringResource(R.string.account_switch))
            }
        }
    }
}
