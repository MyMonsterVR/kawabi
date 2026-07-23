package com.mymonstervr.kawabi.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.BuildConfig
import com.mymonstervr.kawabi.app.update.AppUpdateChecker
import com.mymonstervr.kawabi.app.update.AppUpdateInfo
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.ReadingDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class Available(val info: AppUpdateInfo) : UpdateCheckState
}

class SettingsViewModel(
    private val preferences: AppPreferences,
    tokenStore: TokenStore,
    private val updateChecker: AppUpdateChecker,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = tokenStore.isLoggedIn

    val currentVersion: String = BuildConfig.VERSION_NAME

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.Checking
            val result = updateChecker.check(forceCheck = true)
            _updateCheckState.value = result?.let(UpdateCheckState::Available) ?: UpdateCheckState.UpToDate
        }
    }

    val readingDirection: StateFlow<ReadingDirection> = preferences.readingDirection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingDirection.VERTICAL)

    val markReadOnScroll: StateFlow<Boolean> = preferences.markReadOnScroll
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val keepScreenAwake: StateFlow<Boolean> = preferences.keepScreenAwake
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val accentIndex: StateFlow<Int> = preferences.accentIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setAccentIndex(index: Int) {
        viewModelScope.launch { preferences.setAccentIndex(index) }
    }

    fun setReadingDirection(direction: ReadingDirection) {
        viewModelScope.launch { preferences.setReadingDirection(direction) }
    }

    fun setMarkReadOnScroll(enabled: Boolean) {
        viewModelScope.launch { preferences.setMarkReadOnScroll(enabled) }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        viewModelScope.launch { preferences.setKeepScreenAwake(enabled) }
    }
}
