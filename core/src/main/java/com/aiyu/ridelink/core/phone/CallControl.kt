package com.aiyu.ridelink.core.phone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.provider.CallLog
import android.provider.Settings as AndroidSettings
import android.telephony.SmsManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Scooter switch actions: answer/end calls (ANSWER_PHONE_CALLS), reject SMS (SEND_SMS), voice assistant. */
@Singleton
class CallControl @Inject constructor(@ApplicationContext private val context: Context) {
    private val telecom get() = context.getSystemService(TelecomManager::class.java)

    fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun accept(): Boolean = allowed() && runCatching { telecom?.acceptRingingCall() }.isSuccess

    fun canSms() = granted(Manifest.permission.SEND_SMS) && granted(Manifest.permission.READ_CALL_LOG)

    /**
     * Texts the caller we just rejected. The call notification does not reliably carry the number, so it comes
     * from the newest call-log entry of the last two minutes (the dialer writes it a moment after the call ends).
     */
    suspend fun smsLastCaller(message: String): Boolean = withContext(Dispatchers.IO) {
        if (!canSms()) return@withContext false
        val since = System.currentTimeMillis() - 2 * 60_000
        var to: String? = null
        for (attempt in 0 until 5) {
            to = lastNumberSince(since)
            if (to != null) break
            delay(1_000)
        }
        val number = to ?: return@withContext false
        runCatching { sms().sendTextMessage(number, null, message, null, null) }.isSuccess
    }

    @Suppress("DEPRECATION")
    private fun sms(): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    private fun lastNumberSince(since: Long): String? = runCatching {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER),
            "${CallLog.Calls.DATE} >= ?",
            arrayOf(since.toString()),
            "${CallLog.Calls.DATE} DESC",
        )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
    }.getOrNull()

    /**
     * Opens the default voice assistant. Android blocks activity starts from the background unless the app may
     * draw over other apps, so this needs that grant (Settings shows a button for it).
     */
    fun openAssistant(): Boolean {
        if (!AndroidSettings.canDrawOverlays(context)) return false
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return listOf(Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST).any { action ->
            runCatching { context.startActivity(Intent(action).addFlags(flags)) }.isSuccess
        }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** Rejects a ringing call or hangs up the active one (TelecomManager.endCall, Android 9+). */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun end(): Boolean = allowed() && Build.VERSION.SDK_INT >= 28 && runCatching { telecom?.endCall() == true }.getOrDefault(false)
}
