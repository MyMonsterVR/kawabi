package com.mymonstervr.kawabi.app.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.SourceApi
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.network.dto.AnimeDetailResponse
import com.mymonstervr.kawabi.data.track.TrackerManager
import com.mymonstervr.kawabi.data.track.dto.TrackSearchResult
import com.mymonstervr.kawabi.data.usecase.AddAnimeToLibrary
import com.mymonstervr.kawabi.data.usecase.AnimeSyncClient
import com.mymonstervr.kawabi.data.usecase.AnimeTrackerSyncClient
import com.mymonstervr.kawabi.data.usecase.RefreshAnimeEpisodes
import com.mymonstervr.kawabi.domain.model.AnimeTrack
import com.mymonstervr.kawabi.domain.model.Episode
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.AnimeTrackRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnimeDetailState {
    data object Loading : AnimeDetailState
    data class Success(val anime: AnimeDetailResponse) : AnimeDetailState
    data class Error(val message: String) : AnimeDetailState
}

sealed interface AnimeTrackerSheetState {
    data object Hidden : AnimeTrackerSheetState
    data class Shown(val rows: List<AnimeTrackerLinkRow>) : AnimeTrackerSheetState
}

data class AnimeTrackerLinkRow(
    val trackerId: String,
    val trackerName: String,
    val linked: AnimeTrack?,
    val searching: Boolean = false,
    val searchResults: List<TrackSearchResult>? = null,
    val searchError: String? = null,
)

private const val UNKNOWN_EPISODE_NUMBER = -1.0

