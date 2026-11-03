package com.aiyu.ridelink.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.BatteryManager
import android.telephony.TelephonyManager
import com.aiyu.ridelink.core.ble.ClusterLink
import com.aiyu.ridelink.core.ble.LinkState
import com.aiyu.ridelink.core.protocol.ClusterModel
import com.aiyu.ridelink.core.protocol.ClusterProtocol
import com.aiyu.ridelink.core.protocol.ClusterProtocol.CallState
import com.aiyu.ridelink.core.protocol.ClusterProtocol.NavStep
import com.aiyu.ridelink.core.settings.Settings
import com.aiyu.ridelink.core.settings.SettingsRepository
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

    private fun model(s: Settings) = s.model ?: _detected.value ?: ClusterModel.DEFAULT

    init {
        scope.launch {
            link.incoming.collect { frame ->
                log(frame, ok = true, rx = true)
                ClusterModel.detect(frame)?.let { _detected.value = it }
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
        callJob?.cancel()
        link.disconnect()
    }

    private var statusJob: Job? = null

    private suspend fun onReady() {
        val s = settings.current()
        if (s.riderName.isNotBlank()) send(ClusterProtocol.riderName(s.riderName))
        statusJob?.cancel()
        statusJob = scope.launch {
            while (isActive) {
                send(ClusterProtocol.status(model(settings.current()), phoneStatus()))
                delay(ClusterProtocol.STATUS_INTERVAL_MS)
            }
        }
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

    fun onMessage(sender: String) = scope.launch {
        if (settings.current().forwardMessages) send(ClusterProtocol.text(ClusterProtocol.DID_TEXT_LINE_2, sender))
    }

    /** Debug screen: write raw text to custom line 1. */
    fun sendTestText(text: String) = scope.launch { send(ClusterProtocol.text(ClusterProtocol.DID_TEXT_LINE_1, text)) }

    private suspend fun send(frame: ByteArray): Boolean = link.send(frame).also { log(frame, it) }

    private fun log(frame: ByteArray, ok: Boolean, rx: Boolean = false) {
        val line = FrameLog(LocalDateTime.now().toLocalTime().withNano(0).toString(), frame.joinToString(" ") { "%02x".format(it) }, ok, rx)
        _log.update { (listOf(line) + it).take(LOG_SIZE) }
    }

    @SuppressLint("MissingPermission")
    private fun phoneStatus(): ClusterProtocol.PhoneStatus {
        val battery = context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        val tm = context.getSystemService(TelephonyManager::class.java)
        val signal = runCatching { if (android.os.Build.VERSION.SDK_INT >= 28) tm?.signalStrength?.level ?: 0 else 0 }.getOrDefault(0)
        // getDataNetworkType needs READ_PHONE_STATE; without it the cluster just shows no network type.
        val network = runCatching { ClusterProtocol.networkTypeCode(tm?.dataNetworkType ?: 0) }.getOrDefault(0)
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
        )
    }

    private companion object {
        const val LOG_SIZE = 100
    }
}
