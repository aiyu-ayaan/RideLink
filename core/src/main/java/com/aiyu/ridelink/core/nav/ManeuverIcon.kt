package com.aiyu.ridelink.core.nav

import com.aiyu.ridelink.core.protocol.Maneuver
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Google Maps puts the turn direction only in the navigation notification's large icon: the title is the
 * distance ("40 m"), the text the street ("Mohan Khoper Rd"), no "Turn left" anywhere (Maps 26.14, 2026-11-05).
 * The icon is one of Maps' own `maneuver_*` vector drawables, so it is matched against those, drawn from the
 * installed Maps app at runtime (MapsTurnIcons); nothing from Maps ships with RideLink.
 *
 * Shapes are compared on a [GRID] x [GRID] ink grid cropped to the drawing, so size, padding and colour do not
 * matter. Checked offline against all 65 Maps maneuver icons at 48 and 138 px: every one matched its own turn
 * type, real icons scored 0.98 or more and the next other turn type was at least 0.21 lower.
 */
object ManeuverIcon {
    const val GRID = 24

    /** Below this cosine similarity the icon is not a turn arrow we know; the caller falls back to the text. */
    const val MIN_SCORE = 0.85f

    class Template(val name: String, val maneuver: Maneuver, val grid: FloatArray)

    data class Match(val maneuver: Maneuver, val name: String, val score: Float)

    /**
     * ARGB pixels to a [GRID]² grid of ink (0..1), cropped to the square around the drawing. Ink is the alpha on a
     * transparent background, else the distance from the corner colour. Null when nothing is drawn.
     */
    fun grid(argb: IntArray, width: Int, height: Int): FloatArray? {
        if (width <= 0 || height <= 0 || argb.size < width * height) return null
        val bg = argb[0]
        val transparent = (bg ushr 24) < 128
        val ink = FloatArray(width * height) { i ->
            val p = argb[i]
            if (transparent) (p ushr 24) / 255f
            else max(max(abs(ch(p, 16) - ch(bg, 16)), abs(ch(p, 8) - ch(bg, 8))), abs(ch(p, 0) - ch(bg, 0))) / 255f
        }
        var x0 = width; var y0 = height; var x1 = -1; var y1 = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (ink[y * width + x] > 0.2f) {
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
            }
        }
        if (x1 < 0) return null
        val side = max(x1 - x0 + 1, y1 - y0 + 1).toFloat()
        val sx = (x0 + x1 + 1) / 2f - side / 2
        val sy = (y0 + y1 + 1) / 2f - side / 2
        val cell = side / GRID
        val out = FloatArray(GRID * GRID)
        for (gy in 0 until GRID) for (gx in 0 until GRID) {
            val ax = floor(sx + gx * cell).toInt(); val bx = ceil(sx + (gx + 1) * cell).toInt()
            val ay = floor(sy + gy * cell).toInt(); val by = ceil(sy + (gy + 1) * cell).toInt()
            var sum = 0f
            for (y in ay until by) for (x in ax until bx) if (x in 0 until width && y in 0 until height) sum += ink[y * width + x]
            out[gy * GRID + gx] = sum / max(1, (bx - ax) * (by - ay))
        }
        return out
    }

    fun similarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0f || nb == 0f) 0f else dot / sqrt(na * nb)
    }

    fun match(icon: FloatArray, templates: List<Template>): Match? =
        templates.map { Match(it.maneuver, it.name, similarity(icon, it.grid)) }
            .maxByOrNull { it.score }
            ?.takeIf { it.score >= MIN_SCORE }

    /** Maps drawable name to the turn types the stock pictogram tables know. Merges and depart count as straight. */
    fun maneuverFor(name: String): Maneuver {
        val left = name.endsWith("left")
        return when {
            "roundabout" in name -> roundaboutExit(name)
            "destination" in name -> Maneuver.DESTINATION
            "u_turn" in name -> Maneuver.U_TURN
            "fork" in name || "keep" in name -> if (left) Maneuver.FORK_LEFT else Maneuver.FORK_RIGHT
            "sharp" in name -> if (left) Maneuver.SHARP_LEFT else Maneuver.SHARP_RIGHT
            "slight" in name -> if (left) Maneuver.SLIGHT_LEFT else Maneuver.SLIGHT_RIGHT
            "normal" in name -> if (left) Maneuver.LEFT else Maneuver.RIGHT
            else -> Maneuver.STRAIGHT
        }
    }

    /**
     * Exit of a `maneuver_roundabout_enter_and_exit_*` drawable. Maps draws India's roundabouts clockwise (`_cw_`);
     * a U-turn goes almost all the way round, like the sharp right exit. Enter / exit only icons are the plain roundabout.
     */
    private fun roundaboutExit(name: String): Maneuver = when {
        "u_turn" in name -> Maneuver.ROUNDABOUT_SHARP_RIGHT
        "sharp_left" in name -> Maneuver.ROUNDABOUT_SHARP_LEFT
        "slight_left" in name -> Maneuver.ROUNDABOUT_SLIGHT_LEFT
        "normal_left" in name -> Maneuver.ROUNDABOUT_LEFT
        "sharp_right" in name -> Maneuver.ROUNDABOUT_SHARP_RIGHT
        "slight_right" in name -> Maneuver.ROUNDABOUT_SLIGHT_RIGHT
        "normal_right" in name -> Maneuver.ROUNDABOUT_RIGHT
        else -> Maneuver.ROUNDABOUT
    }

    private val SIDES = listOf("left", "right")

    /** The `maneuver_*` drawables in Google Maps 26.14 (aapt2 dump resources). */
    val MAPS_DRAWABLES: List<String> = buildList {
        addAll(
            listOf(
                "depart", "destination", "destination_left", "destination_right", "destination_straight",
                "fork_left", "fork_right", "keep_left", "keep_right", "merge", "merge_left", "merge_right",
                "name_change", "straight", "u_turn_left", "u_turn_right",
                "roundabout_enter_ccw", "roundabout_enter_cw", "roundabout_exit_ccw", "roundabout_exit_cw",
            ),
        )
        for (kind in listOf("normal", "sharp", "slight")) SIDES.forEach { add("turn_${kind}_$it") }
        for (ramp in listOf("on_ramp", "off_ramp")) for (kind in listOf("keep", "normal", "sharp", "slight", "u_turn")) {
            SIDES.forEach { add("${ramp}_${kind}_$it") }
        }
        for (dir in listOf("ccw", "cw")) {
            add("roundabout_enter_and_exit_$dir")
            add("roundabout_enter_and_exit_${dir}_straight")
            add("roundabout_enter_and_exit_${dir}_u_turn")
            for (kind in listOf("normal", "sharp", "slight")) SIDES.forEach { add("roundabout_enter_and_exit_${dir}_${kind}_$it") }
        }
    }.map { "maneuver_$it" }

    private fun ch(p: Int, shift: Int) = (p ushr shift) and 0xFF
}
