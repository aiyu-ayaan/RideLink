package com.aiyu.ridelink.service

import android.app.Notification
import android.app.Person
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.nav.MapsNotificationParser
import com.aiyu.ridelink.core.nav.MapsTurnIcons
import com.aiyu.ridelink.core.notify.SocialNotificationParser
import com.aiyu.ridelink.core.protocol.ClusterProtocol
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Reads Google Maps directions, incoming calls, chat unread counts and the playing song. */
@AndroidEntryPoint
class RideNotificationListener : NotificationListenerService() {
    @Inject lateinit var controller: ClusterController
    @Inject lateinit var turnIcons: MapsTurnIcons

    private var lastNavKey: String? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        when {
            sbn.packageName == MapsNotificationParser.MAPS_PACKAGE -> {
                val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                // Maps names only the street; the turn direction is in the large icon.
                val turn = runCatching { turnIcons.classify(n.getLargeIcon()) }.getOrNull()
                val step = MapsNotificationParser.parse(title, text, sub, turn?.maneuver)
                if (step == null) {
                    noteOnce("Maps not parsed: title=$title | text=$text | sub=$sub")
                    return
                }
                noteOnce(
                    "Maps: ${step.instruction} = ${step.maneuver} (" +
                        (turn?.let { "icon ${it.name.removePrefix("maneuver_")} %.2f".format(it.score) }
                            ?: "icon not matched, ${turnIcons.templateCount} Maps icons") + ")",
                )
                lastNavKey = sbn.key
                controller.onNavigation(step)
            }
            n.category == Notification.CATEGORY_CALL -> {
                val incoming = isIncoming(n)
                noteOnce("Call ${if (incoming) "ringing" else "not ringing"}: ${sbn.packageName} title=$title | text=$text")
                when {
                    incoming -> controller.onIncomingCall(callerName(n) ?: return)
                    // Answered: the dialer swaps the ringing notification for an ongoing one; stop the caller frames.
                    controller.caller.value != null -> controller.onCallEnded()
                }
            }
            SocialNotificationParser.app(sbn.packageName) != null -> recountSocial()
        }
    }

    private var lastNote: String? = null

    /** Console note, skipped when it repeats (Maps reposts every few seconds). */
    private fun noteOnce(text: String) {
        if (text == lastNote) return
        lastNote = text
        controller.note(text)
    }

    /** CallStyle puts the caller in EXTRA_CALL_PERSON and may leave the title empty. */
    private fun callerName(n: Notification): String? {
        val person = if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            n.extras.getParcelable<Person>(Notification.EXTRA_CALL_PERSON)?.name?.toString()
        } else null
        return n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
            ?: person?.takeIf { it.isNotBlank() }
            ?: n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
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
        // A route started before the listener (re)bound is already in the shade; pick it up now.
        runCatching { activeNotifications }.getOrNull()
            ?.filter { it.packageName == MapsNotificationParser.MAPS_PACKAGE }
            ?.forEach(::onNotificationPosted)
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
            val isSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
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
        noteOnce("Chats: WhatsApp ${wa.messages} messages in ${wa.chats} chats, Facebook $fb, Instagram $ig")
        controller.onSocialCounts(ClusterProtocol.SocialCounts(wa.messages, wa.chats, fb, ig))
    }

    /**
     * Ringing calls carry CallStyle type INCOMING (API 31+). Dialers without CallStyle: a full-screen intent,
     * or no running call timer (an answered call shows a chronometer).
     */
    private fun isIncoming(n: Notification): Boolean {
        val type = n.extras.getInt(Notification.EXTRA_CALL_TYPE, -1)
        if (type != -1) return type == Notification.CallStyle.CALL_TYPE_INCOMING
        return n.fullScreenIntent != null || !n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
    }
}
