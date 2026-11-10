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

    @Test fun noBreakSpaces() {
        val step = MapsNotificationParser.parse("300 m", "Turn left onto MG Road", "12 min · 4.5 km · 10:42 ETA")!!
        assertEquals(300, step.distanceToTurnM)
        assertEquals(Maneuver.LEFT, step.maneuver)
        assertEquals(720L, step.etaSeconds)
        assertEquals(4500, step.distanceLeftM)
    }

    @Test fun keywords() {
        assertEquals(Maneuver.SLIGHT_LEFT, MapsNotificationParser.maneuverOf("Slight left toward NH 48"))
        assertEquals(Maneuver.ROUNDABOUT, MapsNotificationParser.maneuverOf("At the roundabout, take the 2nd exit"))
        assertEquals(Maneuver.FORK_RIGHT, MapsNotificationParser.maneuverOf("Keep right at the fork"))
        assertEquals(Maneuver.STRAIGHT, MapsNotificationParser.maneuverOf("Continue onto Ring Rd"))
    }

    /** Fields of a real Maps 26.14 navigation notification: no direction in any text; it is in the large icon. */
    @Test fun currentMapsTakesTheTurnFromTheIcon() {
        val noIcon = MapsNotificationParser.parse("40 m", "Mohan Khoper Rd", "17 min · 7.8 km · 11:27 PM ETA")!!
        assertEquals(Maneuver.STRAIGHT, noIcon.maneuver)

        val step = MapsNotificationParser.parse("40 m", "Mohan Khoper Rd", "17 min · 7.8 km · 11:27 PM ETA", Maneuver.SHARP_LEFT)!!
        assertEquals(Maneuver.SHARP_LEFT, step.maneuver)
        assertEquals(40, step.distanceToTurnM)
        assertEquals("Mohan Khoper Rd", step.instruction)
        assertEquals(1_020L, step.etaSeconds)
        assertEquals(7_800, step.distanceLeftM)
    }

    @Test fun departureWithoutExplicitDistanceIsParsed() {
        val step = MapsNotificationParser.parse("Head north on 2nd Cross Rd", null, "Arrive 10:17 PM")!!
        assertEquals(0, step.distanceToTurnM)
        assertEquals("Head north on 2nd Cross Rd", step.instruction)
        assertEquals(Maneuver.STRAIGHT, step.maneuver)

        val stepWest = MapsNotificationParser.parse("Head west", null, "Arrive 10:15 PM")!!
        assertEquals(0, stepWest.distanceToTurnM)
        assertEquals(Maneuver.STRAIGHT, stepWest.maneuver)
    }

    @Test fun nonNavigationNotificationIsIgnored() {
        assertNull(MapsNotificationParser.parse("Location sharing", "You are sharing", null))
    }

    @Test fun directionInOtherFieldAndInPrefix() {
        val step = MapsNotificationParser.parse("In 300 m", "Main St", "5 min · 2 km")!!
        assertEquals(300, step.distanceToTurnM)
        assertEquals("Main St", step.instruction)

        val s2 = MapsNotificationParser.parse("Turn left", "Main St · 200 m", null)!!
        assertEquals(200, s2.distanceToTurnM)
        assertEquals(Maneuver.LEFT, s2.maneuver)

        // Street alone in one field, direction next to the distance in the other.
        val s3 = MapsNotificationParser.parse("150 m · Turn right", "MG Road", null)!!
        assertEquals(150, s3.distanceToTurnM)
        assertEquals(Maneuver.RIGHT, s3.maneuver)

        // Single field: distance stripped, rest is the instruction.
        val s4 = MapsNotificationParser.parse("Then 50 m, keep left", null, null)!!
        assertEquals(50, s4.distanceToTurnM)
        assertEquals("keep left", s4.instruction)
        assertEquals(Maneuver.FORK_LEFT, s4.maneuver)

        // Explicit straight is not overridden by a street name with "Left" in it.
        assertEquals(Maneuver.STRAIGHT, MapsNotificationParser.parse("Continue onto Ring Rd", "400 m · Left Bank", null)!!.maneuver)

        // Numbers inside road names are not distances.
        assertNull(MapsNotificationParser.parse("NH48 bypass", "Sector 15", null))

        assertEquals(Maneuver.SLIGHT_RIGHT, MapsNotificationParser.maneuverOf("Bear right onto NH 48"))
        assertEquals(Maneuver.FORK_LEFT, MapsNotificationParser.maneuverOf("Take the ramp on the left"))
    }
}
