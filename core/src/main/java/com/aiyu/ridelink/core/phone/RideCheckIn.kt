package com.aiyu.ridelink.core.phone

import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.analytics.RideRecorder
import com.aiyu.ridelink.core.settings.SettingsRepository
import com.aiyu.ridelink.core.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The SMS the check-in contact gets. A Google Maps link, as stock TVS Connect does for SOS
 * (Utils.sendSendSosMessage: "http://maps.google.com/maps?q=loc:lat,lng" by SmsManager). Google Maps has no
 * public way to start live location sharing from another app, so this is a start and an end message.
 */
object CheckInMessage {
    fun link(lat: Double, lng: Double) = "https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, lat, lng)

    fun start(lat: Double?, lng: Double?) =
        "Started a ride on the scooter." + (if (lat != null && lng != null) " Starting point: ${link(lat, lng)}" else "")

    fun end(lat: Double?, lng: Double?, km: Double?) =
        "Ride ended" + (km?.let { " after %.1f km".format(Locale.US, it) } ?: "") + "." +
            (if (lat != null && lng != null) " I'm here: ${link(lat, lng)}" else "")
}

/**
 * Texts the rider's check-in contact when a recorded ride starts and when it ends. Rides come from [RideRecorder]
 * (first movement while connected; end after 10 min standing or when the link drops). Demo rides send nothing.
 */
@Singleton
class RideCheckIn @Inject constructor(
    private val recorder: RideRecorder,
    private val controller: ClusterController,
    private val settings: SettingsRepository,
    private val location: WeatherRepository,
    private val calls: CallControl,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false
    private var lastStartSms = 0L
    private var startOdometer: Int? = null
    private var sentStart = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            recorder.recording.drop(1).collect { riding ->
                val s = settings.current()
                if (!s.checkIn || s.checkInNumber.isBlank() || controller.demo.value) return@collect
                val now = System.currentTimeMillis()
                if (riding) {
                    // A short stop ends the recorded ride; do not text again for the next leg right away.
                    if (now - lastStartSms < START_COOLDOWN_MS) return@collect
                    startOdometer = controller.telemetry.value.odometerTenths
                    val here = location.location(precise = true)
                    sentStart = send(s.checkInNumber, CheckInMessage.start(here?.latitude, here?.longitude), "start")
                    if (sentStart) lastStartSms = now
                } else if (sentStart) {
                    sentStart = false
                    val km = startOdometer?.let { from -> controller.telemetry.value.odometerTenths?.let { (it - from) / 10.0 } }
                    val here = location.location(precise = true)
                    send(s.checkInNumber, CheckInMessage.end(here?.latitude, here?.longitude, km?.takeIf { it > 0 }), "end")
                }
            }
        }
    }

    /** Developer test: the start message to the saved contact now. */
    fun sendTest() = scope.launch {
        val s = settings.current()
        if (s.checkInNumber.isBlank()) {
            controller.note("Check-in test: no contact set")
            return@launch
        }
        val here = location.location(precise = true)
        send(s.checkInNumber, CheckInMessage.start(here?.latitude, here?.longitude), "test")
    }

    private fun send(to: String, text: String, what: String): Boolean =
        calls.sendSms(to, text).also { controller.note(if (it) "Check-in $what sent" else "Check-in $what not sent (SMS permission?)") }

    private companion object {
        const val START_COOLDOWN_MS = 30 * 60_000L
    }
}
