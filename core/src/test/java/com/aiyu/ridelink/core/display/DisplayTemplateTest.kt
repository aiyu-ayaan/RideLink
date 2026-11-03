package com.aiyu.ridelink.core.display

import com.aiyu.ridelink.core.display.DisplayTemplate.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayTemplateTest {
    private val values = mapOf(
        Token.RIDER to "Ayaan",
        Token.BATTERY to "85",
        Token.TIME to "9 45 PM",
        Token.CALLER to null,
        Token.TEMP to "27C",
    )

    @Test fun plainTextPassesThrough() {
        assertEquals("Ride safe", DisplayTemplate.render("Ride safe", values))
        assertFalse(DisplayTemplate.isDynamic("Ride safe"))
    }

    @Test fun tokensAreFilled() {
        assertEquals("Batt 85 9 45 PM", DisplayTemplate.render("Batt {battery} {time}", values))
        assertTrue(DisplayTemplate.isDynamic("Batt {battery}"))
    }

    @Test fun fallbackPicksFirstValue() {
        assertEquals("Ayaan", DisplayTemplate.render("{caller|rider}", values))
        assertEquals("Mom", DisplayTemplate.render("{caller|rider}", values + (Token.CALLER to "Mom")))
    }

    @Test fun emptyTokensCollapseSpaces() {
        assertEquals("Hi 27C", DisplayTemplate.render("Hi {song} {temp}", values))
        assertEquals("", DisplayTemplate.render("{caller}", values))
    }

    @Test fun unknownTokensRenderEmpty() {
        assertEquals("x", DisplayTemplate.render("x {nope}", values))
    }
}
