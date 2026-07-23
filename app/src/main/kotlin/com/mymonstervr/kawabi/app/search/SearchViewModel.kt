package com.mymonstervr.kawabi.app.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.SourceApi
import com.mymonstervr.kawabi.data.network.dto.SearchResultDto
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.LibraryCardSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val sourceApi: SourceApi,
    preferences: AppPreferences,
) : ViewModel() {

    // Same card-size preference as the library grid -- one density knob, not a
    // second independent setting for a visually identical grid.
    val cardSize: StateFlow<LibraryCardSize> = preferences.libraryCardSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryCardSize.MEDIUM)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResultDto>>(emptyList())
    val results: StateFlow<List<SearchResultDto>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun search() {
        val trimmed = _query.value.trim()
        if (trimmed.isEmpty() || _isSearching.value) return
        viewModelScope.launch {
            _isSearching.value = true
            _error.value = null
            sourceApi.search(trimmed)
                .onSuccess { _results.value = it.results }
                .onFailure { _error.value = it.message ?: "Search failed" }
            _isSearching.value = false
        }
    }
}
