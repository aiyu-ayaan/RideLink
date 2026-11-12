package com.aiyu.ridelink.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.ui.R

/**
 * Backup for incoming calls, like the stock app's phone-state listener: some dialers post a call notification
 * RideNotificationListener cannot recognise. Ringing sends the caller at once (the number when call-log access is
 * granted, else "Incoming call"); when the dialer's notification arrives with a contact name, that name replaces it.
 * Answered or dialled keeps the caller up (call indicator) until the call ends. Needs READ_PHONE_STATE (the optional phone card on the Ride screen).
 *
 * shortcut: PhoneStateListener is deprecated but is the only callback that still hands over the number;
 * move to TelephonyCallback if the number stops mattering.
 */
@Suppress("DEPRECATION")
class CallStateWatcher(private val context: Context, private val controller: ClusterController) {
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private var ringing = false
    private var inCall = false

    private val listener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    ringing = true
                    // A name from the notification wins; only start the frames if nothing is showing yet.
                    if (controller.caller.value == null) {
                        controller.onIncomingCall(phoneNumber?.takeIf { it.isNotBlank() }?.removePrefix("+91") ?: context.getString(R.string.call_unknown))
                    }
                }
                // Answered or dialled: keep the caller (or "On call") up for the whole call.
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    ringing = false
                    inCall = true
                    controller.onCallActive(null)
                }
                else -> if (ringing || inCall) {
                    ringing = false
                    inCall = false
                    if (controller.caller.value != null) controller.onCallEnded()
                }
            }
        }
    }

    fun start(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching { telephony?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE) }.isSuccess
    }

    fun stop() {
        runCatching { telephony?.listen(listener, PhoneStateListener.LISTEN_NONE) }
    }
}
