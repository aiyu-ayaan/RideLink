package com.aiyu.ridelink.core.notify

/**
 * Unread counts from chat notifications, as the stock NotificationListenerService does it
 * (package switch, around line 740 of the decompile).
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

    data class Count(val messages: Int, val chats: Int)

    private val summary = Regex("""^(\d+) messages? from (\d+) chats?$""")
    private val single = Regex("""^(\d+) new messages?$""")

    /**
     * WhatsApp: the group summary (title "WhatsApp") reads "N messages from M chats"; a single chat reads
     * "N new messages". Anything else counts as 1 message in 1 chat. The stock app only counts notifications
     * whose tag is null; others return null.
     */
    fun whatsapp(title: String?, text: String?, tagIsNull: Boolean): Count? {
        if (!tagIsNull) return null
        val t = text?.trim().orEmpty()
        if (title == "WhatsApp") summary.find(t)?.let { return Count(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        single.find(t)?.let { return Count(it.groupValues[1].toInt(), 1) }
        return Count(1, 1)
    }
}
