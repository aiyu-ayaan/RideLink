package com.aiyu.ridelink.core.notify

import com.aiyu.ridelink.core.notify.MessageAlert.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageAlertTest {
    private val chat = MessageAlert(Source.WHATSAPP, "WhatsApp", "Mom", "Where are you?")

    @Test fun mutedAppIsDropped() {
        assertTrue(chat.allowed(emptySet(), groups = true))
        assertFalse(chat.allowed(setOf("whatsapp"), groups = true))
        assertTrue(chat.allowed(setOf("sms"), groups = true))
    }

    @Test fun groupsCanBeHidden() {
        val group = chat.copy(group = true)
        assertFalse(group.allowed(emptySet(), groups = false))
        assertTrue(chat.allowed(emptySet(), groups = false))
    }

    @Test fun textCanBeHidden() {
        assertEquals("WhatsApp Mom" to "Where are you", chat.lines(showText = true))
        assertEquals("WhatsApp Mom" to "", chat.lines(showText = false))
    }
}
