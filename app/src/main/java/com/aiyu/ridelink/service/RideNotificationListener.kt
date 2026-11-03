package com.aiyu.ridelink.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.nav.MapsNotificationParser
import com.aiyu.ridelink.core.notify.SocialNotificationParser
import com.aiyu.ridelink.core.protocol.ClusterProtocol
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Reads Google Maps directions, incoming calls, chat unread counts and the playing song. */
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
            SocialNotificationParser.app(sbn.packageName) != null -> recountSocial()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        when {
            sbn.key == lastNavKey -> {
                lastNavKey = null
                controller.onNavigationEnded()
            }
            sbn.notification.category == Notification.CATEGORY_CALL -> controller.onCallEnded()
            SocialNotificationParser.app(sbn.packageName) != null -> recountSocial()
        }
    }

    private var music: MusicWatcher? = null

    override fun onListenerConnected() {
        recountSocial()
        music = MusicWatcher(this, ComponentName(this, RideNotificationListener::class.java), controller).also { it.start() }
    }

    override fun onListenerDisconnected() {
        music?.stop()
        music = null
    }

    /**
     * Rebuilds unread counts from what is still in the shade, so reading a chat clears its count.
     * WhatsApp follows the stock parser (summary "N messages from M chats"); Facebook and Instagram count
     * their visible notifications. shortcut: the stock FB/IG counting rule was not recovered.
     */
    private fun recountSocial() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        var waSummary: SocialNotificationParser.Count? = null
        var waMsgs = 0
        var waChats = 0
        var fb = 0
        var ig = 0
        for (sbn in active) {
            val app = SocialNotificationParser.app(sbn.packageName) ?: continue
            val extras = sbn.notification.extras
            val isSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
            when (app) {
                SocialNotificationParser.App.WHATSAPP -> {
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    val count = SocialNotificationParser.whatsapp(title, text, sbn.tag == null) ?: continue
                    if (title == "WhatsApp") waSummary = count else if (!isSummary) { waMsgs += count.messages; waChats++ }
                }
                SocialNotificationParser.App.FACEBOOK -> if (!isSummary) fb++
                SocialNotificationParser.App.INSTAGRAM -> if (!isSummary) ig++
            }
        }
        val wa = waSummary ?: SocialNotificationParser.Count(waMsgs, waChats)
        controller.onSocialCounts(ClusterProtocol.SocialCounts(wa.messages, wa.chats, fb, ig))
    }

    /** Ringing calls carry CallStyle type INCOMING (API 31+) or a full-screen intent on older dialers. */
    private fun isIncoming(n: Notification): Boolean {
        val type = n.extras.getInt(Notification.EXTRA_CALL_TYPE, -1)
        return if (type != -1) type == Notification.CallStyle.CALL_TYPE_INCOMING else n.fullScreenIntent != null
    }
}
