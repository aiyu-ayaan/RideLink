package com.aiyu.ridelink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.ble.ClusterLink
import com.aiyu.ridelink.core.ble.FoundCluster
import com.aiyu.ridelink.core.protocol.ClusterModel
import com.aiyu.ridelink.core.protocol.ClusterProtocol.NavStep
import com.aiyu.ridelink.core.protocol.Maneuver
import com.aiyu.ridelink.core.settings.Settings
import com.aiyu.ridelink.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val link: ClusterLink,
    private val controller: ClusterController,
    private val repo: SettingsRepository,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val linkState = link.state
    val found = link.found
    val scanning = link.scanning
    val nav = controller.nav
    val caller = controller.caller
    val log = controller.log
    val detected = controller.detected
    val speed = controller.speed
    val demo = controller.demo
    val social = controller.social
    val weather = controller.weather
    val place = controller.place
    val music = controller.music
    val telemetry = controller.telemetry

    /** Set by the Disconnect button; stops auto-connect until the app process starts again. */
    var userStopped = false

    fun startScan() = link.startScan()
    fun stopScan() = link.stopScan()

    fun pair(cluster: FoundCluster) = viewModelScope.launch {
        link.stopScan()
        userStopped = false
        repo.setDevice(cluster.address, cluster.name)
    }

    fun forget() = viewModelScope.launch {
        controller.stop()
        repo.setDevice(null, null)
    }

    fun setModel(model: ClusterModel?) = viewModelScope.launch { repo.setModel(model) }
    fun setRiderName(name: String) = viewModelScope.launch { repo.setRiderName(name) }
    fun setDisplayLines(line1: String, line2: String) = viewModelScope.launch { repo.setDisplayLines(line1, line2) }
    fun sendDisplayText() = controller.sendDisplayTextNow()
    fun setForwardNavigation(on: Boolean) = viewModelScope.launch { repo.setForwardNavigation(on) }
    fun setForwardCalls(on: Boolean) = viewModelScope.launch { repo.setForwardCalls(on) }
    fun setShowSocial(on: Boolean) = viewModelScope.launch { repo.setShowSocial(on) }
    fun setShowWeather(on: Boolean) = viewModelScope.launch { repo.setShowWeather(on) }
    fun setShowLocation(on: Boolean) = viewModelScope.launch { repo.setShowLocation(on) }
    fun setShowMusic(on: Boolean) = viewModelScope.launch { repo.setShowMusic(on) }
    fun setCallButtons(on: Boolean) = viewModelScope.launch { repo.setCallButtons(on) }
    fun setShowRideData(on: Boolean) = viewModelScope.launch { repo.setShowRideData(on) }
    fun setShowVehicle(on: Boolean) = viewModelScope.launch { repo.setShowVehicle(on) }
    fun setOverSpeedAlert(on: Boolean) = viewModelScope.launch { repo.setOverSpeedAlert(on) }
    fun setOverSpeedKmh(kmh: Int) = viewModelScope.launch { repo.setOverSpeedKmh(kmh) }
    fun setMissedCalls(on: Boolean) = viewModelScope.launch { repo.setMissedCalls(on) }

    fun setDemo(on: Boolean) = controller.setDemo(on)
    fun sendText(text: String) = controller.sendTestText(text)
    fun sendTest(which: ClusterController.TestFrame) = controller.sendTest(which)
    fun sendTestTurn() = controller.onNavigation(NavStep(250, 540, 3_400, Maneuver.RIGHT, "Turn right test"))
    fun testCall(start: Boolean) = if (start) controller.onIncomingCall("Test caller") else controller.onCallEnded()
}
