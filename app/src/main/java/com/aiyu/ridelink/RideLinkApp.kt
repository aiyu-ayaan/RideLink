package com.aiyu.ridelink

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.aiyu.ridelink.core.analytics.RideRecorder
import com.aiyu.ridelink.core.care.CareMonitor
import com.aiyu.ridelink.core.phone.RideCheckIn
import com.aiyu.ridelink.core.settings.AppLocale
import com.aiyu.ridelink.service.CareNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RideLinkApp : Application() {
    @Inject lateinit var recorder: RideRecorder
    @Inject lateinit var care: CareMonitor
    @Inject lateinit var careNotifier: CareNotifier
    @Inject lateinit var checkIn: RideCheckIn

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLocale.applyToResources(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Records rides for Analytics whenever the scooter is connected; no screen needs to be open.
        recorder.start()
        // Refuels, low fuel and service reminders; the notifier listens before the monitor can emit.
        careNotifier.start()
        care.start()
        checkIn.start()
    }
}
