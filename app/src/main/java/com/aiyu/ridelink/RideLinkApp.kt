package com.aiyu.ridelink

import android.app.Application
import com.aiyu.ridelink.core.analytics.RideRecorder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RideLinkApp : Application() {
    @Inject lateinit var recorder: RideRecorder

    override fun onCreate() {
        super.onCreate()
        // Records rides for Analytics whenever the scooter is connected; no screen needs to be open.
        recorder.start()
    }
}
