package com.mymonstervr.kawabi.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeSourceDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnimeSourcesState {
    data object Loading : AnimeSourcesState
    data class Success(val sources: List<AnimeSourceDto>) : AnimeSourcesState
    data class Error(val message: String) : AnimeSourcesState
}

class AnimeSourcesViewModel(private val animeApi: AnimeApi) : ViewModel() {

    private val _state = MutableStateFlow<AnimeSourcesState>(AnimeSourcesState.Loading)
    val state: StateFlow<AnimeSourcesState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = AnimeSourcesState.Loading
            animeApi.getSources()
                .onSuccess { _state.value = AnimeSourcesState.Success(it.sources) }
                .onFailure { _state.value = AnimeSourcesState.Error(it.message ?: "Failed to load sources") }
        }
    }

    fun toggle(key: String, enabled: Boolean) {
        val current = _state.value as? AnimeSourcesState.Success ?: return
        // Optimistic update, revert on failure -- same as the manga sources page.
        _state.value = current.copy(sources = current.sources.map { if (it.key == key) it.copy(enabled = enabled) else it })
        viewModelScope.launch {
            animeApi.setSourceEnabled(key, enabled).onFailure {
                _state.value = current
            }
        }
    }
}
