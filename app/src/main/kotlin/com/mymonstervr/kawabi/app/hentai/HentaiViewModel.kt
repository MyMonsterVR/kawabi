package com.mymonstervr.kawabi.app.hentai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_DEFAULT
import com.mymonstervr.kawabi.data.usecase.AdultSources
import com.mymonstervr.kawabi.domain.model.AnimeLibraryEntry
import com.mymonstervr.kawabi.domain.model.MangaWithUnreadCount
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.MangaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HentaiViewModel(
    mangaRepository: MangaRepository,
    animeRepository: AnimeRepository,
    adultSources: AdultSources,
    preferences: AppPreferences,
) : ViewModel() {

    val adultEnabled: StateFlow<Boolean?> = preferences.adultContentEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val mangaLibrary: StateFlow<List<MangaWithUnreadCount>> = mangaRepository.observeFavoritesWithUnreadCount(adult = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val animeLibrary: StateFlow<List<AnimeLibraryEntry>> = animeRepository.observeLibraryEntries(adult = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val gridColumns: StateFlow<Int> = preferences.libraryGridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LIBRARY_GRID_COLUMNS_DEFAULT)

    init {
        viewModelScope.launch {
            adultSources.mangaSources()
            adultSources.animeSources()
        }
    }
}
