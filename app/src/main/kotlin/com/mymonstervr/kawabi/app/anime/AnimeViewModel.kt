package com.mymonstervr.kawabi.app.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_DEFAULT
import com.mymonstervr.kawabi.data.usecase.AnimeLibraryUpdateManager
import com.mymonstervr.kawabi.data.usecase.AnimeIdentityMatcher
import com.mymonstervr.kawabi.data.usecase.AnimeSyncClient
import com.mymonstervr.kawabi.data.usecase.RefreshAnimeEpisodes
import com.mymonstervr.kawabi.domain.model.AnimeLibraryEntry
import com.mymonstervr.kawabi.domain.model.AnimeWatchStatus
import com.mymonstervr.kawabi.domain.model.NewEpisode
import com.mymonstervr.kawabi.domain.model.normalizeAnimeTitle
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SectionState<out T> {
    data object Loading : SectionState<Nothing>
    data class Loaded<T>(val items: List<T>) : SectionState<T>
    data class Error(val message: String) : SectionState<Nothing>
}

enum class AnimeTab(val label: String) {
    HOME("Home"),
    WATCHING("Watching"),
    LIBRARY("Library"),
}

enum class AnimeLibrarySort(val label: String) {
    LAST_WATCHED("Last watched"),
    TITLE("Title"),
    RECENTLY_ADDED("Recently added"),
    UNWATCHED_COUNT("Unwatched count"),
}

enum class AnimeStatusFilter(val label: String, val status: AnimeWatchStatus?) {
    ALL("All", null),
    WATCHING("Watching", AnimeWatchStatus.WATCHING),
    COMPLETED("Completed", AnimeWatchStatus.COMPLETED),
    PLAN_TO_WATCH("Plan to watch", AnimeWatchStatus.PLAN_TO_WATCH),
    ON_HOLD("On hold", AnimeWatchStatus.ON_HOLD),
    DROPPED("Dropped", AnimeWatchStatus.DROPPED),
}

const val CONTINUE_RAIL_MAX = 15
private const val NEW_EPISODE_WINDOW_MS = 14 * 24 * 60 * 60 * 1000L
private const val NEW_EPISODE_MAX = 40L

class AnimeViewModel(
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val refreshAnimeEpisodes: RefreshAnimeEpisodes,
    private val animeSyncClient: AnimeSyncClient,
    private val animeLibraryUpdateManager: AnimeLibraryUpdateManager,
    private val animeApi: AnimeApi,
    private val identityMatcher: AnimeIdentityMatcher,
    preferences: AppPreferences,
) : ViewModel() {

    fun openKeyFor(card: AnimeCardDto, onResolved: (String) -> Unit) =
        resolveAnimeOpenKey(identityMatcher, card, onResolved)

    val entries: StateFlow<List<AnimeLibraryEntry>> = animeRepository.observeLibraryEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val gridColumns: StateFlow<Int> = preferences.libraryGridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LIBRARY_GRID_COLUMNS_DEFAULT)

    private val newEpisodesSince = System.currentTimeMillis() - NEW_EPISODE_WINDOW_MS
    private val _episodesRetry = MutableStateFlow(0)

    /** Only statuses the user is actually following -- a dropped show's new episode isn't news. */
    val newEpisodes: StateFlow<SectionState<NewEpisode>> =
        combine(episodeRepository.observeRecentUnwatched(newEpisodesSince, NEW_EPISODE_MAX), entries, _episodesRetry) { episodes, library, _ ->
            val followed = library
                .filter { it.status == AnimeWatchStatus.WATCHING || it.status == AnimeWatchStatus.PLAN_TO_WATCH }
                .mapTo(mutableSetOf()) { it.anime.id }
            episodes.filter { it.animeId in followed }
        }
            .map<List<NewEpisode>, SectionState<NewEpisode>> { SectionState.Loaded(it) }
            .catch { emit(SectionState.Error(it.message ?: "Couldn't load episodes")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectionState.Loading)

    private val _releases = MutableStateFlow<SectionState<AnimeCardDto>>(SectionState.Loading)

    /**
     * "New releases": every enabled source's first page of latest, concatenated in source
     * order. Keys are per-source (`<sourceId>:<url>`) so the same show from two sources
     * can't be deduped on them -- normalized titles are the only cross-source identity
     * available, and anything already in the library belongs in "New episodes for you"
     * instead of here.
     */
    val newReleases: StateFlow<SectionState<AnimeCardDto>> = combine(_releases, entries) { state, library ->
        if (state !is SectionState.Loaded) return@combine state
        val inLibrary = library.mapTo(mutableSetOf()) { normalizeAnimeTitle(it.anime.title) }
        val seen = mutableSetOf<String>()
        SectionState.Loaded(
            state.items.filter { card ->
                val normalized = normalizeAnimeTitle(card.title).ifBlank { card.key }
                normalized !in inLibrary && seen.add(normalized)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectionState.Loading)

    private val _tab = MutableStateFlow(AnimeTab.HOME)
    val tab: StateFlow<AnimeTab> = _tab.asStateFlow()

    private val _sort = MutableStateFlow(AnimeLibrarySort.LAST_WATCHED)
    val sort: StateFlow<AnimeLibrarySort> = _sort.asStateFlow()

    private val _statusFilter = MutableStateFlow(AnimeStatusFilter.ALL)
    val statusFilter: StateFlow<AnimeStatusFilter> = _statusFilter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadReleases()
        // Episode refresh on open, but through the update manager's own per-anime
        // next_update schedule rather than a blanket refresh -- every fetch is an engine
        // call through WARP, so opening the tab repeatedly must not multiply them.
        viewModelScope.launch { runCatching { animeLibraryUpdateManager.updateDue() } }
    }

    fun selectTab(value: AnimeTab) {
        _tab.value = value
    }

    fun setSort(value: AnimeLibrarySort) {
        _sort.value = value
    }

    fun setStatusFilter(value: AnimeStatusFilter) {
        _statusFilter.value = value
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /**
     * Resolves where a "continue" tap should land, using the same rule as the detail
     * screen's Continue button. Answers null when there's nothing left to watch, which the
     * caller turns into opening the details page instead.
     */
    fun resolveContinueEpisode(animeId: Long, onResolved: (String?) -> Unit) {
        viewModelScope.launch {
            onResolved(resumeEpisode(episodeRepository.getForAnime(animeId))?.key)
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                for (anime in animeRepository.getFavorites()) {
                    refreshAnimeEpisodes.refresh(anime)
                }
                animeSyncClient.sync()
                loadReleases()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun retryReleases() = loadReleases()

    fun retryNewEpisodes() {
        _episodesRetry.value += 1
    }

    // Sequential rather than parallel: the backend's catalog routes are rate limited, and
    // a source that fails is skipped instead of emptying the whole rail.
    private fun loadReleases() {
        viewModelScope.launch {
            _releases.value = SectionState.Loading
            val sourcesResult = animeApi.getSources()
            val sources = sourcesResult.getOrNull()?.sources?.filter { it.enabled && it.supports_latest }
            if (sources == null) {
                _releases.value = SectionState.Error(
                    sourcesResult.exceptionOrNull()?.message ?: "Couldn't load releases",
                )
                return@launch
            }
            val cards = mutableListOf<AnimeCardDto>()
            for (source in sources) {
                animeApi.browse(source.key, "latest", 1).onSuccess { cards += it.results }
            }
            _releases.value = SectionState.Loaded(cards)
        }
    }
}
