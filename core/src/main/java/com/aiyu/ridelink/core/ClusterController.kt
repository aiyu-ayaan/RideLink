package com.aiyu.ridelink.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.BatteryManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.aiyu.ridelink.core.diag.LogFile
import com.aiyu.ridelink.core.ble.ClusterLink
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.core.display.DisplayTemplate
import com.aiyu.ridelink.core.protocol.ClusterFamily
import com.aiyu.ridelink.core.protocol.ClusterModel
import com.aiyu.ridelink.core.protocol.ClusterProtocol
import com.aiyu.ridelink.core.protocol.ClusterProtocol.CallState
import com.aiyu.ridelink.core.protocol.ClusterProtocol.NavStep
import com.aiyu.ridelink.core.protocol.Maneuver
import com.aiyu.ridelink.core.protocol.WeatherKind
import com.aiyu.ridelink.core.settings.Settings
import com.aiyu.ridelink.core.phone.CallControl
import com.aiyu.ridelink.core.phone.MissedCalls
import com.aiyu.ridelink.core.phone.RejectMessage
import com.aiyu.ridelink.core.protocol.CallButton
import com.aiyu.ridelink.core.protocol.DisplayTelemetry
import com.aiyu.ridelink.core.protocol.FrameDecoder
import com.aiyu.ridelink.core.settings.GarageStore
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
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** [rx] = sent by the display; otherwise written by us and [ok] tells if it was acknowledged. */
data class FrameLog(val time: String, val hex: String, val ok: Boolean, val rx: Boolean = false, val decoded: String? = null) {
    /** One line for "Copy log": direction, time, hex and meaning. */
    fun line() = "${if (rx) "RX" else if (ok) "TX" else "TX-FAIL"} $time $hex${decoded?.let { "  | $it" }.orEmpty()}"
}

