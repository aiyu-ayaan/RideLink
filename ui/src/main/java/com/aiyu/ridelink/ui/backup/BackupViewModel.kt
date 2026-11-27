package com.aiyu.ridelink.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.ridelink.core.account.Account
import com.aiyu.ridelink.core.account.AccountRepository
import com.aiyu.ridelink.core.backup.BackupMode
import com.aiyu.ridelink.core.backup.BackupProgress
import com.aiyu.ridelink.core.backup.BackupRepository
import com.aiyu.ridelink.core.backup.BackupStatus
import com.aiyu.ridelink.core.settings.BackupFrequency
import com.aiyu.ridelink.core.settings.Settings
import com.aiyu.ridelink.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupRepository,
    accounts: AccountRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val available = backup.available
    val status: StateFlow<BackupStatus> = backup.status
    val progress: StateFlow<BackupProgress?> = backup.progress
    val queued: StateFlow<Boolean> = backup.queued.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val account: StateFlow<Account?> = accounts.account.stateIn(viewModelScope, SharingStarted.Eagerly, accounts.current())
    val prefs: StateFlow<Settings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    fun backUpNow() = backup.enqueue(BackupMode.BACKUP)

    /** Merge / use cloud / use phone / delete: queued in WorkManager like "Back up now", so it finishes after leaving the app. */
    fun run(mode: BackupMode) = backup.enqueue(mode)

    fun setFrequency(f: BackupFrequency) = viewModelScope.launch { settings.setBackupFrequency(f) }
    fun setWifiOnly(on: Boolean) = viewModelScope.launch { settings.setBackupWifiOnly(on) }
}
