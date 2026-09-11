package com.mymonstervr.kawabi.app.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_DEFAULT
import com.mymonstervr.kawabi.data.usecase.AnimeSyncClient
import com.mymonstervr.kawabi.data.usecase.RefreshAnimeEpisodes
import com.mymonstervr.kawabi.domain.model.AnimeWithUnwatchedCount
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AnimeLibraryViewModel(
    private val animeRepository: AnimeRepository,
    private val refreshAnimeEpisodes: RefreshAnimeEpisodes,
    private val animeSyncClient: AnimeSyncClient,
    preferences: AppPreferences,
) : ViewModel() {

    val favorites: StateFlow<List<AnimeWithUnwatchedCount>> = animeRepository.observeFavoritesWithUnwatchedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Same column count as the manga grids -- one density knob for every cover grid.
    val gridColumns: StateFlow<Int> = preferences.libraryGridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LIBRARY_GRID_COLUMNS_DEFAULT)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // No `/anime/batch` equivalent on the backend, so this is one request per favorite --
    // acceptable for an explicit pull-to-refresh (the user asked for it and is watching
    // it happen); the scheduled background job uses the smart-interval skip logic instead.
    fun refreshAll() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                for (anime in animeRepository.getFavorites()) {
                    refreshAnimeEpisodes.refresh(anime)
                }
                animeSyncClient.sync()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
