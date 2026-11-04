package com.aiyu.ridelink.ui.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.ridelink.core.settings.Settings
import com.aiyu.ridelink.core.settings.SettingsRepository
import com.aiyu.ridelink.core.update.AppUpdater
import com.aiyu.ridelink.core.update.UpdateChannel
import com.aiyu.ridelink.core.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updater: AppUpdater,
    private val repo: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<Settings> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val state: StateFlow<UpdateState> = updater.state
    val offerShownInline: MutableStateFlow<Boolean> = updater.offerShownInline
    val installedName: String get() = updater.installedName
    val supported: Boolean get() = updater.supported

    fun channel(s: Settings): UpdateChannel = s.updateChannel ?: UpdateChannel.forVersion(installedName)

    /** App start: silent, at most hourly, skipped when switched off or snoozed. */
    fun checkQuietly() { viewModelScope.launch { updater.check() } }
    fun check() { viewModelScope.launch { updater.check(manual = true) } }

    fun download(s: UpdateState.Available) = updater.download(s.release, s.apk)
    fun install(s: UpdateState.Ready) = updater.install(s.file)
    fun notNow() = updater.snooze()
    fun dismiss() = updater.dismiss()

    fun canInstall() = updater.canInstall()
    fun installPermissionIntent(): Intent = updater.installPermissionIntent()

    fun setEnabled(on: Boolean) { viewModelScope.launch { repo.setUpdatesEnabled(on); if (!on) updater.dismiss() } }

    fun setChannel(c: UpdateChannel) {
        viewModelScope.launch {
            repo.setUpdateChannel(c)
            updater.dismiss() // what was on offer came from the old channel
            updater.check(manual = true, channel = c)
        }
    }
}
