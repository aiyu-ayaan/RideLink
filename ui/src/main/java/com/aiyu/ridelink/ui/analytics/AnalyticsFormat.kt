package com.aiyu.ridelink.ui.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Number and date formatting shared by the Analytics screen; locale-aware, no units baked into strings. */
internal object Fmt {
    private val zone get() = ZoneId.systemDefault()
    private val dayShort = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val dayNum = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val month = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
    private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun km(v: Double) = if (v >= 100) "%,d".format(v.roundToInt()) else "%.1f".format(v)
    fun one(v: Double) = "%.1f".format(v)
    fun whole(v: Double) = "%,d".format(v.roundToInt())
    fun percent(change: Double) = "%+d%%".format((change * 100).roundToInt())

    /** "2 h 10 min" / "24 min" / "40 s". */
    fun duration(seconds: Long): String = when {
        seconds >= 3_600 -> "${seconds / 3_600} h ${(seconds % 3_600) / 60} min"
        seconds >= 60 -> "${seconds / 60} min"
        else -> "$seconds s"
    }

    fun minutes(m: Double) = duration((m * 60).roundToInt().toLong())

    fun day(d: LocalDate): String = d.format(dayShort)
    fun dayNumber(d: LocalDate): String = d.format(dayNum)
    fun monthShort(d: LocalDate): String = d.format(month)
    fun monthYear(d: LocalDate): String = d.format(monthYear)
    fun weekday(d: LocalDate): String = d.format(weekday)

    fun dateTime(ms: Long): String {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return "${t.toLocalDate().format(dayShort)} · ${t.toLocalTime().format(clock)}"
    }

    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().format(clock)

    /** Axis tick: no decimals once the step is whole. */
    fun tick(v: Double) = if (abs(v - v.roundToInt()) < 0.001) "%,d".format(v.roundToInt()) else "%.1f".format(v)
}
