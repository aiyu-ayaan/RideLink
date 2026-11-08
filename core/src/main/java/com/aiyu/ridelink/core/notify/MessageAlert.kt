package com.aiyu.ridelink.core.notify

import com.aiyu.ridelink.core.protocol.ClusterProtocol

/** A message for the display's pop-up, and the rider's filters for it (Settings -> On display -> Messages). */
data class MessageAlert(
    val source: Source,
    val app: String,
    val sender: String,
    val text: String,
    val group: Boolean = false,
) {
    /** Filter keys, saved by [key] in Settings.messageMuted. */
    enum class Source(val key: String) { WHATSAPP("whatsapp"), INSTAGRAM("instagram"), FACEBOOK("facebook"), SMS("sms"), OTHER("other"), DELIVERY("delivery"), CALL("call") }

    fun allowed(muted: Set<String>, groups: Boolean): Boolean = source.key !in muted && (groups || !group)

    /** Line 1 app + sender, line 2 the message, or blank when the rider hides message text. */
    fun lines(showText: Boolean): Pair<String, String> =
        ClusterProtocol.plainText("$app $sender") to if (showText) ClusterProtocol.plainText(text) else ""

    companion object {
        fun source(app: SocialNotificationParser.App): Source = when (app) {
            SocialNotificationParser.App.WHATSAPP -> Source.WHATSAPP
            SocialNotificationParser.App.FACEBOOK -> Source.FACEBOOK
            SocialNotificationParser.App.INSTAGRAM -> Source.INSTAGRAM
        }
    }
}
