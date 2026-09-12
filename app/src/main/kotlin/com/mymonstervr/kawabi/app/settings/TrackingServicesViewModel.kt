package com.mymonstervr.kawabi.app.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.track.BrowserOAuthTracker
import com.mymonstervr.kawabi.data.track.TrackerManager
import com.mymonstervr.kawabi.data.track.TrackerStatusState
import com.mymonstervr.kawabi.data.track.kitsu.KitsuTracker
import com.mymonstervr.kawabi.data.usecase.AutoImportAnimeFromTrackers
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
    val expired: Boolean,
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
    val matchedProcessed: Int? = null,
    val matchedTotal: Int? = null,
    val fetchingCount: Int? = null,
    val summary: ImportSummary? = null,
    val error: String? = null,
)

class TrackingServicesViewModel(
    private val trackerManager: TrackerManager,
    private val importAnimeFromTracker: ImportAnimeFromTracker,
    private val autoImportAnimeFromTrackers: AutoImportAnimeFromTrackers,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val animeAutoImportEnabled: StateFlow<Boolean> = appPreferences.animeAutoImportEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setAnimeAutoImportEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setAnimeAutoImportEnabled(enabled) }
    }

    // userName isn't itself a Flow (the trackers expose it as a plain getter over
    // encrypted/plain storage), so re-derive the whole row list whenever connection
    // state changes rather than trying to observe it directly.
    val rows: StateFlow<List<TrackerRowState>> = trackerManager.statuses
        .map { statuses -> rowsFor(statuses) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), rowsFor(trackerManager.statuses.value))

    init {
        // Actively re-checks each tracker upstream every time this screen opens, unlike
        // the app-launch/SyncWorker refresh which is throttled to once per 6h.
        viewModelScope.launch { trackerManager.refresh(verify = true) }
    }

    private fun rowsFor(statuses: Map<String, TrackerStatusState>): List<TrackerRowState> =
        trackerManager.trackers.map { tracker ->
            val status = statuses[tracker.id]
            TrackerRowState(
                id = tracker.id,
                name = tracker.name,
                connected = status?.connected == true,
                expired = status?.expired == true,
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
        _animeImport.value = state.copy(
            running = true,
            matchedProcessed = null,
            matchedTotal = null,
            fetchingCount = null,
            error = null,
            summary = null,
        )
        viewModelScope.launch {
            importAnimeFromTracker.import(
                state.trackerId,
                state.statuses.toList(),
                onMatched = { processed, total ->
                    _animeImport.value = _animeImport.value?.copy(matchedProcessed = processed, matchedTotal = total)
                },
                onFetchingDetails = { count ->
                    _animeImport.value = _animeImport.value?.copy(fetchingCount = count)
                },
            )
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
            val result = kitsu?.login(username, password)
            result?.onFailure { _kitsuLoginError.value = it.message ?: "Kitsu login failed" }
            _kitsuLoggingIn.value = false
            if (kitsu != null && result?.isSuccess == true) {
                // Fire-and-forget: non-blocking for the login dialog, same reasoning as the
                // OAuth redirect handler's own auto-import trigger in MainActivity.
                viewModelScope.launch {
                    autoImportAnimeFromTrackers.run(listOf(kitsu.id), force = true)
                }
            }
        }
    }

    fun clearKitsuError() {
        _kitsuLoginError.value = null
    }

    fun logout(trackerId: String) {
        trackerManager.byId(trackerId)?.logout()
    }
}
