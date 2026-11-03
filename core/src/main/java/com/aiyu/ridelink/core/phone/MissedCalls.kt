package com.aiyu.ridelink.core.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Unseen missed calls for the older Ntorq status frame. Needs READ_CALL_LOG; 0 without it. */
@Singleton
class MissedCalls @Inject constructor(@ApplicationContext private val context: Context) {
    @Volatile private var cached = 0
    @Volatile private var readAt = 0L

    fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /** Cached for [MAX_AGE_MS] so the 2 s status loop does not query the call log every time. */
    fun count(now: Long = System.currentTimeMillis()): Int {
        if (!allowed()) return 0
        if (now - readAt < MAX_AGE_MS) return cached
        cached = runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                null,
            )?.use { it.count } ?: 0
        }.getOrDefault(0)
        readAt = now
        return cached
    }

    private companion object {
        const val MAX_AGE_MS = 30_000L
    }
}
