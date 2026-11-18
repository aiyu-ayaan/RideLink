package com.aiyu.ridelink.core.notify

import com.aiyu.ridelink.core.notify.SocialNotificationParser.App
import com.aiyu.ridelink.core.protocol.ClusterProtocol.SocialCounts
import org.junit.Assert.assertEquals
import org.junit.Test

class SocialNotificationParserTest {
    @Test fun packagesMapToApps() {
        assertEquals(App.WHATSAPP, SocialNotificationParser.app("com.whatsapp.w4b"))
        assertEquals(App.FACEBOOK, SocialNotificationParser.app("com.facebook.orca"))
        assertEquals(App.INSTAGRAM, SocialNotificationParser.app("com.instagram.android"))
        assertEquals(null, SocialNotificationParser.app("org.telegram.messenger"))
    }

    @Test fun shadeCountsMessagesPerAppAndAtLeastOnePerChat() {
        val shade = listOf(App.WHATSAPP to 3, App.WHATSAPP to 0, App.FACEBOOK to 2, App.INSTAGRAM to 0)
        assertEquals(SocialCounts(whatsappMessages = 4, whatsappChats = 2, facebookMessages = 2, instagram = 1), SocialNotificationParser.counts(shade))
    }
}
