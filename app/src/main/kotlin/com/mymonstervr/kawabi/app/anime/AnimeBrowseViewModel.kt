package com.mymonstervr.kawabi.app.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_DEFAULT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AnimeBrowseSort(val apiValue: String, val label: String) {
    POPULAR("popular", "Popular"),
    LATEST("latest", "Latest"),
}

class AnimeBrowseViewModel(
    private val animeApi: AnimeApi,
    preferences: AppPreferences,
) : ViewModel() {

    val gridColumns: StateFlow<Int> = preferences.libraryGridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LIBRARY_GRID_COLUMNS_DEFAULT)

    private var sourceKey: String = ""

    private val _sourceName = MutableStateFlow("")
    val sourceName: StateFlow<String> = _sourceName.asStateFlow()

    private val _supportsLatest = MutableStateFlow(true)
    val supportsLatest: StateFlow<Boolean> = _supportsLatest.asStateFlow()

    private val _sort = MutableStateFlow(AnimeBrowseSort.POPULAR)
    val sort: StateFlow<AnimeBrowseSort> = _sort.asStateFlow()

    private val _items = MutableStateFlow<List<AnimeCardDto>>(emptyList())
    val items: StateFlow<List<AnimeCardDto>> = _items.asStateFlow()

    private var page = 1
    private var hasNext = true

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loaded = false

    // Guarded by `loaded` for the same reason BrowseViewModel is: the nav entry's view
    // model survives recomposition and LaunchedEffect can refire on a config change,
    // and reloading would reset scroll position.
    fun load(key: String) {
        if (loaded && sourceKey == key) return
        loaded = true
        sourceKey = key

        viewModelScope.launch {
            animeApi.getSources().onSuccess { response ->
                response.sources.firstOrNull { it.key == key }?.let {
                    _sourceName.value = it.name
                    _supportsLatest.value = it.supports_latest
                }
            }
        }
        fetchFirstPage()
    }

    fun setSort(newSort: AnimeBrowseSort) {
        if (_sort.value == newSort || _isLoading.value) return
        _sort.value = newSort
        fetchFirstPage()
    }

    private fun fetchFirstPage() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            page = 1
            animeApi.browse(sourceKey, _sort.value.apiValue, page)
                .onSuccess {
                    _items.value = it.results
                    hasNext = it.has_next_page
                }
                .onFailure { _error.value = it.message ?: "Failed to load" }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (!hasNext || _isLoadingMore.value || _isLoading.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            animeApi.browse(sourceKey, _sort.value.apiValue, page + 1)
                .onSuccess {
                    page += 1
                    _items.value = _items.value + it.results
                    hasNext = it.has_next_page
                }
                // Leave hasNext alone on failure so scrolling back to the edge retries
                // instead of looking finished -- same as BrowseViewModel.
                .onFailure { }
            _isLoadingMore.value = false
        }
    }
}