/** Owns the ride session: status loop, navigation and call forwarding to the cluster. */
@Singleton
class ClusterController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val link: ClusterLink,
    private val settings: SettingsRepository,
    private val weatherRepo: WeatherRepository,
    private val calls: CallControl,
    private val missedCalls: MissedCalls,
    private val logFile: LogFile,
    private val garage: GarageStore,
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
     * Accept and reject only touch a call we are showing as ringing; the voice button works any time.
     */
    private fun onSwitchStatus(old: Int?, new: Int?) {
        val button = CallButton.from(new) ?: return
        if (button == CallButton.from(old)) return
        scope.launch {
            val s = settings.current()
            if (button == CallButton.VOICE_ASSISTANT) {
                if (s.voiceButton) calls.openAssistant()
                return@launch
            }
            if (!s.callButtons || _caller.value == null) return@launch
            when (button) {
                CallButton.ACCEPT -> if (calls.accept()) onCallAnswered()
                CallButton.REJECT -> if (calls.end()) onCallEnded()
                CallButton.REJECT_WITH_SMS -> if (calls.end()) {
                    onCallEnded()
                    if (s.rejectSms) calls.smsLastCaller(RejectMessage.text(s.rejectSmsText))
                }
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

    /**
     * A display that proves it is a SmartXonnect LCD wins over a manual pick: its frames only exist in that protocol,
     * and the 2025+ format leaves its turn arrow blank.
     */
    private fun model(s: Settings) =
        _detected.value?.takeIf { it.family == ClusterFamily.SMARTXONNECT } ?: s.model ?: _detected.value ?: ClusterModel.DEFAULT

    init {
        scope.launch {
            link.incoming.collect { frame ->
                log(frame, ok = true, rx = true)
                ClusterModel.detect(frame)?.let { found ->
                    if (_detected.value != found) {
                        _detected.value = found
                        note("Display detected: ${found.name} (from ${"%02x %02x".format(frame[0], frame[1])})")
                        // Saved for this scooter, so the next connect uses the right format from its first frame.
                        if (found.family == ClusterFamily.SMARTXONNECT) {
                            val s = settings.current()
                            if (s.model != found) {
                                settings.setModel(found)
                                s.deviceAddress?.let { garage.setModel(it, found) }
                            }
                        }
                    }
                }
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

    /** Ends the session and forgets what the last display reported, before connecting to another scooter. */
    fun stopForBikeSwitch() {
        stop()
        _detected.value = null
        _telemetry.value = DisplayTelemetry()
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
        sendDisplayText(s, force = true)
        sendSocial()
        sendMusic(force = true)
        // After a reconnect the display has lost the route; put the current turn back without waiting for Maps.
        _nav.value?.let { postNav(it, demo = _demoNav.value) }
        startWeather()
        statusJob?.cancel()
        statusJob = scope.launch {
            while (isActive) {
                val now = settings.current()
                send(ClusterProtocol.status(model(now), phoneStatus(now)))
                // Stock writes no custom lines during a route; on the Race XP LCD they share the text area with
                // the turn, so live lines (speed, time) would keep replacing it. They come back when the route ends.
                if (_nav.value == null) sendDisplayText(now, force = false)
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

    /** Set when we switched the display to the weather screen; cleared when nav switches it back. */
    @Volatile private var weatherScreenOn = false

    /**
     * Screen switch to weather, then the weather frame, then the place name. Skipped while navigating, and
     * [onNavigation] switches back to the default screen first, so weather never covers the turn arrow.
     */
    private suspend fun sendWeather() {
        val s = settings.current()
        val m = model(s)
        if (!m.hasLocation || link.state.value !is LinkState.Ready) return
        val w = _weather.value
        if (m.hasTft && s.showWeather && w != null && _nav.value == null) {
            send(ClusterProtocol.screen(ClusterProtocol.Screen.WEATHER))
            weatherScreenOn = true
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
        if (!s.showMusic || !model(s).hasMusic || link.state.value !is LinkState.Ready) return@withLock
        val last = sentMusic.takeUnless { force }
        if (last?.title != m.title) send(ClusterProtocol.song(m.title))
        if (last?.artist != m.artist) send(ClusterProtocol.artist(m.artist))
        if (last?.state != m.state || last?.volumePercent != m.volumePercent) send(ClusterProtocol.playState(model(s), m))
        sentMusic = m
    }

    /**
     * Maps posts a new step about once a second and each one is two frames 400 ms apart. Steps go out one at a
     * time under [navLock], and a step that a newer update (or the end of navigation) replaced is dropped, so the
     * display never gets one step's text with another's arrow and a late step cannot bring back an ended route.
     */
    private val navLock = Mutex()
    private val navSeq = AtomicLong()

    /** A Google Maps step. Ignored while Demo navigation runs, so the two routes do not mix on the display. */
    fun onNavigation(step: NavStep) {
        if (_demoNav.value || _arrowSweep.value) return
        postNav(step)
    }

    /** [demo] steps go out even with "Forward navigation" off: the demo exists to test the display. */
    private fun postNav(step: NavStep, demo: Boolean = false) {
        val seq = navSeq.incrementAndGet()
        scope.launch {
            val s = settings.current()
            if (!(s.forwardNavigation || demo) || seq != navSeq.get()) return@launch
            _nav.value = step
            navLock.withLock {
                if (seq != navSeq.get()) return@withLock
                sendNav(s, step)
            }
            keepNavAlive()
        }
    }

    @Volatile private var navSentAt = 0L
    private var navKeepAlive: Job? = null

    /**
     * Stock resends the current turn every 2 s for the whole route (NavigationRepoImpl.startTimer, period 2000 ms),
     * while Maps only reposts when its text changes, sometimes 10+ s apart. Without the repeat the display drops
     * out of navigation between Maps updates.
     */
    private fun keepNavAlive() {
        if (navKeepAlive?.isActive == true) return
        navKeepAlive = scope.launch {
            while (isActive) {
                val every = ClusterProtocol.navResendMs(model(settings.current()))
                delay(every)
                val step = _nav.value ?: break
                if (link.state.value !is LinkState.Ready || _arrowSweep.value) continue
                if (System.currentTimeMillis() - navSentAt < every) continue
                val seq = navSeq.get()
                navLock.withLock { if (seq == navSeq.get() && _nav.value === step) sendNav(settings.current(), step) }
            }
        }
    }

    private suspend fun sendNav(s: Settings, step: NavStep) {
        if (weatherScreenOn) {
            weatherScreenOn = false
            send(ClusterProtocol.screen(ClusterProtocol.Screen.DEFAULT))
        }
        val m = model(s)
        ClusterProtocol.navigation(m, step).forEachIndexed { i, f ->
            if (i > 0) delay(ClusterProtocol.navGapMs(m))
            send(f)
        }
        navSentAt = System.currentTimeMillis()
    }

    fun onNavigationEnded() {
        if (_demoNav.value) return
        endNav()
    }

    private fun endNav(demo: Boolean = false) {
        navSeq.incrementAndGet()
        navKeepAlive?.cancel()
        scope.launch {
            navLock.withLock {
                val last = _nav.value ?: return@withLock
                _nav.value = null
                val s = settings.current()
                if (s.forwardNavigation || demo) {
                    val m = model(s)
                    ClusterProtocol.navigation(m, last.copy(stopped = true)).forEachIndexed { i, f ->
                        if (i > 0) delay(ClusterProtocol.navGapMs(m))
                        send(f)
                    }
                }
                // Custom lines were held back during the route (see the status loop); put them back.
                if (link.state.value is LinkState.Ready) sendDisplayText(s, force = true)
            }
        }
    }

    private val _arrowSweep = MutableStateFlow(false)
    /** Console: every arrow code from 0 to [ARROW_CODE_MAX] in turn, about 2.4 s each, with "CODE n" as the text. */
    val arrowSweep: StateFlow<Boolean> = _arrowSweep.asStateFlow()
    private var sweepJob: Job? = null

    fun setArrowSweep(on: Boolean) {
        sweepJob?.cancel()
        _arrowSweep.value = on
        if (!on) return
        if (_demoNav.value) setDemoNav(false)
        sweepJob = scope.launch {
            for (code in 0..ARROW_CODE_MAX) {
                repeat(3) {
                    val m = model(settings.current())
                    navLock.withLock {
                        ClusterProtocol.arrowProbe(m, code).forEachIndexed { i, f ->
                            if (i > 0) delay(ClusterProtocol.navGapMs(m))
                            send(f)
                        }
                    }
                    delay(ClusterProtocol.SMARTXONNECT_NAV_RESEND_MS)
                }
            }
            _arrowSweep.value = false
        }
    }

    private val _demoNav = MutableStateFlow(false)
    /** Developer option: a fake route through every turn type, sent to the display like a Maps route. */
    val demoNav: StateFlow<Boolean> = _demoNav.asStateFlow()
    private var demoNavJob: Job? = null

    fun setDemoNav(on: Boolean) {
        if (on && _arrowSweep.value) setArrowSweep(false)
        demoNavJob?.cancel()
        _demoNav.value = on
        if (!on) {
            endNav(demo = true)
            return
        }
        demoNavJob = scope.launch {
            val turns = DEMO_TURNS
            val leg = 300
            while (isActive) {
                var left = turns.size * leg
                for (maneuver in turns) {
                    val text = ClusterProtocol.directionText(maneuver)
                    // Count down to the turn in 50 m steps, so each arrow stays up for a few seconds.
                    for (d in leg downTo 50 step 50) {
                        postNav(NavStep(d, (left / 6.0).toLong(), left, maneuver, text), demo = true)
                        left -= 50
                        delay(DEMO_NAV_STEP_MS)
                    }
                }
            }
        }
    }

    fun onIncomingCall(nameOrNumber: String) {
        if (callJob?.isActive == true && _caller.value == nameOrNumber) return
        callJob?.cancel()
        callJob = scope.launch {
            if (!settings.current().forwardCalls) return@launch
            _caller.value = nameOrNumber
            callState = CallState.RINGING
            sendDisplayText(settings.current(), force = false)
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
            if (link.state.value is LinkState.Ready) sendDisplayText(settings.current(), force = false)
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
                            speedKmh = v, odometerTenths = 17_180 + tick, fuelLevel = 6, averageSpeedKmh = 34,
                            topSpeedKmh = 72, tripTenths = 182 + tick, averageEconomy = 48, rangeKm = 182 - tick / 20,
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

    @Volatile private var sentLine1: String? = null
    @Volatile private var sentLine2: String? = null

    /**
     * Custom lines are templates (see DisplayTemplate). Rendered on connect and every status tick; a line is
     * written only when its text changed, or always with [force].
     */
    private suspend fun sendDisplayText(s: Settings, force: Boolean) {
        if (s.displayLine1.isNotBlank()) {
            val line = renderLine(s.displayLine1, s)
            if (force || line != sentLine1) { send(ClusterProtocol.customLine1(line)); sentLine1 = line }
        }
        if (s.displayLine2.isNotBlank()) {
            val line = renderLine(s.displayLine2, s)
            if (force || line != sentLine2) { send(ClusterProtocol.customLine2(line)); sentLine2 = line }
        }
    }

    private fun renderLine(template: String, s: Settings) = ClusterProtocol.displayText(DisplayTemplate.render(template, templateValues(s)))

    /** What a template shows on the display right now; for the live preview in Settings. */
    fun previewLine(template: String, s: Settings): String = renderLine(template, s)

    /** Settings "Send to display": rider name and both custom lines right away. */
    fun sendDisplayTextNow() = scope.launch {
        val s = settings.current()
        if (s.riderName.isNotBlank()) send(ClusterProtocol.riderName(s.riderName))
        sendDisplayText(s, force = true)
    }

    /** Live values for the template tokens. Text frames only show letters, digits and spaces, so no dots or colons. */
    private fun templateValues(s: Settings): Map<DisplayTemplate.Token, String?> {
        val now = LocalDateTime.now()
        val t = _telemetry.value
        val w = _weather.value?.takeIf { s.showWeather }
        val m = _music.value?.takeIf { s.showMusic }
        val hour12 = (now.hour % 12).let { if (it == 0) 12 else it }
        return mapOf(
            DisplayTemplate.Token.RIDER to s.riderName,
            DisplayTemplate.Token.CALLER to _caller.value,
            DisplayTemplate.Token.TIME to "%d %02d %s".format(hour12, now.minute, if (now.hour >= 12) "PM" else "AM"),
            DisplayTemplate.Token.DATE to "${now.dayOfMonth} ${now.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}",
            DisplayTemplate.Token.BATTERY to batteryPercent().toString(),
            DisplayTemplate.Token.TEMP to w?.let { "${it.temperatureC}C" },
            DisplayTemplate.Token.WEATHER to w?.description,
            DisplayTemplate.Token.AQI to w?.aqi?.toString(),
            DisplayTemplate.Token.PLACE to _place.value?.takeIf { s.showLocation },
            DisplayTemplate.Token.SONG to m?.title,
            DisplayTemplate.Token.ARTIST to m?.artist,
            DisplayTemplate.Token.SPEED to (t.speedKmh ?: _speed.value)?.toString(),
            DisplayTemplate.Token.ODO to t.odometerKm?.toInt()?.toString(),
            DisplayTemplate.Token.TRIP to t.tripKm?.toInt()?.toString(),
            DisplayTemplate.Token.RANGE to t.rangeKm?.toString(),
            DisplayTemplate.Token.FUEL to t.fuelLevel?.toString(),
            DisplayTemplate.Token.MILEAGE to t.averageEconomy?.toString(),
            DisplayTemplate.Token.WHATSAPP to _social.value.whatsappMessages.takeIf { it > 0 }?.toString(),
            DisplayTemplate.Token.MISSED to (if (s.missedCalls) missedCalls.count() else 0).takeIf { it > 0 }?.toString(),
        )
    }

    private fun batteryPercent() =
        context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0

    /** Debug screen: write raw text to custom line 1. */
    fun sendTestText(text: String) = scope.launch { send(ClusterProtocol.text(ClusterProtocol.DID_TEXT_LINE_1, text)) }

    /** Console test frames, one feature at a time, for checking each on the real display. */
    enum class TestFrame { WEATHER, LOCATION, SOCIAL, MUSIC, DEFAULT_SCREEN }

    fun sendTest(which: TestFrame) = scope.launch {
        val m = model(settings.current())
        when (which) {
            TestFrame.WEATHER -> {
                val w = _weather.value ?: ClusterProtocol.Weather(WeatherKind.RAIN, 24, aqi = 120, rainPercent = 70, description = "Rain")
                send(ClusterProtocol.screen(ClusterProtocol.Screen.WEATHER))
                weatherScreenOn = true
                send(ClusterProtocol.weather(m, w))
            }
            TestFrame.LOCATION -> send(ClusterProtocol.location(_place.value ?: "Test place"))
            TestFrame.SOCIAL -> send(ClusterProtocol.socialCounts(ClusterProtocol.SocialCounts(3, 2, 1, 4)))
            TestFrame.MUSIC -> {
                val music = _music.value ?: ClusterProtocol.Music("Test song", "Test artist", ClusterProtocol.PlayState.PLAYING, 60_000, 200_000, 50)
                send(ClusterProtocol.song(music.title))
                send(ClusterProtocol.artist(music.artist))
                send(ClusterProtocol.playState(m, music))
            }
            TestFrame.DEFAULT_SCREEN -> {
                weatherScreenOn = false
                send(ClusterProtocol.screen(ClusterProtocol.Screen.DEFAULT))
            }
        }
    }

    private suspend fun send(frame: ByteArray): Boolean = link.send(frame).also { log(frame, it) }

    /** Console line for a phone-side event (raw Maps / call notification), so "Copy log" shows what the app saw. */
    fun note(text: String) {
        val line = FrameLog(LocalDateTime.now().toLocalTime().withNano(0).toString(), "note", ok = true, rx = true, decoded = text)
        _log.update { (listOf(line) + it).take(LOG_SIZE) }
        logFile.write(line.line())
    }

    private fun log(frame: ByteArray, ok: Boolean, rx: Boolean = false) {
        val line = FrameLog(
            LocalDateTime.now().toLocalTime().withNano(0).toString(),
            frame.joinToString(" ") { "%02x".format(it) },
            ok,
            rx,
            FrameDecoder.decode(frame),
        )
        _log.update { (listOf(line) + it).take(LOG_SIZE) }
        logFile.write(line.line())
    }

    @SuppressLint("MissingPermission")
    private fun phoneStatus(s: Settings): ClusterProtocol.PhoneStatus {
        val battery = batteryPercent()
        val tm = dataTelephony()
        // getSignalStrength needs no permission (the stock app gated it on location; that left the bars empty).
        val signal = signalLevel(tm)
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

    /** TelephonyManager for the SIM that carries mobile data; on dual-SIM phones the default one may report nothing. */
    private fun dataTelephony(): TelephonyManager? {
        val base = context.getSystemService(TelephonyManager::class.java) ?: return null
        val sub = SubscriptionManager.getDefaultDataSubscriptionId()
        return if (sub != SubscriptionManager.INVALID_SUBSCRIPTION_ID) runCatching { base.createForSubscriptionId(sub) }.getOrDefault(base) else base
    }

    /** 0..4 bars: SignalStrength.getLevel, else the best cell in it (some phones report level 0 with valid cells). */
    private fun signalLevel(tm: TelephonyManager?): Int {
        if (tm == null || Build.VERSION.SDK_INT < 28) return 0
        val strength = runCatching { tm.signalStrength }.getOrNull() ?: return 0
        val cells = if (Build.VERSION.SDK_INT >= 29) runCatching { strength.cellSignalStrengths.maxOfOrNull { it.level } }.getOrNull() else null
        return maxOf(strength.level, cells ?: 0).coerceIn(0, 4)
    }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val LOG_SIZE = 300
        const val DEMO_STEP_MS = 1_500L
        const val DEMO_NAV_STEP_MS = 1_000L
        const val ARROW_CODE_MAX = 80

        /** One of every turn type. */
        val DEMO_TURNS = listOf(
            Maneuver.STRAIGHT, Maneuver.LEFT, Maneuver.RIGHT, Maneuver.SLIGHT_LEFT, Maneuver.SLIGHT_RIGHT,
            Maneuver.SHARP_LEFT, Maneuver.SHARP_RIGHT, Maneuver.FORK_LEFT, Maneuver.FORK_RIGHT,
            Maneuver.ROUNDABOUT, Maneuver.U_TURN, Maneuver.DESTINATION,
        )
        const val WEATHER_INTERVAL_MS = 15 * 60_000L
    }
}
