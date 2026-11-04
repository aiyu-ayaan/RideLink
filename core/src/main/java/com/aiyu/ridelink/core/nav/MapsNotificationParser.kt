package com.aiyu.ridelink.core.nav

import com.aiyu.ridelink.core.protocol.ClusterProtocol.NavStep
import com.aiyu.ridelink.core.protocol.Maneuver

/**
 * Turns a Google Maps turn-by-turn notification into a [NavStep].
 * Maps puts the distance in one of title/text and the instruction in the other;
 * the sub text holds "12 min · 4.5 km · 10:42 ETA".
 * shortcut: English-only keyword matching, extend [maneuverOf] when riding with another Maps language.
 */
object MapsNotificationParser {
    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    // Anywhere in the field: "300 m", "In 300 m", "Main St · 300 m". Not inside a longer number.
    private val distance = Regex("""(?<![\w.,])(\d+(?:[.,]\d+)?)\s*(km|mi|ft|m)\b""", RegexOption.IGNORE_CASE)
    private val minutes = Regex("""(?:(\d+)\s*h(?:r|our)?s?\s*)?(\d+)\s*min""", RegexOption.IGNORE_CASE)
    private val km = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    private val separators = Regex("""^(?:[\s·•\-–,]|\b(?:in|then)\b)+|[\s·•\-–,]+$""", RegexOption.IGNORE_CASE)
    private val explicitStraight = Regex("""\b(continue|straight|head)\b""", RegexOption.IGNORE_CASE)

    // Maps formats "300 m" / "12 min" with no-break spaces, which Java's \s does not match.
    private val oddSpaces = Regex("[\\u00A0\\u2007\\u202F]")

    fun parse(title: String?, text: String?, subText: String?): NavStep? {
        val sub = subText?.replace(oddSpaces, " ")
        val parts = listOfNotNull(title, text).map { it.replace(oddSpaces, " ").trim() }.filter(String::isNotEmpty)
        val distPart = parts.firstOrNull { distance.containsMatchIn(it) } ?: return null
        val distMatch = distance.find(distPart)!!
        val distRest = distPart.removeRange(distMatch.range).replace(separators, "")
        val instruction = (parts - distPart).firstOrNull() ?: distRest.ifEmpty { return null }
        return NavStep(
            distanceToTurnM = toMeters(distMatch),
            etaSeconds = sub?.let(minutes::find)?.let { m ->
                ((m.groupValues[1].toLongOrNull() ?: 0) * 60 + m.groupValues[2].toLong()) * 60
            } ?: 0,
            distanceLeftM = sub?.let(km::find)?.let(::toMeters) ?: 0,
            maneuver = maneuverOf(instruction).let { m ->
                // Maps sometimes puts only the street in one field and the direction next to the distance.
                if (m != Maneuver.STRAIGHT || explicitStraight.containsMatchIn(instruction)) m
                else maneuverOf("$instruction $distRest")
            },
            instruction = instruction,
        )
    }

    fun maneuverOf(instruction: String): Maneuver {
        val s = instruction.lowercase()
        return when {
            "u-turn" in s || "u turn" in s || "turn around" in s -> Maneuver.U_TURN
            "roundabout" in s -> Maneuver.ROUNDABOUT
            "arrive" in s || "destination" in s -> Maneuver.DESTINATION
            "sharp left" in s -> Maneuver.SHARP_LEFT
            "sharp right" in s -> Maneuver.SHARP_RIGHT
            "bear left" in s -> Maneuver.SLIGHT_LEFT
            "bear right" in s -> Maneuver.SLIGHT_RIGHT
            "slight left" in s -> Maneuver.SLIGHT_LEFT
            "slight right" in s -> Maneuver.SLIGHT_RIGHT
            "keep left" in s || ("fork" in s || "ramp" in s || "exit" in s || "merge" in s) && "left" in s -> Maneuver.FORK_LEFT
            "keep right" in s || ("fork" in s || "ramp" in s || "exit" in s || "merge" in s) && "right" in s -> Maneuver.FORK_RIGHT
            "left" in s -> Maneuver.LEFT
            "right" in s -> Maneuver.RIGHT
            else -> Maneuver.STRAIGHT
        }
    }

    private fun toMeters(m: MatchResult): Int {
        val v = m.groupValues[1].replace(',', '.').toDouble()
        return when (m.groupValues[2].lowercase()) {
            "km" -> v * 1000
            "mi" -> v * 1609.34
            "ft" -> v * 0.3048
            else -> v
        }.toInt()
    }
}
