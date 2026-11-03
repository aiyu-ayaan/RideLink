package com.aiyu.ridelink.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.BatteryManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.aiyu.ridelink.core.ble.ClusterLink
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.core.protocol.ClusterModel
import com.aiyu.ridelink.core.protocol.ClusterProtocol
import com.aiyu.ridelink.core.protocol.ClusterProtocol.CallState
import com.aiyu.ridelink.core.protocol.ClusterProtocol.NavStep
import com.aiyu.ridelink.core.protocol.Maneuver
import com.aiyu.ridelink.core.protocol.WeatherKind
import com.aiyu.ridelink.core.settings.Settings
import com.aiyu.ridelink.core.phone.CallControl
import com.aiyu.ridelink.core.phone.MissedCalls
import com.aiyu.ridelink.core.protocol.CallButton
import com.aiyu.ridelink.core.protocol.DisplayTelemetry
import com.aiyu.ridelink.core.settings.SettingsRepository
import com.aiyu.ridelink.core.weather.WeatherRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** [rx] = sent by the display; otherwise written by us and [ok] tells if it was acknowledged. */
data class FrameLog(val time: String, val hex: String, val ok: Boolean, val rx: Boolean = false)

/** Owns the ride session: status loop, navigation and call forwarding to the cluster. */
@Singleton
class ClusterController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val link: ClusterLink,
    private val settings: SettingsRepository,
    private val weatherRepo: WeatherRepository,
    private val calls: CallControl,
    private val missedCalls: MissedCalls,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: Job? = null
    private var callJob: Job? = null
    @Volatile private var callState = CallState.IDLE

    private val _nav = MutableStateFlow<NavStep?>(null)
    val nav: StateFlow<NavStep?> = _nav.asStateFlow()

    private val _caller = MutableStateFlow<String?>(null)
    val caller: StateFlow<String?> = _caller.asStateFlow()

    private val _log = MutableStateFlow<List<FrameLog>>(emptyList())
    val log: StateFlow<List<FrameLog>> = _log.asStateFlow()

    val linkState = link.state

    /** Model inferred from display frames while in Auto mode; null until something proves it. */
    private val _detected = MutableStateFlow<ClusterModel?>(null)
    val detected: StateFlow<ClusterModel?> = _detected.asStateFlow()

    /** Km/h reported by the display. */
    private val _speed = MutableStateFlow<Int?>(null)
    val speed: StateFlow<Int?> = _speed.asStateFlow()

    /** Odometer, trip, fuel, range and the rest from the display's 5A 10 / 11 / 19 frames. */
    private val _telemetry = MutableStateFlow(DisplayTelemetry())
    val telemetry: StateFlow<DisplayTelemetry> = _telemetry.asStateFlow()

    /**
     * Scooter buttons arrive as a level in every 5A 10 frame, so act only when the action changes.
     * Accept and reject only touch a call we are showing as ringing.
     * shortcut: "reject with SMS" rejects without the SMS; voice assistant is ignored (no background activity start).
     */
    private fun onSwitchStatus(old: Int?, new: Int?) {
        val button = CallButton.from(new) ?: return
        if (button == CallButton.from(old)) return
        scope.launch {
            if (!settings.current().callButtons || _caller.value == null) return@launch
            when (button) {
                CallButton.ACCEPT -> if (calls.accept()) onCallAnswered()
                CallButton.REJECT, CallButton.REJECT_WITH_SMS -> if (calls.end()) onCallEnded()
                CallButton.VOICE_ASSISTANT -> Unit
            }
        }
    }

    /** Stop ringing on the display; the status frame reports the call as accepted. */
    private fun onCallAnswered() {
        callJob?.cancel()
        _caller.value = null
        callState = CallState.ACCEPTED
    }

    /** Demo ride: fake speed, turns and a call, for screenshots and testing without a scooter. */
    private val _demo = MutableStateFlow(false)
    val demo: StateFlow<Boolean> = _demo.asStateFlow()
    private var demoJob: Job? = null

    private fun model(s: Settings) = s.model ?: _detected.value ?: ClusterModel.DEFAULT

    init {
        scope.launch {
            link.incoming.collect { frame ->
                log(frame, ok = true, rx = true)
                ClusterModel.detect(frame)?.let { _detected.value = it }
                val before = _telemetry.value
                val after = before.merge(frame)
                if (after !== before) {
                    _telemetry.value = after
                    after.speedKmh?.let { _speed.value = it }
                    onSwitchStatus(before.switchStatus, after.switchStatus)
                }
            }
        }
    }

    /** Called by the foreground service. Connects to the saved cluster and keeps the session alive. */
    fun start() {
        if (session?.isActive == true) return
        session = scope.launch {
            val address = settings.current().deviceAddress ?: return@launch
            link.connect(address)
            link.state.collect { state ->
                if (state is LinkState.Ready) onReady()
            }
        }
    }

    fun stop() {
        session?.cancel()
        statusJob?.cancel()
        weatherJob?.cancel()
        callJob?.cancel()
        link.disconnect()
    }

    private var statusJob: Job? = null
    private var weatherJob: Job? = null

    private val _weather = MutableStateFlow<ClusterProtocol.Weather?>(null)
    val weather: StateFlow<ClusterProtocol.Weather?> = _weather.asStateFlow()

    private val _place = MutableStateFlow<String?>(null)
    val place: StateFlow<String?> = _place.asStateFlow()

    private suspend fun onReady() {
        val s = settings.current()
        if (s.riderName.isNotBlank()) send(ClusterProtocol.riderName(s.riderName))
        sendSocial()
        sendMusic(force = true)
        startWeather()
        statusJob?.cancel()
        statusJob = scope.launch {
            while (isActive) {
                val now = settings.current()
                send(ClusterProtocol.status(model(now), phoneStatus(now)))
                delay(ClusterProtocol.STATUS_INTERVAL_MS)
            }
        }
    }

    /** Weather, AQI and place name every [WEATHER_INTERVAL_MS] while connected (stock weather loop). */
    private fun startWeather() {
        weatherJob?.cancel()
        weatherJob = scope.launch {
            while (isActive) {
                if (!_demo.value) refreshWeather()
                delay(WEATHER_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshWeather() {
        val s = settings.current()
        if (!s.showWeather && !s.showLocation) return
        val here = weatherRepo.location() ?: return
        if (s.showWeather) weatherRepo.weather(here)?.let { _weather.value = it }
        if (s.showLocation) weatherRepo.placeName(here)?.let { _place.value = it }
        sendWeather()
    }

    /**
     * Screen switch to weather, then the weather frame, then the place name. Skipped while navigating so the
     * weather screen never covers the turn arrow. shortcut: whether the display returns to nav on its own is unconfirmed.
     */
    private suspend fun sendWeather() {
        val s = settings.current()
        val m = model(s)
        if (!m.hasTft || link.state.value !is LinkState.Ready) return
        val w = _weather.value
        if (s.showWeather && w != null && _nav.value == null) {
            send(ClusterProtocol.screen(ClusterProtocol.Screen.WEATHER))
            send(ClusterProtocol.weather(m, w))
        }
        _place.value?.takeIf { s.showLocation }?.let { send(ClusterProtocol.location(it)) }
    }

    private val _music = MutableStateFlow<ClusterProtocol.Music?>(null)
    val music: StateFlow<ClusterProtocol.Music?> = _music.asStateFlow()
    private var sentMusic: ClusterProtocol.Music? = null

    /** Current media session, or null when nothing is playing. Frames go out only for what changed. */
    fun onMusic(m: ClusterProtocol.Music?) {
        _music.value = m
        scope.launch { sendMusic(force = false) }
    }

    private val musicLock = Mutex()

    private suspend fun sendMusic(force: Boolean) = musicLock.withLock {
        val s = settings.current()
        val m = _music.value ?: return@withLock
        if (!s.showMusic || link.state.value !is LinkState.Ready) return@withLock
        val last = sentMusic.takeUnless { force }
        if (last?.title != m.title) send(ClusterProtocol.song(m.title))
        if (last?.artist != m.artist) send(ClusterProtocol.artist(m.artist))
        if (last?.state != m.state || last?.volumePercent != m.volumePercent) send(ClusterProtocol.playState(model(s), m))
        sentMusic = m
    }

    fun onNavigation(step: NavStep) = scope.launch {
        val s = settings.current()
        if (!s.forwardNavigation) return@launch
        _nav.value = step
        val (text, control) = ClusterProtocol.navigation(model(s), step)
        send(text)
        delay(ClusterProtocol.NAV_FRAME_GAP_MS)
        send(control)
    }

    fun onNavigationEnded() = scope.launch {
        val last = _nav.value ?: return@launch
        _nav.value = null
        val s = settings.current()
        if (s.forwardNavigation) ClusterProtocol.navigation(model(s), last.copy(stopped = true)).forEach { send(it) }
    }

    fun onIncomingCall(nameOrNumber: String) {
        if (callJob?.isActive == true && _caller.value == nameOrNumber) return
        callJob?.cancel()
        callJob = scope.launch {
            if (!settings.current().forwardCalls) return@launch
            _caller.value = nameOrNumber
            callState = CallState.RINGING
            val frame = ClusterProtocol.caller(nameOrNumber)
            // Stock app repeats the caller frame every 500 ms while ringing; cap it at 60 s.
            withTimeoutOrNull(60_000) {
                while (isActive) {
                    send(frame)
                    delay(ClusterProtocol.CALLER_REPEAT_MS)
                }
            }
        }
    }

    fun onCallEnded() {
        callJob?.cancel()
        _caller.value = null
        callState = CallState.ENDED
        scope.launch {
            delay(ClusterProtocol.STATUS_INTERVAL_MS * 2)
            callState = CallState.IDLE
        }
    }

    private val _social = MutableStateFlow(ClusterProtocol.SocialCounts())
    val social: StateFlow<ClusterProtocol.SocialCounts> = _social.asStateFlow()

    /** Unread chat counts; sent when they change and again on every (re)connect. */
    fun onSocialCounts(counts: ClusterProtocol.SocialCounts) {
        if (counts == _social.value) return
        _social.value = counts
        scope.launch { sendSocial() }
    }

    private suspend fun sendSocial() {
        if (settings.current().showSocial && link.state.value is LinkState.Ready) send(ClusterProtocol.socialCounts(_social.value))
    }

    fun setDemo(on: Boolean) {
        demoJob?.cancel()
        _demo.value = on
        if (!on) {
            _speed.value = null
            _telemetry.value = DisplayTelemetry()
            _weather.value = null
            _place.value = null
            onMusic(null)
            onSocialCounts(ClusterProtocol.SocialCounts())
            onNavigationEnded()
            onCallEnded()
            return
        }
        // Fake data for every TFT feature, sent through the same encoder and link as real data.
        _weather.value = ClusterProtocol.Weather(WeatherKind.CLOUDY, temperatureC = 27, aqi = 84, rainPercent = 20, description = "Cloudy")
        _place.value = "Indiranagar"
        scope.launch { sendWeather() }
        onSocialCounts(ClusterProtocol.SocialCounts(whatsappMessages = 5, whatsappChats = 2, facebookMessages = 1, instagram = 3))
        onMusic(ClusterProtocol.Music("Kesariya", "Arijit Singh", ClusterProtocol.PlayState.PLAYING, 83_000, 268_000, 60))
        demoJob = scope.launch {
            val route = listOf(
                Triple(Maneuver.RIGHT, "Turn right onto MG Road", 450),
                Triple(Maneuver.SLIGHT_LEFT, "Slight left toward Ring Rd", 900),
                Triple(Maneuver.ROUNDABOUT, "At the roundabout take exit 2", 300),
                Triple(Maneuver.U_TURN, "Make a U-turn", 200),
                Triple(Maneuver.DESTINATION, "Arrive at Home", 120),
            )
            var left = route.sumOf { it.third }
            var tick = 0
            while (isActive) {
                for ((maneuver, text, length) in route) {
                    var d = length
                    while (d > 0 && isActive) {
                        val v = 28 + (tick * 7) % 24
                        _speed.value = v
                        _telemetry.value = DisplayTelemetry(
                            speedKmh = v, odometerKm = 12_345 + tick / 20, fuelLevel = 6, averageSpeedKmh = 34,
                            topSpeedKmh = 72, tripKm = 18 + tick / 20, averageEconomy = 48, rangeKm = 182 - tick / 20,
                            rpm = 3_000 + v * 60, instantEconomy = 40 + tick % 12, batteryVoltage = 128, engineTemp = 92,
                            serviceReminder = 0, milCode = 0, turnIndicators = 0,
                        )
                        onNavigation(ClusterProtocol.NavStep(d, (left / 8.0).toLong(), left, maneuver, text))
                        if (tick == 4) onIncomingCall("Mom") else if (tick == 9) onCallEnded()
                        tick++
                        d -= 50
                        left = (left - 50).coerceAtLeast(0)
                        delay(DEMO_STEP_MS)
                    }
                }
                left = route.sumOf { it.third }
            }
        }
    }

    /** Debug screen: write raw text to custom line 1. */
    fun sendTestText(text: String) = scope.launch { send(ClusterProtocol.text(ClusterProtocol.DID_TEXT_LINE_1, text)) }

    private suspend fun send(frame: ByteArray): Boolean = link.send(frame).also { log(frame, it) }

    private fun log(frame: ByteArray, ok: Boolean, rx: Boolean = false) {
        val line = FrameLog(LocalDateTime.now().toLocalTime().withNano(0).toString(), frame.joinToString(" ") { "%02x".format(it) }, ok, rx)
        _log.update { (listOf(line) + it).take(LOG_SIZE) }
    }

    @SuppressLint("MissingPermission")
    private fun phoneStatus(s: Settings): ClusterProtocol.PhoneStatus {
        val battery = context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        val tm = context.getSystemService(TelephonyManager::class.java)
        // Stock app (sendJupiterMobileData) reads the level only with location granted.
        val signal = if (granted(Manifest.permission.ACCESS_COARSE_LOCATION) && Build.VERSION.SDK_INT >= 28) {
            runCatching { tm?.signalStrength?.level ?: 0 }.getOrDefault(0)
        } else 0
        // getDataNetworkType needs READ_PHONE_STATE; without it the cluster shows no network type.
        val network = if (granted(Manifest.permission.READ_PHONE_STATE)) {
            runCatching { ClusterProtocol.networkTypeCode(tm?.dataNetworkType ?: 0) }.getOrDefault(0)
        } else 0
        val now = LocalDateTime.now()
        return ClusterProtocol.PhoneStatus(
            batteryPercent = battery,
            signalLevel = signal,
            networkType = network,
            hour12 = (now.hour % 12).let { if (it == 0) 12 else it },
            minute = now.minute,
            second = now.second,
            isPm = now.hour >= 12,
            day = now.dayOfMonth,
            month = now.monthValue,
            year = now.year,
            callState = callState,
            temperatureC = _weather.value?.takeIf { s.showWeather }?.temperatureC ?: 0,
            overSpeedKmh = s.overSpeedKmh.takeIf { s.overSpeedAlert },
            missedCalls = if (s.missedCalls) missedCalls.count() else 0,
        )
    }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val LOG_SIZE = 100
        const val DEMO_STEP_MS = 1_500L
        const val WEATHER_INTERVAL_MS = 15 * 60_000L
    }
}
