package com.aiyu.ridelink.core.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallNoticeTest {
    @Test fun callStyleTypeDecides() {
        assertTrue(CallNotice.isRinging(callType = 1, fullScreen = false, flags = 0))
        assertFalse(CallNotice.isRinging(callType = 2, fullScreen = true, flags = 0))
        assertFalse(CallNotice.isRinging(callType = 3, fullScreen = false, flags = 0))
    }

    @Test fun plainNotificationRingsOnlyWithFullScreenIntent() {
        assertTrue(CallNotice.isRinging(callType = -1, fullScreen = true, flags = 0))
        assertTrue(CallNotice.isRinging(callType = 0, fullScreen = false, flags = 0x4000))
    }

    @Test fun dialledCallWithoutTimerIsNotRinging() {
        assertFalse(CallNotice.isRinging(callType = -1, fullScreen = false, flags = 0))
    }
}
