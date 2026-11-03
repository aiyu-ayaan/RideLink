package com.aiyu.ridelink.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** 8dp spacing grid with a 4dp half step. */
object Dimens {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val gutter = 16.dp
    val minTouch = 48.dp
    val contentMaxWidth = 640.dp
}

// M3 tonal-spot scheme from seed #B3261E (scooter red). Used when dynamic color is unavailable (< Android 12).
private val LightScheme = lightColorScheme(
    primary = Color(0xFF904A42), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD5), onPrimaryContainer = Color(0xFF73342C),
    inversePrimary = Color(0xFFFFB4A9),
    secondary = Color(0xFF775651), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD5), onSecondaryContainer = Color(0xFF5D3F3B),
    tertiary = Color(0xFF705C2E), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCDFA6), onTertiaryContainer = Color(0xFF574419),
    background = Color(0xFFFFF8F7), onBackground = Color(0xFF231918),
    surface = Color(0xFFFFF8F7), onSurface = Color(0xFF231918),
    surfaceVariant = Color(0xFFF5DDDA), onSurfaceVariant = Color(0xFF534341),
    surfaceTint = Color(0xFF904A42),
    inverseSurface = Color(0xFF392E2D), inverseOnSurface = Color(0xFFFFEDEA),
    outline = Color(0xFF857371), outlineVariant = Color(0xFFD8C2BF),
    surfaceBright = Color(0xFFFFF8F7), surfaceDim = Color(0xFFE8D6D3),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFF0EE),
    surfaceContainer = Color(0xFFFCEAE7), surfaceContainerHigh = Color(0xFFF7E4E1),
    surfaceContainerHighest = Color(0xFFF1DEDC),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB4A9), onPrimary = Color(0xFF561E18),
    primaryContainer = Color(0xFF73342C), onPrimaryContainer = Color(0xFFFFDAD5),
    inversePrimary = Color(0xFF904A42),
    secondary = Color(0xFFE7BDB7), onSecondary = Color(0xFF442926),
    secondaryContainer = Color(0xFF5D3F3B), onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFFDFC38C), onTertiary = Color(0xFF3E2E04),
    tertiaryContainer = Color(0xFF574419), onTertiaryContainer = Color(0xFFFCDFA6),
    background = Color(0xFF1A1110), onBackground = Color(0xFFF1DEDC),
    surface = Color(0xFF1A1110), onSurface = Color(0xFFF1DEDC),
    surfaceVariant = Color(0xFF534341), onSurfaceVariant = Color(0xFFD8C2BF),
    surfaceTint = Color(0xFFFFB4A9),
    inverseSurface = Color(0xFFF1DEDC), inverseOnSurface = Color(0xFF392E2D),
    outline = Color(0xFFA08C8A), outlineVariant = Color(0xFF534341),
    surfaceBright = Color(0xFF423736), surfaceDim = Color(0xFF1A1110),
    surfaceContainerLowest = Color(0xFF140C0B), surfaceContainerLow = Color(0xFF231918),
    surfaceContainer = Color(0xFF271D1C), surfaceContainerHigh = Color(0xFF322827),
    surfaceContainerHighest = Color(0xFF3D3231),
)

@Composable
fun RideLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    // enableEdgeToEdge() follows the system theme; keep bar icon colors in sync with ours.
    val window = (LocalView.current.context as? Activity)?.window
    if (window != null) {
        SideEffect {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        typography = Typography(),
        content = content,
    )
}
