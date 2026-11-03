package com.aiyu.ridelink.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.nav.MapsNotificationParser
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Reads Google Maps directions, incoming calls and chat senders from notifications. */
@AndroidEntryPoint
class RideNotificationListener : NotificationListenerService() {
    @Inject lateinit var controller: ClusterController

    private var lastNavKey: String? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        when {
            sbn.packageName == MapsNotificationParser.MAPS_PACKAGE -> {
                val step = MapsNotificationParser.parse(title, text, extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()) ?: return
                lastNavKey = sbn.key
                controller.onNavigation(step)
            }
            n.category == Notification.CATEGORY_CALL && isIncoming(n) -> controller.onIncomingCall(title ?: text ?: return)
            n.category == Notification.CATEGORY_MESSAGE && sbn.packageName in CHAT_APPS && title != null -> controller.onMessage(title)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        when {
            sbn.key == lastNavKey -> {
                lastNavKey = null
                controller.onNavigationEnded()
            }
            sbn.notification.category == Notification.CATEGORY_CALL -> controller.onCallEnded()
        }
    }

    /** Ringing calls carry CallStyle type INCOMING (API 31+) or a full-screen intent on older dialers. */
    private fun isIncoming(n: Notification): Boolean {
        val type = n.extras.getInt(Notification.EXTRA_CALL_TYPE, -1)
        return if (type != -1) type == Notification.CallStyle.CALL_TYPE_INCOMING else n.fullScreenIntent != null
    }

    private companion object {
        val CHAT_APPS = setOf("com.whatsapp", "com.whatsapp.w4b", "com.google.android.apps.messaging", "org.telegram.messenger")
    }
}
