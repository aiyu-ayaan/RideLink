package com.aiyu.ridelink.core.notify

import com.aiyu.ridelink.core.notify.SocialNotificationParser.App
import com.aiyu.ridelink.core.notify.SocialNotificationParser.Count
import org.junit.Assert.assertEquals
import org.junit.Test

class SocialNotificationParserTest {
    @Test fun summaryNotification() {
        assertEquals(Count(7, 3), SocialNotificationParser.whatsapp("WhatsApp", "7 messages from 3 chats", tagIsNull = true))
    }

    @Test fun singleChatCount() {
        assertEquals(Count(4, 1), SocialNotificationParser.whatsapp("Mom", "4 new messages", tagIsNull = true))
    }

    @Test fun plainMessageCountsAsOne() {
        assertEquals(Count(1, 1), SocialNotificationParser.whatsapp("Mom", "Where are you?", tagIsNull = true))
    }

    @Test fun taggedNotificationsAreIgnored() {
        assertEquals(null, SocialNotificationParser.whatsapp("WhatsApp", "7 messages from 3 chats", tagIsNull = false))
    }

    @Test fun packagesMapToApps() {
        assertEquals(App.WHATSAPP, SocialNotificationParser.app("com.whatsapp.w4b"))
        assertEquals(App.FACEBOOK, SocialNotificationParser.app("com.facebook.orca"))
        assertEquals(App.INSTAGRAM, SocialNotificationParser.app("com.instagram.android"))
        assertEquals(null, SocialNotificationParser.app("org.telegram.messenger"))
    }
}
