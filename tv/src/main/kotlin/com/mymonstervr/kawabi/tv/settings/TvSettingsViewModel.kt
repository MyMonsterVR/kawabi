package com.mymonstervr.kawabi.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AuthApi
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.update.AppUpdateChecker
import com.mymonstervr.kawabi.data.update.AppUpdateInfo
import com.mymonstervr.kawabi.tv.BuildConfig
import com.mymonstervr.kawabi.tv.update.TvUpdateDownloadState
import com.mymonstervr.kawabi.tv.update.TvUpdateStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TvUpdateCheckState {
    data object Idle : TvUpdateCheckState
    data object Checking : TvUpdateCheckState
    data object UpToDate : TvUpdateCheckState
    data class Available(val info: AppUpdateInfo) : TvUpdateCheckState
}

class TvSettingsViewModel(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val updateChecker: AppUpdateChecker,
    updateStateHolder: TvUpdateStateHolder,
) : ViewModel() {

    private val _email = MutableStateFlow<String?>(null)
    val email: StateFlow<String?> = _email.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    val downloadState: StateFlow<TvUpdateDownloadState> = updateStateHolder.state

    private val _updateCheckState = MutableStateFlow<TvUpdateCheckState>(TvUpdateCheckState.Idle)
    val updateCheckState: StateFlow<TvUpdateCheckState> = _updateCheckState.asStateFlow()

    init {
        viewModelScope.launch {
            authApi.me().onSuccess { _email.value = it.email }
        }
        // Silent background check on every Settings visit -- TV has no equivalent "just
        // opened the app" hook worth wiring separately since Settings is already where an
        // available update surfaces.
        checkForUpdate()
    }

    fun checkForUpdate(force: Boolean = false) {
        viewModelScope.launch {
            _updateCheckState.value = TvUpdateCheckState.Checking
            val result = updateChecker.check(forceCheck = force)
            _updateCheckState.value = result?.let(TvUpdateCheckState::Available) ?: TvUpdateCheckState.UpToDate
        }
    }

    /** Only kills this TV's own session -- the phone that paired it stays logged in. */
    fun signOut() {
        viewModelScope.launch {
            authApi.logout()
            tokenStore.clearToken()
        }
    }
}
