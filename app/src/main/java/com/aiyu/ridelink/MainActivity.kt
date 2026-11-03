package com.aiyu.ridelink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aiyu.ridelink.service.ClusterService
import com.aiyu.ridelink.ui.RideLinkRoot
import com.aiyu.ridelink.ui.theme.RideLinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
}
