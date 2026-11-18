package com.mymonstervr.kawabi.app.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_DEFAULT
import com.mymonstervr.kawabi.data.usecase.AnimeLibraryUpdateManager
import com.mymonstervr.kawabi.data.usecase.AnimeNewReleases
import com.mymonstervr.kawabi.data.usecase.AnimeIdentityMatcher
import com.mymonstervr.kawabi.data.usecase.AnimeSyncClient
import com.mymonstervr.kawabi.data.usecase.RefreshAnimeBatch
import com.mymonstervr.kawabi.data.usecase.RefreshAnimeEpisodes
import com.mymonstervr.kawabi.domain.model.AUDIO_DUB
import com.mymonstervr.kawabi.domain.model.AUDIO_SUB
import com.mymonstervr.kawabi.domain.model.AnimeLibraryEntry
import com.mymonstervr.kawabi.domain.model.AnimeWatchStatus
import com.mymonstervr.kawabi.domain.model.NewEpisode
import com.mymonstervr.kawabi.domain.model.effectiveShowAudio
import com.mymonstervr.kawabi.domain.model.forAudio
import com.mymonstervr.kawabi.domain.model.normalizeAnimeTitle
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
const val FEED_DAYS_DEFAULT = 90
private const val FEED_DAYS_STEP = 30
const val FEED_DAYS_MAX = 365
const val RELEASES_PAGE_SIZE = 9

data class FeedEpisode(
    val key: String,
    val episodeKey: String,
    val title: String,
    val coverUrl: String?,
    val coverFallbackUrl: String?,
    val source: String,
    val number: Double,
    val dateUpload: Long,
    val animeKey: String = "",
)

data class EpisodesUi(val state: SectionState<FeedEpisode>, val days: Int, val fromFeed: Boolean)

private data class FeedLoad(
    val days: Int = FEED_DAYS_DEFAULT,
    val episodes: List<FeedEpisode>? = null,
    val error: Boolean = false,
)

data class ReleasesUi(
    val cards: List<AnimeNewReleases.ReleaseCard> = emptyList(),
    val started: Boolean = false,
    val loading: Boolean = false,
    val checkingAudio: Boolean = false,
    val canGrow: Boolean = true,
    val failed: Boolean = false,
    val filter: String = "",
)

