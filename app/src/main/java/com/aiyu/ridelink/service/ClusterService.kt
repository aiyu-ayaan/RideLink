package com.aiyu.ridelink.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aiyu.ridelink.MainActivity
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.core.settings.SettingsRepository
import com.aiyu.ridelink.ui.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Keeps the cluster session alive while the screen is off. */
@AndroidEntryPoint
class ClusterService : LifecycleService() {
    @Inject lateinit var controller: ClusterController
    @Inject lateinit var settings: SettingsRepository
    private var watchJob: Job? = null
    private var idleJob: Job? = null
    private var statusJob: Job? = null
    private var calls: CallStateWatcher? = null

    /**
     * Held while the display is connected, like stock BaseBleConnectHelperService.startWakelock (partial, 30 min).
     * Without it the phone sleeps with the screen off and the 2 s status, nav and caller frames stall.
     */
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RideLink:ride").apply {
            setReferenceCounted(false)
        }
    }
    private var wakeJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            // The rider ended the session: stay off until they press Connect, even if the scooter is nearby.
            watchJob?.cancel()
            NearbyReceiver.watch(this, null)
            shutDown()
            return START_NOT_STICKY
        }
        startForegroundTyped(notification(getString(R.string.status_connecting)))
        running = true
        controller.start()
        // Armed while the session runs, so the scooter wakes RideLink again if Android kills it.
        if (watchJob == null) watchJob = lifecycleScope.launch {
            settings.settings.map { it.deviceAddress }.distinctUntilChanged().collect { NearbyReceiver.watch(this@ClusterService, it) }
        }
        // Scooter off or out of range: end the session (and its "Waiting for scooter" notification);
        // the nearby scan above starts it again at the next ignition on.
        if (idleJob == null) idleJob = lifecycleScope.launch {
            controller.linkState.collectLatest { state ->
                if (state is LinkState.Ready) return@collectLatest
                delay(IDLE_STOP_MS)
                shutDown()
            }
        }
        if (calls == null) calls = CallStateWatcher(this, controller).takeIf { it.start() }
        // Android can leave the listener unbound after an app update; without it no Maps or call events arrive.
        runCatching { NotificationListenerService.requestRebind(ComponentName(this, RideNotificationListener::class.java)) }
        if (statusJob == null) statusJob = lifecycleScope.launch {
            controller.linkState.collect { state ->
                val text = when (state) {
                    is LinkState.Ready -> getString(R.string.status_connected, state.name ?: state.address)
                    is LinkState.Connecting -> getString(R.string.status_waiting)
                    is LinkState.Failed -> state.reason
                    LinkState.Idle -> getString(R.string.status_idle)
                }
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
                if (state is LinkState.Ready) holdWakeLock() else releaseWakeLock()
            }
        }
        return START_STICKY
    }

    /**
     * Also typed `location` when location is granted, so the parked spot, check-in links and weather get a fix
     * while the screen is off. Android refuses that type when the service is started from the background (boot):
     * then it runs as `connectedDevice` only, and the next start from the app adds location.
     */
    private fun startForegroundTyped(n: Notification) {
        val device = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        val withLocation = Build.VERSION.SDK_INT >= 29 && hasLocation() && runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, n, device or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }.isSuccess
        if (!withLocation) ServiceCompat.startForeground(this, NOTIFICATION_ID, n, device)
    }

    private fun shutDown() {
        calls?.stop()
        calls = null
        controller.stop()
        releaseWakeLock()
        stopSelf()
    }

    private fun hasLocation() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    /** Renewed before the timeout for as long as the link stays up, so a long ride keeps it. */
    private fun holdWakeLock() {
        if (wakeJob?.isActive == true) return
        wakeJob = lifecycleScope.launch {
            while (true) {
                wakeLock.acquire(WAKE_LOCK_MS)
                delay(WAKE_LOCK_MS / 2)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeJob?.cancel()
        wakeJob = null
        if (wakeLock.isHeld) wakeLock.release()
    }

    override fun onDestroy() {
        running = false
        calls?.stop()
        releaseWakeLock()
        super.onDestroy()
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
        private const val WAKE_LOCK_MS = 30 * 60_000L
        /** Rides out short drops (tunnel, restart) before ending the session. */
        private const val IDLE_STOP_MS = 60_000L

        /** True while this process runs the session; a killed process reads false again. */
        @Volatile var running = false
            private set

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, ClusterService::class.java))
        fun stop(context: Context) = context.startService(Intent(context, ClusterService::class.java).setAction(ACTION_STOP))
    }
}
