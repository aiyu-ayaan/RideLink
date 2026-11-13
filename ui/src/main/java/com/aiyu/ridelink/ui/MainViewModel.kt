package com.aiyu.ridelink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.ble.ClusterLink
import com.aiyu.ridelink.core.ble.FoundCluster
import com.aiyu.ridelink.core.protocol.ClusterModel
import com.aiyu.ridelink.core.protocol.ClusterProtocol.NavStep
import com.aiyu.ridelink.core.protocol.Maneuver
import com.aiyu.ridelink.core.diag.LogFile
import com.aiyu.ridelink.core.care.CareMonitor
import com.aiyu.ridelink.core.phone.RideCheckIn
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
    private val logFile: LogFile,
    private val care: CareMonitor,
    private val checkIn: RideCheckIn,
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
    val demoNav = controller.demoNav
    val arrowSweep = controller.arrowSweep
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
    fun setDisplayBrightness(level: Int) = viewModelScope.launch { repo.setDisplayBrightness(level); controller.applyDisplaySettings() }
    fun setSmsAlerts(on: Boolean) = viewModelScope.launch { repo.setSmsAlerts(on) }
    fun setMessageAlerts(on: Boolean) = viewModelScope.launch { repo.setMessageAlerts(on) }
    fun setMessageText(on: Boolean) = viewModelScope.launch { repo.setMessageText(on) }
    fun setMessageGroups(on: Boolean) = viewModelScope.launch { repo.setMessageGroups(on) }
    fun setMessageSource(key: String, on: Boolean) = viewModelScope.launch { repo.setMessageSource(key, on) }
    fun setLedClear(on: Boolean) = viewModelScope.launch { repo.setLedClear(on) }
    fun setDeliveryAlerts(on: Boolean) = viewModelScope.launch { repo.setDeliveryAlerts(on) }
    fun setDeliveryCode(code: Int) = viewModelScope.launch { repo.setDeliveryCode(code) }
    fun setCarbon(on: Boolean) = viewModelScope.launch { repo.setCarbon(on); controller.applyDisplaySettings() }
    val alertSweep = controller.alertSweep
    fun setAlertSweep(on: Boolean) = controller.setAlertSweep(on)
    fun setAutoReply(on: Boolean) = viewModelScope.launch { repo.setAutoReply(on) }
    fun setChatAutoReply(on: Boolean) = viewModelScope.launch { repo.setChatAutoReply(on) }
    fun setCheckIn(on: Boolean) = viewModelScope.launch { repo.setCheckIn(on) }
    fun setCheckInContact(number: String, name: String) = viewModelScope.launch { repo.setCheckInContact(number, name) }
    fun setBirthday(iso: String) = viewModelScope.launch { repo.setBirthday(iso); controller.applyDisplaySettings() }
    fun setPurchaseDate(iso: String) = viewModelScope.launch { repo.setPurchaseDate(iso); controller.applyDisplaySettings() }
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
    fun setLanguageTag(tag: String?) = viewModelScope.launch { repo.setLanguageTag(tag) }
    fun setOverSpeedAlert(on: Boolean) = viewModelScope.launch { repo.setOverSpeedAlert(on) }
    fun setOverSpeedKmh(kmh: Int) = viewModelScope.launch { repo.setOverSpeedKmh(kmh) }
    fun setMissedCalls(on: Boolean) = viewModelScope.launch { repo.setMissedCalls(on) }

    fun setDeveloperMode(on: Boolean) = viewModelScope.launch { repo.setDeveloperMode(on) }
    fun setSaveLogs(on: Boolean) = viewModelScope.launch { repo.setSaveLogs(on) }

    /** Saved log files, oldest first; empty until "Save logs" has written something. */
    fun savedLogs() = logFile.files()

    /** Writes every saved day into [out] (a file the rider picked). [done] gets false when nothing was saved yet. */
    fun exportLogs(out: java.io.OutputStream, done: (Boolean) -> Unit) = viewModelScope.launch { done(logFile.exportTo(out)) }
    fun clearLogs(done: () -> Unit) = viewModelScope.launch { logFile.clear(); done() }

    fun setDemo(on: Boolean) = controller.setDemo(on)
    fun setDemoNav(on: Boolean) = controller.setDemoNav(on)
    fun setArrowSweep(on: Boolean) = controller.setArrowSweep(on)
    fun sendText(text: String) = controller.sendTestText(text)
    fun sendTest(which: ClusterController.TestFrame) = controller.sendTest(which)
    fun sendTestTurn() = controller.onNavigation(NavStep(250, 540, 3_400, Maneuver.RIGHT, "Turn right test"))
    // Feature tests (Console): each runs the real store, display and notification path.
    val pretendRiding = controller.pretendRiding
    fun setPretendRiding(on: Boolean) = controller.setPretendRiding(on)
    fun testRefuel() = care.testRefuel()
    fun testLowFuel() = care.testLowFuel()
    fun testDue() = care.testDue()
    fun testCheckIn() = checkIn.sendTest()
    fun testParked() = controller.saveParkedNow()
    fun testCall(start: Boolean) = if (start) controller.onIncomingCall("Test caller") else controller.onCallEnded()
}
