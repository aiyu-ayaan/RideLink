package com.aiyu.ridelink.core.notify

import com.aiyu.ridelink.core.protocol.ClusterProtocol.SocialCounts

/**
 * Chat apps whose unread counts the display shows, as the stock NotificationListenerService switches on them
 * (package switch, around line 740 of the decompile). Counting is per new message (RideNotificationListener).
 */
object SocialNotificationParser {
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val FACEBOOK = "com.facebook.katana"
    const val MESSENGER = "com.facebook.orca"
    const val INSTAGRAM = "com.instagram.android"

    enum class App { WHATSAPP, FACEBOOK, INSTAGRAM }

    fun app(packageName: String): App? = when (packageName) {
        WHATSAPP, WHATSAPP_BUSINESS -> App.WHATSAPP
        FACEBOOK, MESSENGER -> App.FACEBOOK
        INSTAGRAM -> App.INSTAGRAM
        else -> null
    }

    /** Unread counts from the chats waiting in the notification shade: each pair is an app and that chat's messages. */
    fun counts(chats: List<Pair<App, Int>>): SocialCounts {
        fun messages(app: App) = chats.filter { it.first == app }.sumOf { it.second.coerceAtLeast(1) }
        return SocialCounts(
            whatsappMessages = messages(App.WHATSAPP),
            whatsappChats = chats.count { it.first == App.WHATSAPP },
            facebookMessages = messages(App.FACEBOOK),
            instagram = messages(App.INSTAGRAM),
        )
    }
}
