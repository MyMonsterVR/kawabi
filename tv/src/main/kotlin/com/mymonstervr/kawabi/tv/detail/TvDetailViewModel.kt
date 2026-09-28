package com.mymonstervr.kawabi.tv.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeDetailResponse
import com.mymonstervr.kawabi.data.usecase.AddAnimeToLibrary
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository
import com.mymonstervr.kawabi.tv.common.resumeEpisode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TvDetailUiState {
    data object Loading : TvDetailUiState
    data class Loaded(val detail: AnimeDetailResponse, val resumeEpisodeKey: String?) : TvDetailUiState
    data class Error(val message: String) : TvDetailUiState
}

class TvDetailViewModel(
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val addAnimeToLibrary: AddAnimeToLibrary,
) : ViewModel() {

    private val _state = MutableStateFlow<TvDetailUiState>(TvDetailUiState.Loading)
    val state: StateFlow<TvDetailUiState> = _state.asStateFlow()

    private var loadedKey: String? = null

    fun load(key: String) {
        if (loadedKey == key) return
        loadedKey = key
        viewModelScope.launch {
            _state.value = TvDetailUiState.Loading
            animeApi.getAnime(key).fold(
                onSuccess = { detail ->
                    // Caches a non-favorite local row before anything else, same as the phone
                    // app's detail screen: playback and watch marks both write into a local
                    // episode row, so without this nothing watched here ever reaches Continue
                    // Watching or a linked tracker (see PlayerViewModel.resolveLocalEpisode).
                    addAnimeToLibrary.cache(detail)
                    val local = animeRepository.getByKey(key)
                    val resumeKey = local
                        ?.let { anime -> resumeEpisode(episodeRepository.getForAnime(anime.id))?.key }
                        ?: detail.episodes.minByOrNull { it.number }?.key
                    _state.value = TvDetailUiState.Loaded(detail, resumeKey)
                },
                onFailure = { e -> _state.value = TvDetailUiState.Error(e.message ?: "Couldn't load this title") },
            )
        }
    }
}
