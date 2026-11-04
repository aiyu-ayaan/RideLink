package com.aiyu.ridelink.core.nav

import com.aiyu.ridelink.core.protocol.Maneuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapsNotificationParserTest {
    @Test fun distanceInTitle() {
        val step = MapsNotificationParser.parse("300 m", "Turn right onto MG Road", "12 min · 4.5 km · 10:42 ETA")!!
        assertEquals(300, step.distanceToTurnM)
        assertEquals("Turn right onto MG Road", step.instruction)
        assertEquals(Maneuver.RIGHT, step.maneuver)
        assertEquals(720L, step.etaSeconds)
        assertEquals(4500, step.distanceLeftM)
    }

    @Test fun distanceInText() {
        val step = MapsNotificationParser.parse("Make a U-turn", "1.2 km", "1 hr 5 min · 52 km")!!
        assertEquals(1200, step.distanceToTurnM)
        assertEquals(Maneuver.U_TURN, step.maneuver)
        assertEquals(3900L, step.etaSeconds)
        assertEquals(52_000, step.distanceLeftM)
    }

    @Test fun keywords() {
        assertEquals(Maneuver.SLIGHT_LEFT, MapsNotificationParser.maneuverOf("Slight left toward NH 48"))
        assertEquals(Maneuver.ROUNDABOUT, MapsNotificationParser.maneuverOf("At the roundabout, take the 2nd exit"))
        assertEquals(Maneuver.FORK_RIGHT, MapsNotificationParser.maneuverOf("Keep right at the fork"))
        assertEquals(Maneuver.STRAIGHT, MapsNotificationParser.maneuverOf("Continue onto Ring Rd"))
    }

    @Test fun nonNavigationNotificationIsIgnored() {
        assertNull(MapsNotificationParser.parse("Location sharing", "You are sharing", null))
    }

    @Test fun directionInOtherFieldAndInPrefix() {
        val step = MapsNotificationParser.parse("In 300 m", "Main St", "5 min · 2 km")
        assertEquals(300, step!!.distanceToTurnM)
        val s2 = MapsNotificationParser.parse("Turn left", "Main St · 200 m", null)
        assertEquals(Maneuver.LEFT, s2!!.maneuver)
        assertEquals(Maneuver.SLIGHT_RIGHT, MapsNotificationParser.maneuverOf("Bear right onto NH 48"))
        assertEquals(Maneuver.FORK_LEFT, MapsNotificationParser.maneuverOf("Take the ramp on the left"))
    }
}
