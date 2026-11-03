package com.aiyu.ridelink.core.phone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Answers or ends the ringing call for the scooter buttons. Needs ANSWER_PHONE_CALLS. */
@Singleton
class CallControl @Inject constructor(@ApplicationContext private val context: Context) {
    private val telecom get() = context.getSystemService(TelecomManager::class.java)

    fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun accept(): Boolean = allowed() && runCatching { telecom?.acceptRingingCall() }.isSuccess

    /** Rejects a ringing call or hangs up the active one (TelecomManager.endCall, Android 9+). */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun end(): Boolean = allowed() && Build.VERSION.SDK_INT >= 28 && runCatching { telecom?.endCall() == true }.getOrDefault(false)
}
