package com.aiyu.ridelink.core.notify

import com.aiyu.ridelink.core.notify.SocialNotificationParser.App
import org.junit.Assert.assertEquals
import org.junit.Test

class SocialNotificationParserTest {
    @Test fun packagesMapToApps() {
        assertEquals(App.WHATSAPP, SocialNotificationParser.app("com.whatsapp.w4b"))
        assertEquals(App.FACEBOOK, SocialNotificationParser.app("com.facebook.orca"))
        assertEquals(App.INSTAGRAM, SocialNotificationParser.app("com.instagram.android"))
        assertEquals(null, SocialNotificationParser.app("org.telegram.messenger"))
    }
}
