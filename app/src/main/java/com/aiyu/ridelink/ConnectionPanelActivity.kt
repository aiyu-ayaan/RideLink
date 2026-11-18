package com.aiyu.ridelink

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aiyu.ridelink.core.settings.AppLocale
import com.aiyu.ridelink.service.ClusterService
import com.aiyu.ridelink.ui.panel.ConnectionPanel
import com.aiyu.ridelink.ui.theme.RideLinkTheme
import dagger.hilt.android.AndroidEntryPoint

/** The panel the quick settings tile opens. Being visible also lets it start the ride service from the background. */
@AndroidEntryPoint
class ConnectionPanelActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RideLinkTheme {
                ConnectionPanel(
                    onConnect = { ClusterService.start(this) },
                    onDisconnect = { ClusterService.stop(this) },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }
}
