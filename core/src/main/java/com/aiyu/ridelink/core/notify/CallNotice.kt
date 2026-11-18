package com.aiyu.ridelink.core.notify

/** Tells a ringing call from one being dialled or already answered, from what its notification carries. */
object CallNotice {
    /** Notification.CallStyle.CALL_TYPE_INCOMING. */
    private const val TYPE_INCOMING = 1

    /** Notification.FLAG_FSI_REQUESTED_BUT_DENIED (hidden): Android 14+ removed the full-screen intent the app asked for. */
    private const val FLAG_FULL_SCREEN_DENIED = 0x4000

    /**
     * CallStyle apps state the type ([callType] above 0). Others ring with a full-screen intent. A missing call timer
     * proves nothing: a call being dialled has no timer yet either.
     */
    fun isRinging(callType: Int, fullScreen: Boolean, flags: Int): Boolean =
        if (callType > 0) callType == TYPE_INCOMING else fullScreen || flags and FLAG_FULL_SCREEN_DENIED != 0
}
