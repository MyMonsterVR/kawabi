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
import com.mymonstervr.kawabi.data.usecase.AnimeIdentityMatcher
import com.mymonstervr.kawabi.data.usecase.RefreshAnimeEpisodes
import com.mymonstervr.kawabi.data.usecase.SwitchAnimeSource
import com.mymonstervr.kawabi.domain.model.AnimeTrack
import com.mymonstervr.kawabi.domain.model.animeIdentityOf
import com.mymonstervr.kawabi.domain.model.Episode
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.AnimeTrackRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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

/**
 * The library row that is the same show as the key currently open, when that key isn't the
 * library row's own (PLAN-anime.md section 18) -- i.e. the user reached a show they already
 * have from a different source.
 */
data class AnimeLibraryMatch(
    val animeId: Long,
    val key: String,
    val sourceName: String,
)

data class AnimeSourceOption(
    val key: String,
    val sourceName: String,
    val coverUrl: String?,
)

sealed interface AnimeSourceOptionsState {
    data object Idle : AnimeSourceOptionsState
    data object Loading : AnimeSourceOptionsState
    data class Loaded(val options: List<AnimeSourceOption>, val selected: String) : AnimeSourceOptionsState
    data class Error(val message: String) : AnimeSourceOptionsState
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
    private val identityMatcher: AnimeIdentityMatcher,
    private val switchAnimeSource: SwitchAnimeSource,
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

    // Non-fatal: the details loaded but the episode list couldn't be stored, so no row is
    // playable. Shown inline above the list instead of leaving a dead list behind.
    private val _episodeError = MutableStateFlow<String?>(null)
    val episodeError: StateFlow<String?> = _episodeError.asStateFlow()

    private val _libraryMatch = MutableStateFlow<AnimeLibraryMatch?>(null)
    val libraryMatch: StateFlow<AnimeLibraryMatch?> = _libraryMatch.asStateFlow()

    private val _sourceOptions = MutableStateFlow<AnimeSourceOptionsState>(AnimeSourceOptionsState.Idle)
    val sourceOptions: StateFlow<AnimeSourceOptionsState> = _sourceOptions.asStateFlow()

    private val _trackerSheet = MutableStateFlow<AnimeTrackerSheetState>(AnimeTrackerSheetState.Hidden)
    val trackerSheet: StateFlow<AnimeTrackerSheetState> = _trackerSheet.asStateFlow()

    private val _altTitleSuggestions = MutableStateFlow<List<String>>(emptyList())
    val altTitleSuggestions: StateFlow<List<String>> = _altTitleSuggestions.asStateFlow()

    private val _lastTitle = MutableStateFlow("")
    val lastTitle: StateFlow<String> = _lastTitle.asStateFlow()

    // The key actually loaded, which can drift from the nav argument after a silent
    // identity redirect or a source switch -- the screen watches this to fix up the
    // back-stack entry so rotating/returning keeps the chosen source (PLAN-anime.md
    // section 18 follow-up).
    private val _openKey = MutableStateFlow<String?>(null)
    val openKey: StateFlow<String?> = _openKey.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events

    private var localAnimeId: Long? = null
    private var loadedKey: String? = null
    private var lastFailedSwitchKey: String? = null

    fun load(key: String) {
        if (loadedKey == key && _state.value is AnimeDetailState.Success) return
        loadedKey = key
        viewModelScope.launch {
            _state.value = AnimeDetailState.Loading
            animeApi.getAnime(key)
                .onSuccess { response -> applyLoadedDetail(response) }
                .onFailure { _state.value = AnimeDetailState.Error(it.message ?: "Failed to load") }
        }
    }

    // Every opened key gets a local anime row (non-favorite unless it is already in the
    // library) and its episodes stored before the list renders: playback and watch marks
    // both write into a local episode row, so without this nothing on the screen is
    // tappable for a show that isn't in the library yet.
    //
    // Before that, check for a non-favorite local row under a different key: that means
    // the user previously switched this (not-yet-favorited) show to another source, and
    // the key just loaded is stale (e.g. the same search/browse card tapped again, or a
    // back-stack entry that predates the switch). Redirect silently onto that row instead
    // of caching a second one under the stale key. A favorite match is left alone here --
    // resolveLibraryMatch below surfaces that case as the "in your library from X" banner
    // instead, so the user chooses rather than being moved automatically.
    private suspend fun applyLoadedDetail(response: AnimeDetailResponse) {
        val redirect = identityMatcher.findAny(response.title, excludeKey = response.key)
        if (redirect != null && !redirect.favorite && redirect.key != response.key) {
            loadedKey = redirect.key
            _openKey.value = redirect.key
            animeApi.getAnime(redirect.key)
                .onSuccess { applyLoadedDetailForKey(it) }
                .onFailure { _state.value = AnimeDetailState.Error(it.message ?: "Failed to load") }
            return
        }
        applyLoadedDetailForKey(response)
    }

