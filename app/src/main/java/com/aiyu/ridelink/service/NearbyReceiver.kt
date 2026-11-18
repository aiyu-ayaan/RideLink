package com.aiyu.ridelink.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.ui.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Wakes RideLink when the paired scooter starts advertising (ignition on), even after Android killed the app.
 *
 * Uses a PendingIntent BLE scan: the Bluetooth stack filters by address (offloaded to the controller on most
 * phones) at low-power duty cycle and only wakes the app on a match, so the idle cost is close to zero.
 * The display stops advertising once connected, so hits only come while RideLink is not linked.
 */
@AndroidEntryPoint
class NearbyReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: ClusterController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FOUND) return
        if (intent.getIntExtra(android.bluetooth.le.BluetoothLeScanner.EXTRA_ERROR_CODE, 0) != 0) return
        if (ClusterService.running) return
        Log.i(TAG, "scooter nearby, starting ride session")
        controller.note("Scooter nearby, starting the ride session")
        try {
            ClusterService.start(context)
        } catch (e: Exception) {
            // Android 12+ blocks background service starts unless RideLink is out of battery optimisation.
            if (Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException) {
                controller.note("Scooter nearby, but Android blocked the background start (battery optimisation is on)")
                promptToConnect(context)
            }
            else Log.w(TAG, "start failed", e)
        }
    }

    companion object {
        private const val TAG = "NearbyReceiver"
        private const val ACTION_FOUND = "com.aiyu.ridelink.SCOOTER_NEARBY"
        private const val CHANNEL = "nearby"
        private const val NOTIFICATION_ID = 40

        /** Tapping connects straight away: a notification tap is allowed to start the foreground service. */
        fun promptToConnect(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.channel_nearby), NotificationManager.IMPORTANCE_DEFAULT))
            val connect = PendingIntent.getForegroundService(context, 0, Intent(context, ClusterService::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_ridelink)
                .setContentTitle(context.getString(R.string.notify_nearby_title))
                .setContentText(context.getString(R.string.notify_nearby_body))
                .setContentIntent(connect)
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(NOTIFICATION_ID, n) }
        }

        fun clearPrompt(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }

        private fun pending(context: Context) = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, NearbyReceiver::class.java).setAction(ACTION_FOUND),
            // The Bluetooth stack writes the scan results into the intent, so it must be mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )

        /**
         * Watches for [address] (null stops watching). Survives the app process being killed, but not a force stop.
         * shortcut: Bluetooth off/on drops the scan until the ride service starts again; add a BT state hook if that bites.
         */
        fun watch(context: Context, address: String?) {
            // BLUETOOTH_SCAN only exists on Android 12+; older versions always report it as denied.
            if (Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
            ) return
            val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return
            val pi = pending(context)
            runCatching {
                scanner.stopScan(pi)
                if (address == null) return
                val filter = ScanFilter.Builder().setDeviceAddress(address).build()
                val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
                scanner.startScan(listOf(filter), settings, pi)
            }.onFailure { Log.w(TAG, "watch failed", it) }
        }
    }
}
