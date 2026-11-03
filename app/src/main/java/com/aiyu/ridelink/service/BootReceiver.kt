package com.aiyu.ridelink.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aiyu.ridelink.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Restarts the ride session after reboot so the scooter reconnects without opening the app. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            if (settings.current().deviceAddress != null) runCatching { ClusterService.start(context) }
            pending.finish()
        }
    }
}
