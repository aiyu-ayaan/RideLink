package com.aiyu.ridelink.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aiyu.ridelink.MainActivity
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.ui.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Keeps the cluster session alive while the screen is off. */
@AndroidEntryPoint
class ClusterService : LifecycleService() {
    @Inject lateinit var controller: ClusterController

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            controller.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(getString(R.string.status_connecting)), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        controller.start()
        // Android can leave the listener unbound after an app update; without it no Maps or call events arrive.
        runCatching { NotificationListenerService.requestRebind(ComponentName(this, RideNotificationListener::class.java)) }
        lifecycleScope.launch {
            controller.linkState.collect { state ->
                val text = when (state) {
                    is LinkState.Ready -> getString(R.string.status_connected, state.name ?: state.address)
                    is LinkState.Connecting -> getString(R.string.status_waiting)
                    is LinkState.Failed -> state.reason
                    LinkState.Idle -> getString(R.string.status_idle)
                }
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
            }
        }
        return START_STICKY
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_ride), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ClusterService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_ridelink)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_end_ride), stop)
            .build()
    }

    companion object {
        private const val CHANNEL = "ride"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "stop"

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, ClusterService::class.java))
        fun stop(context: Context) = context.startService(Intent(context, ClusterService::class.java).setAction(ACTION_STOP))
    }
}
