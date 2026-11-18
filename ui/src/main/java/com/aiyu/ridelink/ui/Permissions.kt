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

/** Opens Android's "stop optimising battery usage" prompt for RideLink. */
@android.annotation.SuppressLint("BatteryLife") // Personal sideloaded app; the ride session must not be frozen.
internal fun Context.askBatteryExemption() {
    val ask = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:$packageName"))
    runCatching { startActivity(ask) }
        .onFailure { startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

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
    // Fresh signal bars with the screen off (TelephonyManager.requestCellInfoUpdate). Optional: the rider may pick
    // "approximate", so the card does not wait for it.
    Manifest.permission.ACCESS_FINE_LOCATION,
)

internal fun Context.hasPhoneState() = phonePermissions.filter { it != Manifest.permission.ACCESS_FINE_LOCATION }.all { granted(it) }

/** "Reject with SMS": send the text, and find the caller's number in the call log. Asked from Settings. */
internal val smsPermissions = arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CALL_LOG)

internal fun Context.hasSms() = smsPermissions.all { granted(it) }

/** Parked spot and check-in links in metres; Android asks fine and coarse together. */
internal val preciseLocationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** Ride check-in: send the SMS and put a precise link in it. */
internal val checkInPermissions = arrayOf(Manifest.permission.SEND_SMS) + preciseLocationPermissions
