package com.aiyu.ridelink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.ble.ClusterLink
import com.aiyu.ridelink.core.ble.FoundCluster
import com.aiyu.ridelink.core.protocol.ClusterModel
import com.aiyu.ridelink.core.protocol.ClusterProtocol.NavStep
import com.aiyu.ridelink.core.protocol.Maneuver
import com.aiyu.ridelink.core.settings.Bike
import com.aiyu.ridelink.core.settings.GarageStore
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
    private val garage: GarageStore,
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

    val bikes = garage.bikes

    /** Saves the scooter and makes it the active one. */
    fun pair(cluster: FoundCluster) = viewModelScope.launch {
        link.stopScan()
        val bike = garage.bikes.value.find { it.address == cluster.address }?.copy(name = cluster.name)
            ?: Bike(cluster.address, cluster.name, model = null)
        garage.add(bike)
        activate(bike)
    }

    /** Switches to another saved scooter; AutoConnect connects to it once the old link is idle. */
    fun select(bike: Bike) = viewModelScope.launch {
        if (bike.address != repo.current().deviceAddress) activate(bike)
    }

    /** Removes a saved scooter; removing the active one switches to the next saved one, if any. */
    fun remove(bike: Bike) = viewModelScope.launch {
        garage.remove(bike.address)
        if (bike.address != repo.current().deviceAddress) return@launch
        val next = garage.bikes.value.firstOrNull()
        if (next != null) activate(next) else {
            controller.stopForBikeSwitch()
            repo.setDevice(null, null)
            repo.setModel(null)
        }
    }

    private suspend fun activate(bike: Bike) {
        if (repo.current().deviceAddress != null) controller.stopForBikeSwitch()
        userStopped = false
        repo.setModel(bike.model)
        repo.setDevice(bike.address, bike.name)
    }

    fun setModel(model: ClusterModel?) = viewModelScope.launch {
        repo.setModel(model)
        repo.current().deviceAddress?.let { garage.setModel(it, model) }
    }
    fun setRiderName(name: String) = viewModelScope.launch { repo.setRiderName(name) }
    fun setDisplayLines(line1: String, line2: String) = viewModelScope.launch { repo.setDisplayLines(line1, line2) }
    fun sendDisplayText() = controller.sendDisplayTextNow()

    /** Rendered custom line for the Settings preview. [tick] only forces a recompute as live values change. */
    @Suppress("UNUSED_PARAMETER")
    fun preview(template: String, tick: Int): String = controller.previewLine(template, settings.value)
    fun setForwardNavigation(on: Boolean) = viewModelScope.launch { repo.setForwardNavigation(on) }
    fun setForwardCalls(on: Boolean) = viewModelScope.launch { repo.setForwardCalls(on) }
    fun setShowSocial(on: Boolean) = viewModelScope.launch { repo.setShowSocial(on) }
    fun setShowWeather(on: Boolean) = viewModelScope.launch { repo.setShowWeather(on) }
    fun setShowLocation(on: Boolean) = viewModelScope.launch { repo.setShowLocation(on) }
    fun setShowMusic(on: Boolean) = viewModelScope.launch { repo.setShowMusic(on) }
    fun setCallButtons(on: Boolean) = viewModelScope.launch { repo.setCallButtons(on) }
    fun setRejectSms(on: Boolean) = viewModelScope.launch { repo.setRejectSms(on) }
    fun setRejectSmsText(text: String) = viewModelScope.launch { repo.setRejectSmsText(text) }
    fun setVoiceButton(on: Boolean) = viewModelScope.launch { repo.setVoiceButton(on) }
    fun setShowRideData(on: Boolean) = viewModelScope.launch { repo.setShowRideData(on) }
    fun setShowVehicle(on: Boolean) = viewModelScope.launch { repo.setShowVehicle(on) }
    fun setRecordRides(on: Boolean) = viewModelScope.launch { repo.setRecordRides(on) }
    fun setOverSpeedAlert(on: Boolean) = viewModelScope.launch { repo.setOverSpeedAlert(on) }
    fun setOverSpeedKmh(kmh: Int) = viewModelScope.launch { repo.setOverSpeedKmh(kmh) }
    fun setMissedCalls(on: Boolean) = viewModelScope.launch { repo.setMissedCalls(on) }

    fun setDemo(on: Boolean) = controller.setDemo(on)
    fun sendText(text: String) = controller.sendTestText(text)
    fun sendTest(which: ClusterController.TestFrame) = controller.sendTest(which)
    fun sendTestTurn() = controller.onNavigation(NavStep(250, 540, 3_400, Maneuver.RIGHT, "Turn right test"))
    fun testCall(start: Boolean) = if (start) controller.onIncomingCall("Test caller") else controller.onCallEnded()
}
