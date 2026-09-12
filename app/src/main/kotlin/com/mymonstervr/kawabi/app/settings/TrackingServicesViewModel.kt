package com.mymonstervr.kawabi.app.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.track.BrowserOAuthTracker
import com.mymonstervr.kawabi.data.track.TrackerManager
import com.mymonstervr.kawabi.data.track.kitsu.KitsuTracker
import com.mymonstervr.kawabi.data.usecase.ImportAnimeFromTracker
import com.mymonstervr.kawabi.data.usecase.ImportSummary
import com.mymonstervr.kawabi.domain.model.TrackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * [browserLogin] = this tracker connects through an external browser redirect rather than
 * the in-app Kitsu form, so the row's Connect button just needs [TrackingServicesViewModel.authUrlFor].
 */
data class TrackerRowState(
    val id: String,
    val name: String,
    val connected: Boolean,
    val userName: String?,
    val browserLogin: Boolean,
)

/**
 * One dialog's worth of anime-list-import state: status picker -> running -> result/error.
 * Held in the ViewModel rather than in the composable so a rotation mid-import (the request
 * is allowed up to ~2.5 minutes) neither restarts it nor loses the result sheet.
 */
data class AnimeImportState(
    val trackerId: String,
    val trackerName: String,
    val statuses: Set<String> = setOf(TrackStatus.WATCHING),
    val running: Boolean = false,
    val summary: ImportSummary? = null,
    val error: String? = null,
)

class TrackingServicesViewModel(
    private val trackerManager: TrackerManager,
    private val importAnimeFromTracker: ImportAnimeFromTracker,
) : ViewModel() {

    // userName isn't itself a Flow (the trackers expose it as a plain getter over
    // encrypted/plain storage), so re-derive the whole row list whenever connection
    // state changes rather than trying to observe it directly.
    val rows: StateFlow<List<TrackerRowState>> = trackerManager.loggedInTrackerIds
        .map { ids -> rowsFor(ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), rowsFor(trackerManager.loggedInTrackerIds.value))

    private fun rowsFor(connectedIds: Set<String>): List<TrackerRowState> =
        trackerManager.trackers.map { tracker ->
            TrackerRowState(
                id = tracker.id,
                name = tracker.name,
                connected = tracker.id in connectedIds,
                userName = tracker.userName,
                browserLogin = tracker is BrowserOAuthTracker,
            )
        }

    private val _kitsuLoginError = MutableStateFlow<String?>(null)
    val kitsuLoginError: StateFlow<String?> = _kitsuLoginError.asStateFlow()

    private val _kitsuLoggingIn = MutableStateFlow(false)
    val kitsuLoggingIn: StateFlow<Boolean> = _kitsuLoggingIn.asStateFlow()

    private val _animeImport = MutableStateFlow<AnimeImportState?>(null)
    val animeImport: StateFlow<AnimeImportState?> = _animeImport.asStateFlow()

    fun startAnimeImport(trackerId: String, trackerName: String) {
        _animeImport.value = AnimeImportState(trackerId = trackerId, trackerName = trackerName)
    }

    fun toggleAnimeImportStatus(status: String) {
        val state = _animeImport.value ?: return
        if (state.running) return
        val statuses = if (status in state.statuses) state.statuses - status else state.statuses + status
        _animeImport.value = state.copy(statuses = statuses)
    }

    fun runAnimeImport() {
        val state = _animeImport.value ?: return
        if (state.running || state.statuses.isEmpty()) return
        _animeImport.value = state.copy(running = true, error = null, summary = null)
        viewModelScope.launch {
            importAnimeFromTracker.import(state.trackerId, state.statuses.toList())
                .onSuccess { summary ->
                    _animeImport.value = _animeImport.value?.copy(running = false, summary = summary)
                }
                .onFailure { error ->
                    _animeImport.value = _animeImport.value?.copy(running = false, error = error.message ?: "Import failed")
                }
        }
    }

    fun dismissAnimeImport() {
        if (_animeImport.value?.running == true) return
        _animeImport.value = null
    }

    /** Authorize URL for a browser-OAuth tracker, or null if that tracker logs in in-app. */
    fun authUrlFor(trackerId: String): Uri? = (trackerManager.byId(trackerId) as? BrowserOAuthTracker)?.authUrl()

    fun kitsuLogin(username: String, password: String) {
        if (_kitsuLoggingIn.value) return
        viewModelScope.launch {
            _kitsuLoggingIn.value = true
            _kitsuLoginError.value = null
            val kitsu = trackerManager.trackers.filterIsInstance<KitsuTracker>().firstOrNull()
            kitsu?.login(username, password)
                ?.onFailure { _kitsuLoginError.value = it.message ?: "Kitsu login failed" }
            _kitsuLoggingIn.value = false
        }
    }

    fun clearKitsuError() {
        _kitsuLoginError.value = null
    }

    fun logout(trackerId: String) {
        trackerManager.byId(trackerId)?.logout()
    }
}
