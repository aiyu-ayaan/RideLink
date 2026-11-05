package com.aiyu.ridelink.core.nav

import com.aiyu.ridelink.core.protocol.Maneuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManeuverIconTest {
    /** A turn arrow on a 24-unit canvas: stem up from the bottom, then a bar and a head to one side. */
    private fun arrow(size: Int, right: Boolean, ink: Int, bg: Int, pad: Int = 0): IntArray {
        val s = size - 2 * pad
        val px = IntArray(size * size) { bg }
        fun fill(x0: Float, y0: Float, x1: Float, y1: Float) {
            for (y in (pad + y0 * s / 24).toInt() until (pad + y1 * s / 24).toInt())
                for (x in (pad + x0 * s / 24).toInt() until (pad + x1 * s / 24).toInt()) {
                    val mx = if (right) x else size - 1 - x
                    px[y * size + mx] = ink
                }
        }
        fill(3f, 9f, 6f, 22f) // stem
        fill(3f, 9f, 18f, 12f) // bar
        fill(15f, 5f, 18f, 16f) // head
        fill(18f, 8f, 21f, 13f) // tip
        return px
    }

    private fun grid(px: IntArray, size: Int) = ManeuverIcon.grid(px, size, size)!!

    private val templates = listOf(
        ManeuverIcon.Template("maneuver_turn_normal_right", Maneuver.RIGHT, grid(arrow(96, true, 0xFF000000.toInt(), 0), 96)),
        ManeuverIcon.Template("maneuver_turn_normal_left", Maneuver.LEFT, grid(arrow(96, false, 0xFF000000.toInt(), 0), 96)),
    )

    @Test fun matchesAcrossSizePaddingAndColour() {
        // Notification style: white arrow on transparent, smaller, with padding.
        val right = ManeuverIcon.match(grid(arrow(138, true, 0xFFFFFFFF.toInt(), 0, pad = 20), 138), templates)!!
        assertEquals(Maneuver.RIGHT, right.maneuver)
        // Opaque green background, white arrow.
        val left = ManeuverIcon.match(grid(arrow(48, false, 0xFFFFFFFF.toInt(), 0xFF00654F.toInt()), 48), templates)!!
        assertEquals(Maneuver.LEFT, left.maneuver)
    }

    @Test fun blankOrUnknownShapeIsNotMatched() {
        assertNull(ManeuverIcon.grid(IntArray(64 * 64), 64, 64))
        val disc = IntArray(64 * 64) { i -> val x = i % 64 - 32; val y = i / 64 - 32; if (x * x + y * y < 400) -1 else 0 }
        assertNull(ManeuverIcon.match(grid(disc, 64), templates))
    }

    @Test fun mapsDrawableNames() {
        assertEquals(64, ManeuverIcon.MAPS_DRAWABLES.size)
        assertEquals(Maneuver.SHARP_LEFT, ManeuverIcon.maneuverFor("maneuver_turn_sharp_left"))
        assertEquals(Maneuver.FORK_RIGHT, ManeuverIcon.maneuverFor("maneuver_on_ramp_keep_right"))
        assertEquals(Maneuver.U_TURN, ManeuverIcon.maneuverFor("maneuver_off_ramp_u_turn_left"))
        assertEquals(Maneuver.ROUNDABOUT, ManeuverIcon.maneuverFor("maneuver_roundabout_enter_and_exit_cw_slight_left"))
        assertEquals(Maneuver.DESTINATION, ManeuverIcon.maneuverFor("maneuver_destination_right"))
        assertEquals(Maneuver.STRAIGHT, ManeuverIcon.maneuverFor("maneuver_merge_left"))
    }
}