class AnimeDetailViewModel(
    private val animeApi: AnimeApi,
    private val sourceApi: SourceApi,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val addAnimeToLibrary: AddAnimeToLibrary,
    private val refreshAnimeEpisodes: RefreshAnimeEpisodes,
    private val tokenStore: TokenStore,
    private val animeSyncClient: AnimeSyncClient,
    private val trackerManager: TrackerManager,
    private val animeTrackRepository: AnimeTrackRepository,
    private val animeTrackerSyncClient: AnimeTrackerSyncClient,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = tokenStore.isLoggedIn

    private val _state = MutableStateFlow<AnimeDetailState>(AnimeDetailState.Loading)
    val state: StateFlow<AnimeDetailState> = _state.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Episode key -> local Episode row, once the anime is in the library. Playback needs a
    // local row to write position/watched into, so this is what an episode tap resolves
    // against, exactly like the manga screen's localChaptersByUrl.
    private val _localEpisodesByKey = MutableStateFlow<Map<String, Episode>>(emptyMap())
    val localEpisodesByKey: StateFlow<Map<String, Episode>> = _localEpisodesByKey.asStateFlow()

    private val _trackerSheet = MutableStateFlow<AnimeTrackerSheetState>(AnimeTrackerSheetState.Hidden)
    val trackerSheet: StateFlow<AnimeTrackerSheetState> = _trackerSheet.asStateFlow()

    private val _altTitleSuggestions = MutableStateFlow<List<String>>(emptyList())
    val altTitleSuggestions: StateFlow<List<String>> = _altTitleSuggestions.asStateFlow()

    private val _lastTitle = MutableStateFlow("")
    val lastTitle: StateFlow<String> = _lastTitle.asStateFlow()

    private var localAnimeId: Long? = null
    private var loadedKey: String? = null

    fun load(key: String) {
        if (loadedKey == key && _state.value is AnimeDetailState.Success) return
        loadedKey = key
        viewModelScope.launch {
            _state.value = AnimeDetailState.Loading
            animeApi.getAnime(key)
                .onSuccess { response ->
                    _state.value = AnimeDetailState.Success(response)
                    if (response.title.isNotBlank()) _lastTitle.value = response.title
                    resolveLocalFavoriteState(key)
                }
                .onFailure { _state.value = AnimeDetailState.Error(it.message ?: "Failed to load") }
        }
    }

    /** Local-only, no network -- refreshes what playback wrote while the screen was away. */
    fun onResume() {
        if (localAnimeId == null) return
        viewModelScope.launch { refreshLocalEpisodes() }
    }

    fun refresh(key: String) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            animeApi.getAnime(key).onSuccess { _state.value = AnimeDetailState.Success(it) }
            val animeId = localAnimeId
            if (animeId != null) {
                animeRepository.getById(animeId)?.let { refreshAnimeEpisodes.refresh(it) }
                refreshLocalEpisodes()
            }
            _isRefreshing.value = false
        }
    }

    fun toggleFavorite(key: String) {
        viewModelScope.launch {
            if (_isFavorite.value) {
                localAnimeId?.let { animeRepository.setFavorite(it, false) }
                _isFavorite.value = false
                animeApi.deleteEntry(key)
            } else {
                addAnimeToLibrary.add(key).onSuccess { anime ->
                    localAnimeId = anime.id
                    _isFavorite.value = true
                    refreshLocalEpisodes()
                    animeSyncClient.sync()
                }
            }
        }
    }

    fun setEpisodeWatched(episodeId: Long, watched: Boolean) {
        viewModelScope.launch {
            episodeRepository.setWatched(episodeId, watched)
            refreshLocalEpisodes()
            pushTrackers()
        }
    }

    /** Marks every episode numbered before [target] as watched -- not including it. */
    fun markPreviousAsWatched(target: Episode) {
        if (target.episodeNumber == UNKNOWN_EPISODE_NUMBER) return
        viewModelScope.launch {
            _localEpisodesByKey.value.values
                .filter { it.episodeNumber != UNKNOWN_EPISODE_NUMBER && it.episodeNumber < target.episodeNumber && !it.watched }
                .forEach { episodeRepository.setWatched(it.id, true) }
            refreshLocalEpisodes()
            pushTrackers()
        }
    }

    fun openTrackerSheet() {
        viewModelScope.launch { refreshTrackerSheet() }
    }

    fun closeTrackerSheet() {
        _trackerSheet.value = AnimeTrackerSheetState.Hidden
    }

    fun loadAltTitleSuggestions(title: String) {
        _altTitleSuggestions.value = emptyList()
        viewModelScope.launch {
            sourceApi.getAltTitles(title).onSuccess { _altTitleSuggestions.value = it }
        }
    }

    fun searchTracker(trackerId: String, query: String) {
        updateTrackerRow(trackerId) { it.copy(searching = true, searchError = null) }
        viewModelScope.launch {
            animeTrackerSyncClient.search(trackerId, query)
                .onSuccess { results -> updateTrackerRow(trackerId) { it.copy(searching = false, searchResults = results) } }
                .onFailure { e -> updateTrackerRow(trackerId) { it.copy(searching = false, searchError = e.message ?: "Search failed") } }
        }
    }

    fun linkTracker(trackerId: String, result: TrackSearchResult) {
        val animeId = localAnimeId ?: return
        viewModelScope.launch {
            animeTrackerSyncClient.link(animeId, trackerId, result)
                .onSuccess {
                    refreshLocalEpisodes()
                    refreshTrackerSheet()
                }
                .onFailure { e -> updateTrackerRow(trackerId) { it.copy(searchError = e.message ?: "Link failed") } }
        }
    }

    fun unlinkTracker(trackerId: String) {
        val animeId = localAnimeId ?: return
        viewModelScope.launch {
            animeTrackerSyncClient.unlink(animeId, trackerId)
            refreshTrackerSheet()
        }
    }

    /** Manual episode/status/score edit -- explicitly allowed to lower the count. */
    fun updateTrackDetails(track: AnimeTrack, episodesWatched: Double, status: String, score: Double, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = animeTrackerSyncClient.updateTrackDetails(track, episodesWatched, status, score)
            if (result.isSuccess) {
                refreshLocalEpisodes()
                refreshTrackerSheet()
            }
            onResult(result)
        }
    }

    private suspend fun refreshTrackerSheet() {
        _trackerSheet.value = AnimeTrackerSheetState.Shown(buildTrackerRows())
    }

    private suspend fun buildTrackerRows(): List<AnimeTrackerLinkRow> {
        val animeId = localAnimeId ?: return emptyList()
        val linkedByTracker = animeTrackRepository.getForAnime(animeId).associateBy { it.trackerId }
        return trackerManager.loggedInTrackers().map { tracker ->
            AnimeTrackerLinkRow(trackerId = tracker.id, trackerName = tracker.name, linked = linkedByTracker[tracker.id])
        }
    }

    private fun updateTrackerRow(trackerId: String, transform: (AnimeTrackerLinkRow) -> AnimeTrackerLinkRow) {
        val current = _trackerSheet.value as? AnimeTrackerSheetState.Shown ?: return
        _trackerSheet.value = current.copy(rows = current.rows.map { if (it.trackerId == trackerId) transform(it) else it })
    }

    // Monotonic-max pull, fired on open only -- never after an explicit local watch-state
    // edit, which pushLocalEpisodesWatched handles instead (see the manga side for why
    // mixing the two would silently re-mark a manually-unwatched episode).
    private fun syncTrackers() {
        val animeId = localAnimeId ?: return
        viewModelScope.launch {
            runCatching { animeTrackerSyncClient.syncAnime(animeId) }
            refreshLocalEpisodes()
        }
    }

    private fun pushTrackers() {
        val animeId = localAnimeId ?: return
        viewModelScope.launch { runCatching { animeTrackerSyncClient.pushLocalEpisodesWatched(animeId) } }
    }

    private suspend fun resolveLocalFavoriteState(key: String) {
        val existing = animeRepository.getByKey(key)
        localAnimeId = existing?.id
        _isFavorite.value = existing?.favorite == true
        refreshLocalEpisodes()
        syncTrackers()
    }

    private suspend fun refreshLocalEpisodes() {
        val animeId = localAnimeId ?: return
        _localEpisodesByKey.value = episodeRepository.getForAnime(animeId).associateBy { it.key }
    }
}

/**
 * Where "Continue"/"Start watching" should resume: a part-watched episode (lowest-numbered
 * if several) takes priority over the next unwatched one. Null once every numbered episode
 * is watched. Simpler than the manga equivalent -- episodes have no scanlator/version axis
 * to tiebreak on, so the lowest number wins outright.
 */
fun resumeEpisode(episodes: Collection<Episode>): Episode? {
    val numbered = episodes.filter { it.episodeNumber != UNKNOWN_EPISODE_NUMBER }
    return numbered.filter { !it.watched && it.positionMs > 0 }.minByOrNull { it.episodeNumber }
        ?: numbered.filter { !it.watched }.minByOrNull { it.episodeNumber }
}
