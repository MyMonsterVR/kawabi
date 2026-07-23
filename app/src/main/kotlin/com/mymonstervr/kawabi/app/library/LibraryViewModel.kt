package com.mymonstervr.kawabi.app.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.LibraryCardSize
import com.mymonstervr.kawabi.data.usecase.RefreshMangaChapters
import com.mymonstervr.kawabi.domain.model.MangaWithUnreadCount
import com.mymonstervr.kawabi.domain.repository.MangaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val REFRESH_CONCURRENCY = 6

class LibraryViewModel(
    mangaRepository: MangaRepository,
    private val refreshMangaChapters: RefreshMangaChapters,
    preferences: AppPreferences,
) : ViewModel() {

    val favorites: StateFlow<List<MangaWithUnreadCount>> = mangaRepository.observeFavoritesWithUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cardSize: StateFlow<LibraryCardSize> = preferences.libraryCardSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryCardSize.MEDIUM)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // One manga at a time was making pull-to-refresh feel stuck once the library grew
    // past a couple dozen entries -- each is a real network round-trip (some through
    // Suwayomi/WARP-routed sources), so 28 manga sequentially could take the better part
    // of a minute. Bounded concurrency keeps it responsive without hammering the backend
    // unboundedly (same "bounded concurrency" principle PLAN.md's step 9 background job
    // will need too).
    //
    // A large library still can't beat the backend's sustained rate limit (~30/min,
    // internal/middleware/ratelimit.go) no matter how much client concurrency there is --
    // once the burst is spent, requests trickle in at 1/2s regardless. So order matters:
    // caught-up manga (unreadCount == 0) go first, since a refresh is the only way to
    // discover a brand new chapter on those -- manga already sitting on unread chapters
    // don't gain anything urgent from a faster refresh, we already know there's something
    // to read. Within each tier, most-recently-read first (favors what's actively being
    // followed over dormant entries, which can wait out the rate limit).
    fun refreshAll() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val ordered = favorites.value.sortedWith(
                    compareBy<MangaWithUnreadCount> { it.unreadCount > 0 }
                        .thenByDescending { it.manga.lastReadAt },
                )
                val semaphore = Semaphore(REFRESH_CONCURRENCY)
                ordered.map { entry ->
                    async { semaphore.withPermit { refreshMangaChapters.refresh(entry.manga) } }
                }.awaitAll()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
