package com.mymonstervr.kawabi.app.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_DEFAULT
import com.mymonstervr.kawabi.data.usecase.RefreshLibraryBatch
import com.mymonstervr.kawabi.domain.model.MangaWithUnreadCount
import com.mymonstervr.kawabi.domain.repository.MangaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    mangaRepository: MangaRepository,
    private val refreshLibraryBatch: RefreshLibraryBatch,
    preferences: AppPreferences,
) : ViewModel() {

    val favorites: StateFlow<List<MangaWithUnreadCount>> = mangaRepository.observeFavoritesWithUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val gridColumns: StateFlow<Int> = preferences.libraryGridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LIBRARY_GRID_COLUMNS_DEFAULT)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // RefreshLibraryBatch (POST /manga/batch) fans out server-side instead of the client
    // doing one GET /manga per favorite -- previously this ordered caught-up manga first
    // (only way to discover a new chapter) since a large library couldn't beat the
    // backend's sustained per-request rate limit no matter how much client concurrency
    // there was. Batching sidesteps that limiter entirely (one request in), so there's no
    // longer a rate-limit budget to ration by priority order.
    fun refreshAll() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                refreshLibraryBatch.refresh(favorites.value.map { it.manga })
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
