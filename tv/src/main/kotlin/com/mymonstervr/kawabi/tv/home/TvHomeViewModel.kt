package com.mymonstervr.kawabi.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.model.Episode
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository
import com.mymonstervr.kawabi.tv.common.resumeEpisode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One favorited anime with enough episode state to render a continue-watching card. */
data class ContinueWatchingItem(
    val anime: Anime,
    val resumeEpisode: Episode,
)

/**
 * Home is intentionally NOT organized by source (no "Popular on Anikoto" rows) -- per the
 * plan's locked decision, "New Releases" fans [AnimeApi.browse] with sort="latest" across
 * every enabled+latest-capable source in parallel (same fan-out shape AnimeApi.search already
 * uses server-side for search), then merges and dedupes by key. Per-source browsing belongs
 * on a future Browse screen instead, mirroring kawabi-web's browse/page.tsx.
 *
 * Continue-watching reads the *local* DB (synced from the backend by TvNavHost's bootstrap),
 * same source of truth as the phone app -- not a direct network call -- since resume position
 * and watched-state live there (see PlayerViewModel.resolveLocalEpisode).
 */
class TvHomeViewModel(
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
) : ViewModel() {

    private val _continueWatching = MutableStateFlow<List<ContinueWatchingItem>>(emptyList())
    val continueWatching: StateFlow<List<ContinueWatchingItem>> = _continueWatching.asStateFlow()

    private val _newReleases = MutableStateFlow<List<AnimeCardDto>>(emptyList())
    val newReleases: StateFlow<List<AnimeCardDto>> = _newReleases.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            val continueWatchingJob = async { loadContinueWatching() }
            val newReleasesJob = async { loadNewReleases() }
            awaitAll(continueWatchingJob, newReleasesJob)
            _isLoading.value = false
        }
    }

    private suspend fun loadContinueWatching() {
        val favorites = animeRepository.getFavorites().sortedByDescending { it.lastWatchedAt }
        _continueWatching.value = favorites.mapNotNull { anime ->
            resumeEpisode(episodeRepository.getForAnime(anime.id))?.let { episode -> ContinueWatchingItem(anime, episode) }
        }
    }

    private suspend fun loadNewReleases() {
        val sources = animeApi.getSources().getOrNull()?.sources.orEmpty()
            .filter { it.enabled && it.supports_latest }
        if (sources.isEmpty()) return
        // Bounded per-source, same reasoning as kawabi-web's NewReleasesRail: without this,
        // AnimeApi.browse's own client-level read timeout (20s) is the only thing standing
        // between one slow/contended source and the whole row staying empty that long, even
        // when every other source already answered in well under a second.
        val perSource = coroutineScope {
            sources.map { source ->
                async { withTimeoutOrNull(BROWSE_TIMEOUT_MS) { animeApi.browse(source.key, "latest", 1).getOrNull()?.results } }
            }.awaitAll()
        }
        _newReleases.value = perSource.filterNotNull().flatten().distinctBy { it.key }
    }

    private companion object {
        const val BROWSE_TIMEOUT_MS = 8_000L
    }
}
