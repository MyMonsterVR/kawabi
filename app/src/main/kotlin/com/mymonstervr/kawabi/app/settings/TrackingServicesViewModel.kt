package com.mymonstervr.kawabi.app.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.track.TrackerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrackerRowState(val id: String, val name: String, val connected: Boolean, val userName: String?)

class TrackingServicesViewModel(private val trackerManager: TrackerManager) : ViewModel() {

    // userName isn't itself a Flow (the trackers expose it as a plain getter over
    // encrypted/plain storage), so re-derive the whole row list whenever connection
    // state changes rather than trying to observe it directly.
    val rows: StateFlow<List<TrackerRowState>> = trackerManager.loggedInTrackerIds
        .map { ids -> trackerManager.trackers.map { t -> TrackerRowState(t.id, t.name, t.id in ids, t.userName) } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            trackerManager.trackers.map { t -> TrackerRowState(t.id, t.name, t.id in trackerManager.loggedInTrackerIds.value, t.userName) },
        )

    private val _kitsuLoginError = MutableStateFlow<String?>(null)
    val kitsuLoginError: StateFlow<String?> = _kitsuLoginError.asStateFlow()

    private val _kitsuLoggingIn = MutableStateFlow(false)
    val kitsuLoggingIn: StateFlow<Boolean> = _kitsuLoggingIn.asStateFlow()

    fun malAuthUrl(): Uri = trackerManager.myAnimeList.authUrl()

    fun kitsuLogin(username: String, password: String) {
        if (_kitsuLoggingIn.value) return
        viewModelScope.launch {
            _kitsuLoggingIn.value = true
            _kitsuLoginError.value = null
            trackerManager.kitsu.login(username, password)
                .onFailure { _kitsuLoginError.value = it.message ?: "Kitsu login failed" }
            _kitsuLoggingIn.value = false
        }
    }

    fun clearKitsuError() {
        _kitsuLoginError.value = null
    }

    fun logout(trackerId: String) {
        trackerManager.trackers.firstOrNull { it.id == trackerId }?.logout()
    }
}