    private suspend fun applyLoadedDetailForKey(response: AnimeDetailResponse) {
        loadedKey = response.key
        _openKey.value = response.key
        _state.value = AnimeDetailState.Success(response)
        if (response.title.isNotBlank()) _lastTitle.value = response.title
        addAnimeToLibrary.cache(response)
            .onSuccess { _episodeError.value = null }
            .onFailure { _episodeError.value = it.message ?: "Couldn't load episodes for this source" }
        resolveLocalFavoriteState(response.key)
        resolveLibraryMatch(response)
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
            // loadedKey, not the screen's argument: a source switch moved this row to
            // another key without the nav argument changing.
            animeApi.getAnime(loadedKey ?: key).onSuccess { _state.value = AnimeDetailState.Success(it) }
            val animeId = localAnimeId
            if (animeId != null) {
                animeRepository.getById(animeId)?.let { refreshAnimeEpisodes.refresh(it) }
                refreshLocalEpisodes()
            }
            _isRefreshing.value = false
        }
    }

    fun toggleFavorite(animeKey: String) {
        val key = loadedKey ?: animeKey
        viewModelScope.launch {
            if (_isFavorite.value) {
                localAnimeId?.let { animeRepository.setFavorite(it, false) }
                _isFavorite.value = false
                animeApi.deleteEntry(key)
            } else {
                val response = (_state.value as? AnimeDetailState.Success)?.anime
                val result = if (response != null) {
                    addAnimeToLibrary.addWithDetail(response)
                } else {
                    addAnimeToLibrary.add(key)
                }
                result.onSuccess { anime ->
                    // An identity match means the show is already in the library under
                    // another source's key: the banner takes over from here instead of a
                    // second favorite being created.
                    if (anime.key != key) {
                        resolveLibraryMatch(response)
                        return@onSuccess
                    }
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

    /** "Use this source instead": moves the library row onto the key currently open. */
    fun useOpenedSource() {
        val match = _libraryMatch.value ?: return
        val key = loadedKey ?: return
        viewModelScope.launch {
            switchAnimeSource.switch(match.animeId, key)
                .onSuccess { anime ->
                    _libraryMatch.value = null
                    localAnimeId = anime.id
                    _isFavorite.value = true
                    _sourceOptions.value = AnimeSourceOptionsState.Idle
                    refreshLocalEpisodes()
                }
                .onFailure { _episodeError.value = it.message ?: "Couldn't switch source" }
        }
    }

    /**
     * Other sources carrying this same show, found by searching the enabled sources for its
     * title and keeping the results whose identity matches. On demand (the pill's first tap)
     * because it fans out across every enabled source.
     */
    fun loadSourceOptions() {
        val current = (_state.value as? AnimeDetailState.Success)?.anime ?: return
        if (_sourceOptions.value is AnimeSourceOptionsState.Loading) return
        viewModelScope.launch {
            _sourceOptions.value = AnimeSourceOptionsState.Loading
            animeApi.search(_lastTitle.value.ifBlank { current.title })
                .onSuccess { response ->
                    val target = animeIdentityOf(current.title)
                    val here = AnimeSourceOption(
                        key = current.key,
                        sourceName = current.source_name.ifBlank { current.source },
                        coverUrl = current.cover_url,
                    )
                    val others = response.results
                        .filter { it.key != current.key && it.source != current.source && animeIdentityOf(it.title).matches(target) }
                        .distinctBy { it.source.ifBlank { it.key } }
                        .map {
                            AnimeSourceOption(
                                key = it.key,
                                sourceName = it.source_name.ifBlank { it.source },
                                coverUrl = it.cover_url,
                            )
                        }
                    _sourceOptions.value = AnimeSourceOptionsState.Loaded(listOf(here) + others, current.key)
                }
                .onFailure { _sourceOptions.value = AnimeSourceOptionsState.Error(it.message ?: "Couldn't load sources") }
        }
    }

    /** Source pill pick on a library entry -- switches the row itself onto [key]. */
    fun selectSource(key: String) {
        val animeId = localAnimeId ?: return
        if (loadedKey == key) return
        val targetName = sourceOptionName(key)
        viewModelScope.launch {
            _sourceOptions.value = AnimeSourceOptionsState.Loading
            switchAnimeSource.switch(animeId, key)
                .onSuccess {
                    lastFailedSwitchKey = null
                    _sourceOptions.value = AnimeSourceOptionsState.Idle
                    animeApi.getAnime(key)
                        .onSuccess { response -> applyLoadedDetail(response) }
                        .onFailure { _state.value = AnimeDetailState.Error(it.message ?: "Failed to load") }
                    _events.emit("Now using $targetName")
                }
                .onFailure { e ->
                    lastFailedSwitchKey = key
                    _sourceOptions.value = AnimeSourceOptionsState.Error(e.message ?: "Couldn't switch source")
                    _events.emit("Couldn't switch to $targetName: ${e.message ?: "unknown error"} — tap to retry")
                }
        }
    }

    fun retryLastSwitch() {
        lastFailedSwitchKey?.let { selectSource(it) }
    }

    private fun sourceOptionName(key: String): String {
        val loaded = _sourceOptions.value as? AnimeSourceOptionsState.Loaded
        return loaded?.options?.firstOrNull { it.key == key }?.sourceName ?: "this source"
    }

    private suspend fun resolveLibraryMatch(response: AnimeDetailResponse?) {
        if (response == null || _isFavorite.value) {
            _libraryMatch.value = null
            return
        }
        val match = identityMatcher.findFavorite(response.title, excludeKey = response.key)
        _libraryMatch.value = match?.let {
            AnimeLibraryMatch(animeId = it.id, key = it.key, sourceName = sourceNameFor(it.source))
        }
    }

    // /anime/sources is the cheap inventory (no live probing), and its entries carry both
    // the site key and the engine source id that anime keys are built from.
    private suspend fun sourceNameFor(sourceId: String): String {
        if (sourceId.isBlank()) return "another source"
        val sources = animeApi.getSources().getOrNull()?.sources ?: return sourceId
        return sources.firstOrNull { it.id == sourceId || it.key == sourceId }?.name ?: sourceId
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
