package com.mymonstervr.kawabi.tv.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TvSearchViewModel(
    private val animeApi: AnimeApi,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<AnimeCardDto>>(emptyList())
    val results: StateFlow<List<AnimeCardDto>> = _results.asStateFlow()

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
            animeApi.search(trimmed)
                .onSuccess { _results.value = it.results }
                .onFailure { _error.value = it.message ?: "Search failed" }
            _isSearching.value = false
        }
    }
}