class AnimeViewModel(
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val refreshAnimeBatch: RefreshAnimeBatch,
    private val animeSyncClient: AnimeSyncClient,
    private val animeLibraryUpdateManager: AnimeLibraryUpdateManager,
    private val animeApi: AnimeApi,
    private val identityMatcher: AnimeIdentityMatcher,
    private val animeNewReleases: AnimeNewReleases,
    private val preferences: AppPreferences,
    private val refreshAnimeEpisodes: RefreshAnimeEpisodes,
) : ViewModel() {

    fun openKeyFor(card: AnimeCardDto, onResolved: (String) -> Unit) =
        resolveAnimeOpenKey(identityMatcher, card, onResolved)

    val entries: StateFlow<List<AnimeLibraryEntry>> = animeRepository.observeLibraryEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val gridColumns: StateFlow<Int> = preferences.libraryGridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LIBRARY_GRID_COLUMNS_DEFAULT)

    private val newEpisodesSince = System.currentTimeMillis() - NEW_EPISODE_WINDOW_MS

    /** Only statuses the user is actually following -- a dropped show's new episode isn't news. */
    private val localEpisodes: StateFlow<SectionState<FeedEpisode>> =
        combine(episodeRepository.observeRecentUnwatched(newEpisodesSince, NEW_EPISODE_MAX), entries) { episodes, library ->
            val followed = library
                .filter { it.status == AnimeWatchStatus.WATCHING || it.status == AnimeWatchStatus.PLAN_TO_WATCH }
                .mapTo(mutableSetOf()) { it.anime.id }
            episodes.filter { it.animeId in followed }
        }
            .map<List<NewEpisode>, SectionState<FeedEpisode>> { list ->
                SectionState.Loaded(
                    list.map {
                        FeedEpisode(
                            key = "e${it.episodeId}",
                            episodeKey = it.episodeKey,
                            title = it.animeTitle,
                            coverUrl = it.animeThumbnailUrl,
                            coverFallbackUrl = null,
                            source = it.animeSource,
                            number = it.episodeNumber,
                            dateUpload = it.dateUpload,
                        )
                    },
                )
            }
            .catch { emit(SectionState.Error(it.message ?: "Couldn't load episodes")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectionState.Loading)

    private val _feed = MutableStateFlow(FeedLoad())
    private var feedJob: Job? = null

    /** Feed data once the call has succeeded; the local list only as an offline fallback. */
    val newEpisodes: StateFlow<EpisodesUi> = combine(_feed, localEpisodes) { feed, local ->
        when {
            feed.episodes != null -> EpisodesUi(SectionState.Loaded(feed.episodes), feed.days, true)
            feed.error -> {
                val localItems = (local as? SectionState.Loaded)?.items.orEmpty()
                if (localItems.isNotEmpty()) EpisodesUi(local, feed.days, false)
                else EpisodesUi(SectionState.Error("Couldn't load new episodes."), feed.days, false)
            }
            else -> EpisodesUi(SectionState.Loading, feed.days, false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpisodesUi(SectionState.Loading, FEED_DAYS_DEFAULT, false))

    private val releaseToken = MutableStateFlow(0)
    private val releaseTarget = MutableStateFlow(0)

    val newReleases: StateFlow<ReleasesUi> = combine(
        animeNewReleases.state,
        preferences.animeReleaseAudio,
        entries,
    ) { pool, filter, library ->
        val inLibrary = library.mapTo(mutableSetOf()) { normalizeAnimeTitle(it.anime.title) }
        val visible = pool.cards.filter { it.norm !in inLibrary && it.fullNorm !in inLibrary }
        val filtered = when (filter) {
            AUDIO_SUB, AUDIO_DUB -> visible.filter { it.audio == filter }
            else -> visible
        }
        val sorted = if (filter == AUDIO_DUB) filtered.sortedByDescending { it.dubSince } else filtered
        ReleasesUi(
            cards = sorted,
            started = pool.started,
            loading = pool.loading,
            checkingAudio = filter.isNotEmpty() && visible.any { it.audio == null },
            canGrow = pool.hasMore,
            failed = !pool.loading && pool.started && !pool.anySuccess && (pool.sourcesFailed || !pool.hasMore),
            filter = filter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReleasesUi())

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
        viewModelScope.launch {
            preferences.animeFeedNextOnly.collectLatest { loadFeed(_feed.value.days) }
        }
        viewModelScope.launch {
            combine(releaseTarget, releaseToken, preferences.animeReleaseAudio) { target, token, filter ->
                Triple(target, token, filter)
            }.collectLatest { (target, token, filter) ->
                if (target <= 0) return@collectLatest
                val norms = entries.value.mapTo(mutableSetOf()) { normalizeAnimeTitle(it.anime.title) }
                animeNewReleases.fill(target, filter, norms, token)
            }
        }
        viewModelScope.launch {
            combine(
                preferences.animeReleaseAudio,
                animeNewReleases.state.map { it.cards.size }.distinctUntilChanged(),
            ) { filter, _ -> filter }.collectLatest { animeNewReleases.resolveAudio(it) }
        }
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
     * screen's Continue button, over the episodes of the audio the user watches this show
     * in. Answers null when there's nothing left to watch, which the caller turns into
     * opening the details page instead.
     */
    // The feed can list an episode newer than the locally stored rows. The player only
    // saves progress for episodes it finds locally, so the listing is refreshed first.
    fun openFeedEpisode(episode: FeedEpisode, onEpisode: (String) -> Unit, onAnime: (String) -> Unit) {
        viewModelScope.launch {
            if (episode.animeKey.isNotEmpty() && episodeRepository.getByKey(episode.episodeKey) == null) {
                val anime = animeRepository.getByKey(episode.animeKey)
                val stored = anime != null && refreshAnimeEpisodes.refresh(anime).isSuccess &&
                    episodeRepository.getByKey(episode.episodeKey) != null
                if (!stored) {
                    onAnime(episode.animeKey)
                    return@launch
                }
            }
            onEpisode(episode.episodeKey)
        }
    }

    fun resolveContinueEpisode(animeId: Long, onResolved: (String?) -> Unit) {
        viewModelScope.launch {
            val anime = animeRepository.getById(animeId)
            val showPref = anime?.let { preferences.animeShowAudio(it.key).first() }.orEmpty()
            val audio = effectiveShowAudio(showPref, preferences.animePreferredAudio.first())
            onResolved(resumeEpisode(episodeRepository.getForAnime(animeId).forAudio(audio))?.key)
        }
    }

    // Batched via /anime/cached (same as AnimeLibraryUpdateManager.updateDue()) instead of
    // one live GET /anime per favorite -- that sequential loop reintroduced the exact
    // rate-limit-prone pattern the manga side was already fixed for (SyncClient's push()
    // doc comment), and made pull-to-refresh the slowest thing in the app for any real
    // library size.
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                loadFeed(_feed.value.days)
                if (releaseTarget.value > 0) releaseToken.value += 1
                refreshAnimeBatch.refresh(animeRepository.getFavorites())
                animeSyncClient.sync()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun retryReleases() {
        releaseToken.value += 1
        releaseTarget.value = maxOf(releaseTarget.value, RELEASES_PAGE_SIZE + 1)
    }

    fun retryNewEpisodes() = loadFeed(_feed.value.days)

    fun showMoreDays() {
        loadFeed((_feed.value.days + FEED_DAYS_STEP).coerceAtMost(FEED_DAYS_MAX))
    }

    fun requestReleases(count: Int) {
        if (count > releaseTarget.value) releaseTarget.value = count
    }

    fun setReleaseAudio(value: String) {
        viewModelScope.launch { preferences.setAnimeReleaseAudio(value) }
    }

    private fun loadFeed(days: Int) {
        feedJob?.cancel()
        _feed.update { it.copy(days = days, error = false) }
        feedJob = viewModelScope.launch {
            val nextOnly = preferences.animeFeedNextOnly.first()
            val limit = if (days > 90) 500 else 120
            animeApi.getFeed(days, limit, nextOnly)
                .onSuccess { response ->
                    _feed.update {
                        it.copy(
                            episodes = response.episodes.map { ep ->
                                FeedEpisode(
                                    key = "f${ep.episode_key}",
                                    episodeKey = ep.episode_key,
                                    title = ep.displayTitle ?: ep.anime_title,
                                    coverUrl = ep.cover_url,
                                    coverFallbackUrl = ep.coverFallbackUrl,
                                    source = ep.source,
                                    number = ep.number,
                                    dateUpload = ep.date_upload,
                                    animeKey = ep.anime_key,
                                )
                            },
                            error = false,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _feed.update { it.copy(error = true) }
                }
        }
    }
}
