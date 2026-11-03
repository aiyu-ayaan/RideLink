package com.aiyu.ridelink.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DisplayTelemetryTest {
    private fun frame(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test fun speedometer1() {
        // speed 62, odo 0x0030 39 = 12345, fuel 7, avg 41, trip-status 0, top 88, throttle 0, backlight 0,
        // switch 1, 0-60 0, trip 0x00 00 7b = 123, rpm 0x1f40 = 8000.
        val t = DisplayTelemetry().merge(frame("5a 10 3e 00 30 39 07 29 00 58 00 00 01 00 00 00 7b 1f 40 ff"))
        assertEquals(62, t.speedKmh)
        assertEquals(12345, t.odometerKm)
        assertEquals(7, t.fuelLevel)
        assertEquals(41, t.averageSpeedKmh)
        assertEquals(88, t.topSpeedKmh)
        assertEquals(1, t.switchStatus)
        assertEquals(123, t.tripKm)
        assertEquals(8000, t.rpm)
    }

    @Test fun speedometer2And3MergeWithoutLosingSpeed() {
        val t = DisplayTelemetry(speedKmh = 30)
            .merge(frame("5a 11 00 00 02 00 00 00 00 27 00 00 03 00 00 00 00 00 00 ff"))
            .merge(frame("5a 19 00 00 00 00 00 00 34 30 00 00 b4 00 00 00 00 00 00 ff"))
        assertEquals(30, t.speedKmh)
        assertEquals(2, t.serviceReminder)
        assertEquals(0x27, t.vehicleModel)
        assertEquals(3, t.turnIndicators)
        assertEquals(52, t.averageEconomy)
        assertEquals(48, t.instantEconomy)
        assertEquals(180, t.rangeKm)
    }

    @Test fun speedometer2Diagnostics() {
        val t = DisplayTelemetry().merge(frame("5a 11 00 00 00 00 00 0c 05 27 00 00 00 00 00 00 00 00 00 ff"))
        assertEquals(12, t.softwareVersion)
        assertEquals(5, t.milCode)
        assertEquals(true, t.hasHealth)
    }

    @Test fun speedometer4Health() {
        // load 35, intake 0x2e, engine 0x5a, battery 0x7e = 126, run time 0x0258 = 600.
        val t = DisplayTelemetry().merge(frame("5a 18 23 00 00 00 00 2e 5a 00 00 7e 02 58 00 00 00 00 00 ff"))
        assertEquals(35, t.engineLoad)
        assertEquals(0x2e, t.intakeTemp)
        assertEquals(0x5a, t.engineTemp)
        assertEquals(126, t.batteryVoltage)
        assertEquals(600, t.engineRunTime)
        assertEquals(true, t.hasHealth)
        assertEquals(false, DisplayTelemetry().hasHealth)
    }

    @Test fun shortAndForeignFramesAreIgnored() {
        val t = DisplayTelemetry(speedKmh = 30)
        assertSame(t, t.merge(frame("5a 6b 01")))
        assertSame(t, t.merge(frame("5b 10 01 02")))
        // A truncated 5A 10 keeps what it cannot read.
        val partial = t.merge(frame("5a 10 28 00"))
        assertEquals(40, partial.speedKmh)
        assertEquals(null, partial.odometerKm)
    }

    @Test fun callButtons() {
        assertEquals(CallButton.ACCEPT, CallButton.from(1))
        assertEquals(CallButton.REJECT, CallButton.from(2))
        assertEquals(CallButton.REJECT_WITH_SMS, CallButton.from(3))
        assertEquals(CallButton.VOICE_ASSISTANT, CallButton.from(4))
        // Ignition flag (64) does not hide the action.
        assertEquals(CallButton.REJECT, CallButton.from(64 or 2))
        assertEquals(null, CallButton.from(0))
        assertEquals(null, CallButton.from(128))
        assertEquals(null, CallButton.from(null))
    }
}
