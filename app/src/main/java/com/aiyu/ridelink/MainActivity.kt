package com.aiyu.ridelink

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import com.aiyu.ridelink.core.settings.AppLocale
import com.aiyu.ridelink.service.ClusterService
import com.aiyu.ridelink.ui.RideLinkRoot
import com.aiyu.ridelink.ui.theme.RideLinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener(::playSplashExit)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RideLinkTheme {
                RideLinkRoot(
                    onConnect = { ClusterService.start(this) },
                    onDisconnect = { ClusterService.stop(this) },
                )
            }
        }
    }

    /**
     * Lets the logo animation (ring draws, spark pops, pulse) finish, then the mark grows and fades while the
     * red background dissolves into the app.
     *
     * Never leaves the splash stuck on screen: any failure removes it at once (on Android 12 the icon view can be
     * missing when the app is opened from a notification or adb, and [SplashScreenViewProvider.iconView] then
     * throws), removal is idempotent, and a watchdog removes it shortly after the exit should have ended even if
     * the animator never reports its end. With animations turned off the animator ends immediately.
     */
    private fun playSplashExit(provider: SplashScreenViewProvider) {
        var removed = false
        fun remove() {
            if (removed) return
            removed = true
            runCatching { provider.remove() }
        }
        val screen: View = provider.view
        val wait = runCatching {
            val iconEnd = provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis
            (iconEnd - SystemClock.uptimeMillis()).coerceIn(0L, ICON_MAX_MS)
        }.getOrDefault(0L)
        // Watchdog: worst case the splash is gone WATCHDOG_SLACK_MS after the planned end.
        // Posted on the activity's decor view, which stays attached for the activity's whole life.
        window.decorView.postDelayed({ remove() }, wait + EXIT_MS + WATCHDOG_SLACK_MS)
        runCatching {
            val icon: View? = runCatching { provider.iconView }.getOrNull()
            val parts = buildList<Animator> {
                if (icon != null) {
                    add(ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.35f).setDuration(EXIT_MS))
                    add(ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.35f).setDuration(EXIT_MS))
                    add(ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f).setDuration(EXIT_MS * 3 / 4))
                }
                add(ObjectAnimator.ofFloat(screen, View.ALPHA, 1f, 0f).setDuration(EXIT_MS).apply { interpolator = AccelerateInterpolator() })
            }
            AnimatorSet().apply {
                playTogether(parts)
                interpolator = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
                startDelay = wait
                // onAnimationEnd also runs after cancel(), e.g. when the activity goes away mid-exit.
                doOnEnd { remove() }
                start()
            }
        }.onFailure { remove() }
    }

    private companion object {
        const val EXIT_MS = 380L
        /** Never hold the app back by more than the icon animation itself. */
        const val ICON_MAX_MS = 1_000L
        const val WATCHDOG_SLACK_MS = 500L
    }
}
