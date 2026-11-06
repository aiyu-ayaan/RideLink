package com.aiyu.ridelink.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.aiyu.ridelink.MainActivity
import com.aiyu.ridelink.core.care.CareMonitor
import com.aiyu.ridelink.core.care.CareStatus
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.care.label
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Posts the fuel and service notifications [CareMonitor] asks for. */
@Singleton
class CareNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val monitor: CareMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch { monitor.events.collect(::post) }
    }

    private fun post(e: CareMonitor.Event) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.channel_care), NotificationManager.IMPORTANCE_DEFAULT))
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val (id, builder) = when (e) {
            is CareMonitor.Event.Refuel -> ID_REFUEL to base(
                context.getString(R.string.notify_refuel_title),
                context.getString(R.string.notify_refuel_body, e.odometerKm),
                openApp,
            )
            is CareMonitor.Event.LowFuel -> {
                // "geo:0,0?q=" searches around the phone's position in Maps (or any maps app).
                val search = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(context.getString(R.string.notify_fuel_query))))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val find = PendingIntent.getActivity(context, 1, search, PendingIntent.FLAG_IMMUTABLE)
                ID_LOW_FUEL to base(
                    context.getString(R.string.notify_low_fuel_title),
                    e.rangeKm?.let { context.getString(R.string.notify_low_fuel_range, it) } ?: context.getString(R.string.notify_low_fuel_bars),
                    find,
                ).addAction(0, context.getString(R.string.notify_low_fuel_action), find)
            }
            is CareMonitor.Event.Due -> ID_DUE to base(
                context.getString(R.string.notify_due_title),
                e.items.joinToString("\n") { line(it) },
                openApp,
            )
        }
        runCatching { nm.notify(id, builder.build()) }
    }

    private fun base(title: String, text: String, tap: PendingIntent) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_ridelink)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(tap)
        .setAutoCancel(true)

    private fun line(st: CareStatus): String {
        val name = context.getString(st.record.item.label)
        return when {
            st.kmLeft != null && st.kmLeft!! < 0 -> context.getString(R.string.care_overdue_km, name, -st.kmLeft!!)
            st.kmLeft != null -> context.getString(R.string.care_left_km, name, st.kmLeft!!)
            st.daysLeft != null && st.daysLeft!! < 0 -> context.getString(R.string.care_expired, name)
            st.daysLeft != null -> context.getString(R.string.care_left_days, name, st.daysLeft!!.toInt())
            else -> name
        }
    }

    private companion object {
        const val CHANNEL = "care"
        const val ID_REFUEL = 20
        const val ID_LOW_FUEL = 21
        const val ID_DUE = 22
    }
}
