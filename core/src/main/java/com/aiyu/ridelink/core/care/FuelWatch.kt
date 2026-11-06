package com.aiyu.ridelink.core.care

/**
 * Spots a refuel from the display's fuel bars (`5A 10` [6]). A bar is roughly a litre, so a fill shows as a jump
 * of [JUMP_BARS] or more over the last settled level while the scooter stands still. Both directions must hold
 * for [HOLD_MS] before they count, so a slosh on the side stand or a 0 the gauge reports while it wakes up is
 * ignored. [level] starts from the reading saved at the last disconnect, which catches a fill made with the
 * ignition off (the usual case at a pump).
 *
 * shortcut: a top-up of a single bar is not detected; the rider adds it by hand.
 */
class RefuelDetector(private var level: Int?) {
    private var pending: Int? = null
    private var pendingSince = 0L

    /** Last settled fuel level. */
    val baseline: Int? get() = level

    /** Feeds one reading; true once when a refuel is confirmed. */
    fun feed(bars: Int?, standing: Boolean, now: Long): Boolean {
        if (bars == null) return false
        val base = level ?: run { level = bars; return false }
        // Same level, or a one-bar wobble up: nothing to do.
        if (bars == base || bars == base + 1) {
            pending = null
            return false
        }
        if (pending != bars) {
            pending = bars
            pendingSince = now
            return false
        }
        if (now - pendingSince < HOLD_MS) return false
        val refuel = bars >= base + JUMP_BARS && standing
        // A drop settles at once after the hold; a jump settles only when it is taken as a refuel (standing).
        if (bars < base || refuel) {
            level = bars
            pending = null
        }
        return refuel
    }

    companion object {
        const val JUMP_BARS = 2
        const val HOLD_MS = 10_000L
    }
}

/**
 * Low-fuel prompt. Uses the display's distance to empty (`5A 19` [11..12]) when it reports one, else the stock
 * rule from N251ClusterDataReceiver / N360ClusterDataReceiver: fuel bars at or below [LOW_BARS] raise
 * ACTION_LOW_FUEL_ALERT. Fires once, then re-arms after a refuel (range back above the limit + [REARM_KM],
 * or bars above [LOW_BARS] + 1).
 */
class LowFuelWatch(private val limitKm: Int) {
    private var armed = true
    private var since: Long? = null

    fun isLow(rangeKm: Int?, bars: Int?): Boolean = when {
        rangeKm != null && rangeKm > 0 -> rangeKm <= limitKm
        bars != null -> bars <= LOW_BARS
        else -> false
    }

    private fun isRefilled(rangeKm: Int?, bars: Int?): Boolean = when {
        rangeKm != null && rangeKm > 0 -> rangeKm > limitKm + REARM_KM
        bars != null -> bars > LOW_BARS + 1
        else -> false
    }

    /** True once when the level has been low for [HOLD_MS]. */
    fun feed(rangeKm: Int?, bars: Int?, now: Long): Boolean {
        if (isRefilled(rangeKm, bars)) armed = true
        if (!armed || !isLow(rangeKm, bars)) {
            since = null
            return false
        }
        val start = since ?: now.also { since = it }
        if (now - start < HOLD_MS) return false
        armed = false
        since = null
        return true
    }

    companion object {
        const val LOW_BARS = 1
        const val REARM_KM = 15
        const val HOLD_MS = 30_000L
    }
}
