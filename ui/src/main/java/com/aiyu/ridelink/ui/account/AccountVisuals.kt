package com.aiyu.ridelink.ui.account

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.aiyu.ridelink.core.account.Account
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.theme.Dimens

/** [morph] at [progress], scaled from MaterialShapes' unit square to the layout size. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/**
 * The welcome hero: the "Closed loop" mark in an open cookie shape. Signing in closes the loop: the cookie morphs
 * into a circle with a quarter turn and the rider's photo replaces the mark.
 */
@Composable
fun LoopMark(account: Account?, photo: ImageBitmap?, modifier: Modifier = Modifier, size: Dp = 176.dp) {
    val motion = MaterialTheme.motionScheme
    val closed by animateFloatAsState(if (account != null) 1f else 0f, motion.slowSpatialSpec(), label = "loop")
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val c = MaterialTheme.colorScheme
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = closed * 90f }
                .background(c.primaryContainer, MorphShape(morph, closed)),
        )
        Crossfade(account, animationSpec = motion.defaultEffectsSpec(), label = "face") { a ->
            if (a == null) {
                Icon(painterResource(R.drawable.ic_ridelink), contentDescription = null, tint = c.onPrimaryContainer, modifier = Modifier.size(size * 0.46f))
            } else {
                ProfileAvatar(a, photo, size * 0.72f)
            }
        }
    }
}

/** Google photo in a circle; the first letter of the name on primary while it loads or when there is none. */
@Composable
fun ProfileAvatar(account: Account, photo: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val label = stringResource(R.string.account_photo_desc, account.name ?: account.email.orEmpty())
    Box(
        modifier.size(size).clip(CircleShape).background(c.primary).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val initial = (account.name ?: account.email)?.firstOrNull()?.uppercase() ?: "?"
            Text(
                initial,
                style = if (size >= 64.dp) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = c.onPrimary,
            )
        }
    }
}

/** "Continue with Google": the branded G on its white disc, a loading indicator while the picker is open. */
@Composable
fun GoogleSignInButton(busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = !busy,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        modifier = modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.md), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                LoadingIndicator(Modifier.size(24.dp))
            } else {
                Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Text(stringResource(R.string.account_continue_google), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
    }
}
