package com.aiyu.ridelink.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.aiyu.ridelink.ConnectionPanelActivity
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.core.settings.AppLocale
import com.aiyu.ridelink.core.settings.SettingsRepository
import com.aiyu.ridelink.ui.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Quick settings tile: shows the link state and opens [ConnectionPanelActivity], like Android's Internet tile. */
@AndroidEntryPoint
class ConnectionTileService : TileService() {
    @Inject lateinit var controller: ClusterController
    @Inject lateinit var settings: SettingsRepository
    private var listening: CoroutineScope? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onStartListening() {
        super.onStartListening()
        listening = MainScope().also { scope ->
            scope.launch {
                combine(controller.linkState, settings.settings) { link, s -> link to s.deviceName }
                    .collect { (link, name) -> show(link, name) }
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    private fun show(link: LinkState, name: String?) {
        val tile = qsTile ?: return
        tile.state = if (link is LinkState.Ready) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (link) {
                is LinkState.Ready -> link.name ?: name
                is LinkState.Connecting -> getString(R.string.panel_waiting)
                else -> getString(R.string.panel_off)
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val panel = Intent(this, ConnectionPanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, panel, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(panel)
            }
        }
    }
}
