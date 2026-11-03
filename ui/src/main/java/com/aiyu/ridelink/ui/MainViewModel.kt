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
    fun setForwardNavigation(on: Boolean) = viewModelScope.launch { repo.setForwardNavigation(on) }
    fun setForwardCalls(on: Boolean) = viewModelScope.launch { repo.setForwardCalls(on) }
    fun setForwardMessages(on: Boolean) = viewModelScope.launch { repo.setForwardMessages(on) }

    fun setDemo(on: Boolean) = controller.setDemo(on)
    fun sendText(text: String) = controller.sendTestText(text)
    fun sendTestTurn() = controller.onNavigation(NavStep(250, 540, 3_400, Maneuver.RIGHT, "Turn right test"))
    fun testCall(start: Boolean) = if (start) controller.onIncomingCall("Test caller") else controller.onCallEnded()
}
