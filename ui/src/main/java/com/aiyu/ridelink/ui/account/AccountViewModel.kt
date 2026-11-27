package com.aiyu.ridelink.ui.account

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.ridelink.core.account.Account
import com.aiyu.ridelink.core.account.AccountRepository
import com.aiyu.ridelink.core.account.SignInResult
import com.aiyu.ridelink.core.settings.SettingsRepository
import com.aiyu.ridelink.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val available = accounts.available

    val account: StateFlow<Account?> = accounts.account.stateIn(viewModelScope, SharingStarted.Eagerly, accounts.current())

    /** null until DataStore answers, so a returning rider never sees the welcome screen flash. */
    val showWelcome: StateFlow<Boolean?> = settings.settings
        .map { available && !it.welcomeDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val photo: StateFlow<ImageBitmap?> = account
        .map { it?.photoUrl }
        .distinctUntilChanged()
        .mapLatest { url -> url?.let { accounts.photo(it) }?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var busy by mutableStateOf(false)
        private set

    /** String resource of the last sign-in problem, cleared on the next try. */
    var error by mutableStateOf<Int?>(null)
        private set

    fun signIn(activity: Context) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            error = when (accounts.signIn(activity)) {
                SignInResult.Done, SignInResult.Cancelled -> null
                SignInResult.NoAccount -> R.string.account_err_no_account
                is SignInResult.Failed -> R.string.account_err_failed
            }
            busy = false
        }
    }

    fun signOut() = viewModelScope.launch { accounts.signOut() }

    fun finishWelcome() = viewModelScope.launch { settings.setWelcomeDone() }
}
