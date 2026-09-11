package com.mymonstervr.kawabi.app.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import com.mymonstervr.kawabi.data.network.dto.AnimeSourceDto
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_DEFAULT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// PLAN-anime.md decision D4: Anikoto is the default/main source, so it leads the chip row
// regardless of the order the backend happens to declare sources in.
private const val PRIMARY_SOURCE_NAME = "anikoto"

class AnimeSearchViewModel(
    private val animeApi: AnimeApi,
    preferences: AppPreferences,
) : ViewModel() {

    val gridColumns: StateFlow<Int> = preferences.libraryGridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LIBRARY_GRID_COLUMNS_DEFAULT)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<AnimeCardDto>>(emptyList())
    val results: StateFlow<List<AnimeCardDto>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Chip row for Browse. Same silent-failure UX as the manga search screen: a logged-out
    // or unreachable /anime/sources just leaves the row unrendered rather than showing an
    // error over an otherwise usable search box.
    private val _sources = MutableStateFlow<List<AnimeSourceDto>>(emptyList())
    val sources: StateFlow<List<AnimeSourceDto>> = _sources.asStateFlow()

    init {
        viewModelScope.launch {
            animeApi.getSources().onSuccess { response ->
                _sources.value = response.sources
                    .filter { it.enabled }
                    .sortedByDescending { it.name.lowercase() == PRIMARY_SOURCE_NAME }
            }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun search() {
        val trimmed = _query.value.trim()
        if (trimmed.isEmpty() || _isSearching.value) return
        viewModelScope.launch {
            _isSearching.value = true
            _error.value = null
            animeApi.search(trimmed)
                .onSuccess { _results.value = it.results }
                .onFailure { _error.value = it.message ?: "Search failed" }
            _isSearching.value = false
        }
    }
}
