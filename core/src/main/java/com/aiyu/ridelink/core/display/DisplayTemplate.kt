package com.aiyu.ridelink.core.display

/**
 * Live text for the display's custom lines. A template mixes plain text with `{token}` placeholders;
 * `{a|b|c}` uses the first token that has a value, so `{caller|rider}` shows the caller while ringing and
 * the rider name otherwise. Unknown or empty tokens render as nothing; spaces left behind are collapsed.
 */
object DisplayTemplate {
    /** Every token RideLink fills in, in the order Settings offers them. */
    enum class Token(val key: String) {
        RIDER("rider"), CALLER("caller"), TIME("time"), DATE("date"), BATTERY("battery"),
        TEMP("temp"), WEATHER("weather"), AQI("aqi"), PLACE("place"),
        SONG("song"), ARTIST("artist"),
        SPEED("speed"), ODO("odo"), TRIP("trip"), RANGE("range"), FUEL("fuel"), MILEAGE("mileage"),
        WHATSAPP("whatsapp"), MISSED("missed"),
    }

    private val placeholder = Regex("""\{([a-z|]+)\}""")
    private val spaces = Regex(""" {2,}""")

    fun render(template: String, values: Map<Token, String?>): String {
        val byKey = values.entries.associate { (k, v) -> k.key to v?.trim().orEmpty() }
        return placeholder.replace(template) { m ->
            m.groupValues[1].split('|').firstNotNullOfOrNull { byKey[it]?.takeIf(String::isNotEmpty) }.orEmpty()
        }.replace(spaces, " ").trim()
    }

    /** True when the template has any placeholder, so it must be re-rendered as values change. */
    fun isDynamic(template: String) = placeholder.containsMatchIn(template)
}
