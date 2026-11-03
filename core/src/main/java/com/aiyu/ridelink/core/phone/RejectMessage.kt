package com.aiyu.ridelink.core.phone

/** Text for "reject with SMS" from the scooter switch. */
object RejectMessage {
    const val DEFAULT = "Riding now, will call you back."

    /** One SMS segment of plain text; blank falls back to [DEFAULT]. */
    const val MAX = 160

    fun text(custom: String): String = custom.trim().ifEmpty { DEFAULT }.take(MAX)
}
