package com.mymonstervr.kawabi.app.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import com.mymonstervr.kawabi.data.network.dto.AnimeSourceDto
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_DEFAULT
import com.mymonstervr.kawabi.data.usecase.AnimeIdentityMatcher
import com.mymonstervr.kawabi.domain.model.AnimeIdentity
import com.mymonstervr.kawabi.domain.model.animeIdentityOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// PLAN-anime.md decision D4: Anikoto is the default/main source, so it leads the chip row
// regardless of the order the backend happens to declare sources in.
private const val PRIMARY_SOURCE_NAME = "anikoto"

data class AnimeSearchGroup(val primary: AnimeCardDto, val sourceCount: Int)

private class GroupBuilder(val primary: AnimeCardDto) {
    val titleIdentity = animeIdentityOf(primary.title)
    val fullIdentity: AnimeIdentity? = primary.fullTitle?.takeIf { it.isNotBlank() }?.let { animeIdentityOf(it) }
    val canonicalIds = mutableSetOf<Long>()
    var count = 1

    init {
        primary.canonicalId?.takeIf { it > 0 }?.let { canonicalIds += it }
    }

    fun accepts(card: AnimeCardDto): Boolean {
        val id = card.canonicalId
        if (id != null && id > 0 && id in canonicalIds) return true
        val cardTitle = animeIdentityOf(card.title)
        if (cardTitle.matches(titleIdentity)) return true
        val cardFull = card.fullTitle?.takeIf { it.isNotBlank() }?.let { animeIdentityOf(it) }
        if (cardFull != null && cardFull.matches(titleIdentity)) return true
        return fullIdentity != null && fullIdentity.matches(cardTitle)
    }

    fun add(card: AnimeCardDto) {
        count++
        card.canonicalId?.takeIf { it > 0 }?.let { canonicalIds += it }
    }
}

private fun groupSearchResults(results: List<AnimeCardDto>): List<AnimeSearchGroup> {
    val groups = mutableListOf<GroupBuilder>()
    for (card in results) {
        val existing = groups.firstOrNull { it.accepts(card) }
        if (existing != null) existing.add(card) else groups += GroupBuilder(card)
    }
    return groups.map { AnimeSearchGroup(it.primary, it.count) }
}

class AnimeSearchViewModel(
    private val animeApi: AnimeApi,
    private val identityMatcher: AnimeIdentityMatcher,
    preferences: AppPreferences,
    private val adult: Boolean = false,
) : ViewModel() {

    fun openKeyFor(card: AnimeCardDto, onResolved: (String) -> Unit) =
        resolveAnimeOpenKey(identityMatcher, card, onResolved)

    val gridColumns: StateFlow<Int> = preferences.libraryGridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LIBRARY_GRID_COLUMNS_DEFAULT)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<AnimeSearchGroup>>(emptyList())
    val results: StateFlow<List<AnimeSearchGroup>> = _results.asStateFlow()

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
                    .filter { it.enabled && it.adult == adult }
                    .sortedByDescending { it.name.lowercase() == PRIMARY_SOURCE_NAME }
            }
        }
    }

    /**
     * One-shot prefill for the route's optional `q` argument (the tracker-list import's
     * unmatched titles open search this way). Guarded so a recomposition/rotation doesn't
     * re-run the search or stomp a query the user has since edited.
     */
    fun prefillAndSearch(value: String) {
        if (prefilled || value.isBlank()) return
        prefilled = true
        _query.value = value
        search()
    }

    private var prefilled = false

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun search() {
        val trimmed = _query.value.trim()
        if (trimmed.isEmpty() || _isSearching.value) return
        viewModelScope.launch {
            _isSearching.value = true
            _error.value = null
            animeApi.search(trimmed, adult)
                .onSuccess { _results.value = groupSearchResults(it.results) }
                .onFailure { _error.value = it.message ?: "Search failed" }
            _isSearching.value = false
        }
    }
}
