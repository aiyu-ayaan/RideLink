package com.aiyu.ridelink.core.notify

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
}
