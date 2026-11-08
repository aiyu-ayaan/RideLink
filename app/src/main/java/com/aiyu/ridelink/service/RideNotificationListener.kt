package com.aiyu.ridelink.service

import android.app.Notification
import android.app.Person
import android.app.RemoteInput
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.content.ComponentName
import android.os.Build
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.nav.MapsNotificationParser
import com.aiyu.ridelink.core.nav.MapsTurnIcons
import com.aiyu.ridelink.core.notify.MessageAlert
import com.aiyu.ridelink.core.notify.SocialNotificationParser
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
            SocialNotificationParser.app(sbn.packageName) != null -> {
                val app = SocialNotificationParser.app(sbn.packageName)!!
                if (isNewMessage(sbn)) {
                    noteOnce("Chat: ${sbn.packageName} title=$title")
                    controller.onChatPosted(app, chatsInShade(app))
                    showMessage(sbn, MessageAlert.source(app))
                }
                if (app == SocialNotificationParser.App.WHATSAPP) offerChatReply(sbn)
            }
            // Delivery / shopping pop-up, once per order update.
            sbn.packageName in DELIVERY_APPS && (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 -> {
                val id = "${sbn.key}|${n.`when`}"
                if (id != lastDelivery) {
                    lastDelivery = id
                    controller.onDelivery(food = sbn.packageName in FOOD_APPS)
                }
            }
            // SMS sender on the SmartXonnect display; each message once (the SMS app reposts and groups).
            sbn.packageName == smsPackage && (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 && !title.isNullOrBlank() -> {
                val id = "${sbn.key}|${n.`when`}"
                if (id != lastSms) {
                    lastSms = id
                    controller.onSms(title)
                    showMessage(sbn, MessageAlert.Source.SMS)
                }
            }
            // Any other chat app (Telegram, Signal, Messenger Lite, Google Chat...): the message on the display.
            sbn.packageName != packageName && isChat(n) && isNewMessage(sbn) -> showMessage(sbn, MessageAlert.Source.OTHER)
        }
    }

    /**
     * WhatsApp auto-reply while riding: one-to-one chats only (group conversations are skipped), through the
     * notification's own reply action (RemoteInput), the same way a watch or Android Auto replies.
     */
    private fun offerChatReply(sbn: StatusBarNotification) {
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0 || n.category == Notification.CATEGORY_CALL) return
        val extras = n.extras
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)) return
        val chat = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString()?.takeIf { it.isNotBlank() && it != "WhatsApp" } ?: return
        val action = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true } ?: return
        controller.onChatMessage("${sbn.packageName}|${n.shortcutId ?: chat}") { text -> reply(action, text) }
    }

    private fun reply(action: Notification.Action, text: String): Boolean = runCatching {
        val inputs = action.remoteInputs
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        val fill = Intent()
        RemoteInput.addResultsToIntent(inputs, fill, results)
        action.actionIntent.send(this, 0, fill)
    }.isSuccess

    private var lastSms: String? = null
    private var lastDelivery: String? = null
    private val smsPackage: String? by lazy { runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull() }

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
        seenMessages.remove(sbn.key)
        when {
            sbn.key == lastNavKey -> {
                lastNavKey = null
                controller.onNavigationEnded()
            }
            sbn.notification.category == Notification.CATEGORY_CALL -> controller.onCallEnded()
            sbn.packageName == smsPackage -> controller.onSmsRead()
            SocialNotificationParser.app(sbn.packageName) != null -> {
                val app = SocialNotificationParser.app(sbn.packageName)!!
                if (chatsInShade(app) == 0) controller.onChatCleared(app)
            }
        }
    }

    private var music: MusicWatcher? = null

    override fun onListenerConnected() {
        // A route started before the listener (re)bound is already in the shade; pick it up now.
        runCatching { activeNotifications }.getOrNull()
            ?.filter { it.packageName == MapsNotificationParser.MAPS_PACKAGE }
            ?.forEach(::onNotificationPosted)
        // Chats already in the shade are not new; only messages from now on raise the badge (stock clears on start).
        runCatching { activeNotifications }.getOrNull()
            ?.filter { SocialNotificationParser.app(it.packageName) != null }
            ?.forEach(::isNewMessage)
        music = MusicWatcher(this, ComponentName(this, RideNotificationListener::class.java), controller).also { it.start() }
    }

    override fun onListenerDisconnected() {
        music?.stop()
        music = null
    }

    /** Last `when` + text per notification key: chat apps repost every chat on each message, only a change is new. */
    private val seenMessages = HashMap<String, String>()

    /**
     * A real new message: not a group summary or an ongoing notice (WhatsApp Web, backup), and not a silent repost.
     * Stock does the same per key + text (NotificationReceiverService.previousKey).
     */
    private fun isNewMessage(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if ((n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT)) != 0) return false
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return false
        val id = "${n.`when`}|$text"
        return seenMessages.put(sbn.key, id) != id
    }

    private fun isChat(n: Notification) =
        n.category == Notification.CATEGORY_MESSAGE || n.extras.containsKey(Notification.EXTRA_MESSAGES)

    /** App, sender and text of a message notification, for the display's custom lines. */
    private fun showMessage(sbn: StatusBarNotification, source: MessageAlert.Source) {
        val extras = sbn.notification.extras
        val sender = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))
            ?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val group = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
        controller.onMessage(MessageAlert(source, appName(sbn), sender, text, group))
    }

    /**
     * The posting app's name. The builder stores its ApplicationInfo in the extras ("android.appInfo"), which avoids
     * the package-visibility rules a PackageManager lookup runs into on Android 11+.
     */
    private fun appName(sbn: StatusBarNotification): String = runCatching {
        @Suppress("DEPRECATION")
        val info = sbn.notification.extras.getParcelable<ApplicationInfo>("android.appInfo")
            ?: packageManager.getApplicationInfo(sbn.packageName, 0)
        info.loadLabel(packageManager).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: sbn.packageName.substringAfterLast('.')

    /** Chat notifications of [app] still in the shade (summaries left out). */
    private fun chatsInShade(app: SocialNotificationParser.App): Int =
        (runCatching { activeNotifications }.getOrNull() ?: return 0).count {
            SocialNotificationParser.app(it.packageName) == app &&
                (it.notification.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT)) == 0
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

    private companion object {
        /** Swiggy, Zomato, Blinkit, Zepto, Swiggy Instamart (same app), Dunzo. */
        val FOOD_APPS = setOf("in.swiggy.android", "com.application.zomato", "com.grofers.customerapp", "com.zeptoconsumerapp", "com.dunzo.user")
        /** Food apps plus Amazon and Flipkart ("Order", as stock N251HelperService.sendSocialmediaNotification). */
        val DELIVERY_APPS = FOOD_APPS + setOf("in.amazon.mShop.android.shopping", "com.flipkart.android")
    }
}
