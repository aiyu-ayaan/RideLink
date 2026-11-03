package com.aiyu.ridelink.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Runtime permissions the ride session needs, asked together from the Ride screen. */
internal val bluetoothPermissions = buildList {
    if (Build.VERSION.SDK_INT >= 31) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

internal fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Notification permission is nice-to-have; the Bluetooth ones gate everything. */
internal fun Context.hasBluetooth() = bluetoothPermissions.all { it == Manifest.permission.POST_NOTIFICATIONS || granted(it) }

internal fun Context.hasListener() = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)

/**
 * Optional: network type (TelephonyManager.getDataNetworkType) needs READ_PHONE_STATE, and the stock app only
 * reads the signal level with location granted. Coarse location also feeds weather and place name;
 * ANSWER_PHONE_CALLS lets the scooter buttons accept or reject a call; READ_CALL_LOG feeds the missed-call badge.
 */
internal val phonePermissions = arrayOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ANSWER_PHONE_CALLS,
    Manifest.permission.READ_CALL_LOG,
)

internal fun Context.hasPhoneState() = phonePermissions.all { granted(it) }
