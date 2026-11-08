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

    /** Plain SMS to [to] (SEND_SMS only); split into parts when longer than one segment. */
    fun sendSms(to: String, message: String): Boolean {
        if (!granted(Manifest.permission.SEND_SMS)) return false
        return runCatching {
            val sms = sms()
            val parts = sms.divideMessage(message)
            if (parts.size > 1) sms.sendMultipartTextMessage(to, null, parts, null, null) else sms.sendTextMessage(to, null, message, null, null)
        }.isSuccess
    }

    private val autoReplied = mutableMapOf<String, Long>()

    /**
     * Auto-reply: texts the newest caller since [since] if the call log marks the call missed (an answered or
     * rejected call gets nothing), at most once per number every 10 minutes.
     */
    suspend fun smsMissedCaller(message: String, since: Long): Boolean = withContext(Dispatchers.IO) {
        if (!canSms()) return@withContext false
        var to: String? = null
        for (attempt in 0 until 5) {
            to = lastMissedSince(since)
            if (to != null) break
            delay(1_000)
        }
        val number = to ?: return@withContext false
        val now = System.currentTimeMillis()
        if (now - (autoReplied[number] ?: 0) < 10 * 60_000) return@withContext false
        autoReplied[number] = now
        runCatching { sms().sendTextMessage(number, null, message, null, null) }.isSuccess
    }

    /**
     * Name (or number) of the caller if the newest call since [since] was missed, for the display's pop-up.
     * Needs READ_CALL_LOG; retries while the dialer writes the entry.
     */
    suspend fun missedCallerSince(since: Long): String? = withContext(Dispatchers.IO) {
        if (!granted(Manifest.permission.READ_CALL_LOG)) return@withContext null
        repeat(5) {
            if (it > 0) delay(1_000)
            lastMissed(since)?.let { (number, name) -> return@withContext name ?: number }
        }
        null
    }

    private fun lastMissedSince(since: Long): String? = lastMissed(since)?.first

    /** Number and cached contact name of the newest call since [since], if it was missed. */
    private fun lastMissed(since: Long): Pair<String, String?>? = runCatching {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME),
            "${CallLog.Calls.DATE} >= ?",
            arrayOf(since.toString()),
            "${CallLog.Calls.DATE} DESC",
        )?.use { c ->
            if (c.moveToFirst() && c.getInt(1) == CallLog.Calls.MISSED_TYPE) {
                c.getString(0)?.takeIf { it.isNotBlank() }?.let { it to c.getString(2)?.takeIf(String::isNotBlank) }
            } else null
        }
    }.getOrNull()

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
