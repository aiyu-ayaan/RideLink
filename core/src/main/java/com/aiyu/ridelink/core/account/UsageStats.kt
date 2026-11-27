package com.aiyu.ridelink.core.account

import android.content.Context
import com.aiyu.ridelink.core.settings.SettingsRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps Firebase Analytics collection in line with Settings -> In the app -> Usage statistics (on by default). */
@Singleton
class UsageStats @Inject constructor(@ApplicationContext private val context: Context, private val settings: SettingsRepository) {
    fun start() {
        // A build without google-services.json has no Firebase app and nothing to switch.
        if (FirebaseApp.getApps(context).isEmpty()) return
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.settings.map { it.usageStats }.distinctUntilChanged().collect {
                FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(it)
            }
        }
    }
}
