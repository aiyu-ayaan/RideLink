package com.aiyu.ridelink.core.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class RejectMessageTest {
    @Test fun blankFallsBackToDefault() {
        assertEquals(RejectMessage.DEFAULT, RejectMessage.text("   "))
    }

    @Test fun customTextIsTrimmedAndCapped() {
        assertEquals("On the scooter", RejectMessage.text("  On the scooter "))
        assertEquals(160, RejectMessage.text("x".repeat(200)).length)
    }
}
