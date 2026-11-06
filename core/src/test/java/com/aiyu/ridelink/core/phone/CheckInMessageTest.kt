package com.aiyu.ridelink.core.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInMessageTest {
    @Test fun startWithLocation() {
        assertEquals(
            "Started a ride on the scooter. Starting point: https://maps.google.com/?q=12.971600,77.594600",
            CheckInMessage.start(12.9716, 77.5946),
        )
    }

    @Test fun endWithDistanceAndWithoutLocation() {
        assertEquals("Ride ended after 12.4 km.", CheckInMessage.end(null, null, 12.4))
        assertEquals("Ride ended. I'm here: https://maps.google.com/?q=1.000000,2.000000", CheckInMessage.end(1.0, 2.0, null))
    }
}
