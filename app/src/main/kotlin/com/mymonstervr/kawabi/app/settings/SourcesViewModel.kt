package com.mymonstervr.kawabi.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.SourceApi
import com.mymonstervr.kawabi.data.network.dto.SourceToggleDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SourcesState {
    data object Loading : SourcesState
    data class Success(val sources: List<SourceToggleDto>) : SourcesState
    data class Error(val message: String) : SourcesState
}

class SourcesViewModel(private val sourceApi: SourceApi) : ViewModel() {

    private val _state = MutableStateFlow<SourcesState>(SourcesState.Loading)
    val state: StateFlow<SourcesState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = SourcesState.Loading
            sourceApi.getSources()
                .onSuccess { _state.value = SourcesState.Success(it.sources) }
                .onFailure { _state.value = SourcesState.Error(it.message ?: "Failed to load sources") }
        }
    }

    fun toggle(key: String, enabled: Boolean) {
        val current = _state.value as? SourcesState.Success ?: return
        // Optimistic update, matching kawabi-web's SourceToggles -- revert on failure.
        _state.value = current.copy(sources = current.sources.map { if (it.key == key) it.copy(enabled = enabled) else it })
        viewModelScope.launch {
            sourceApi.setSourceEnabled(key, enabled).onFailure {
                _state.value = current
            }
        }
    }
}
