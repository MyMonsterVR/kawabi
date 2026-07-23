package com.mymonstervr.kawabi.app.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.SourceApi
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.network.dto.MangaResponse
import com.mymonstervr.kawabi.data.network.dto.MangaSourceOptionDto
import com.mymonstervr.kawabi.data.track.TrackerManager
import com.mymonstervr.kawabi.data.track.dto.TrackSearchResult
import com.mymonstervr.kawabi.data.usecase.AddMangaToLibrary
import com.mymonstervr.kawabi.data.usecase.RefreshMangaChapters
import com.mymonstervr.kawabi.data.usecase.SyncClient
import com.mymonstervr.kawabi.data.usecase.TrackerSyncClient
import com.mymonstervr.kawabi.domain.model.Chapter
import com.mymonstervr.kawabi.domain.model.Track
import com.mymonstervr.kawabi.domain.repository.ChapterRepository
import com.mymonstervr.kawabi.domain.repository.MangaRepository
import com.mymonstervr.kawabi.domain.repository.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MangaDetailState {
    data object Loading : MangaDetailState
    data class Success(val manga: MangaResponse) : MangaDetailState
    data class Error(val message: String) : MangaDetailState
}

sealed interface SourcePickerState {
    data object Idle : SourcePickerState
    data object Loading : SourcePickerState
    data class Loaded(val options: List<MangaSourceOptionDto>, val selected: String?) : SourcePickerState
    data class Error(val message: String) : SourcePickerState
}

sealed interface TrackerSheetState {
    data object Hidden : TrackerSheetState
    data class Shown(val rows: List<TrackerLinkRow>) : TrackerSheetState
}

data class TrackerLinkRow(
    val trackerId: String,
    val trackerName: String,
    val linked: Track?,
    val searching: Boolean = false,
    val searchResults: List<TrackSearchResult>? = null,
    val searchError: String? = null,
)

private const val UNKNOWN_CHAPTER_NUMBER = -1.0

class MangaDetailViewModel(
    private val sourceApi: SourceApi,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val addMangaToLibrary: AddMangaToLibrary,
    private val refreshMangaChapters: RefreshMangaChapters,
    private val tokenStore: TokenStore,
    private val syncClient: SyncClient,
    private val trackerManager: TrackerManager,
    private val trackRepository: TrackRepository,
    private val trackerSyncClient: TrackerSyncClient,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = tokenStore.isLoggedIn

    private val _state = MutableStateFlow<MangaDetailState>(MangaDetailState.Loading)
    val state: StateFlow<MangaDetailState> = _state.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _sourcePicker = MutableStateFlow<SourcePickerState>(SourcePickerState.Idle)
    val sourcePicker: StateFlow<SourcePickerState> = _sourcePicker.asStateFlow()

    // Site key -> display name, e.g. "demonicscans.org" -> "Manga Demon". Backed by
    // GET /sources (the cheap toggle inventory, no live per-manga probing) so the pill
    // can show a friendly name for whichever source is auto-serving the manga right away,
    // instead of the raw domain until the user opens the (expensive, live-probing)
    // per-manga source picker.
    private val _siteNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val siteNames: StateFlow<Map<String, String>> = _siteNames.asStateFlow()

    // Last known title, kept even through a failed load -- the source picker's title
    // search (backend's resolveOnSite) needs a title to match against, and the error
    // screen has no MangaResponse to read one from. Without this, trying an alternate
    // source from the error screen searched with an empty title, weakening exactly the
    // recovery path a fully-broken default source needs most.
    private val _lastTitle = MutableStateFlow("")
    val lastTitle: StateFlow<String> = _lastTitle.asStateFlow()

    // Chapter url -> local Chapter, once the manga is in the library and its
    // chapters are synced. Reading requires a local chapter row (progress writes
    // need somewhere to land), so this is how chapter taps resolve a reader target,
    // and how read-state/continue are displayed.
    private val _localChaptersByUrl = MutableStateFlow<Map<String, Chapter>>(emptyMap())
    val localChaptersByUrl: StateFlow<Map<String, Chapter>> = _localChaptersByUrl.asStateFlow()

    private val _trackerSheet = MutableStateFlow<TrackerSheetState>(TrackerSheetState.Hidden)
    val trackerSheet: StateFlow<TrackerSheetState> = _trackerSheet.asStateFlow()

    // MangaUpdates alt-titles (backend's /alt-titles, kawabi-server's altitle.Client)
    // -- suggestion chips in the tracker search dialog, since a manga can be named
    // differently on MAL/Kitsu than on its source site.
    private val _altTitleSuggestions = MutableStateFlow<List<String>>(emptyList())
    val altTitleSuggestions: StateFlow<List<String>> = _altTitleSuggestions.asStateFlow()

    fun loadAltTitleSuggestions(title: String) {
        _altTitleSuggestions.value = emptyList()
        viewModelScope.launch {
            sourceApi.getAltTitles(title).onSuccess { _altTitleSuggestions.value = it }
        }
    }

    private var localMangaId: Long? = null
    private var loadedUrl: String? = null

    fun load(url: String) {
        if (loadedUrl == url && _state.value is MangaDetailState.Success) return
        loadedUrl = url
        viewModelScope.launch {
            _state.value = MangaDetailState.Loading
            sourceApi.getManga(url)
                .onSuccess { response ->
                    _state.value = MangaDetailState.Success(response)
                    if (response.title.isNotBlank()) _lastTitle.value = response.title
                    resolveLocalFavoriteState(url)
                }
                .onFailure { _state.value = MangaDetailState.Error(it.message ?: "Failed to load") }
        }
        if (tokenStore.isLoggedIn.value && _siteNames.value.isEmpty()) {
            viewModelScope.launch {
                sourceApi.getSources().onSuccess { response ->
                    _siteNames.value = response.sources.associate { it.key to it.name }
                }
            }
        }
    }

    fun refresh(url: String) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            sourceApi.getManga(url)
                .onSuccess { response -> _state.value = MangaDetailState.Success(response) }
            val mangaId = localMangaId
            if (mangaId != null) {
                mangaRepository.getById(mangaId)?.let { refreshMangaChapters.refresh(it) }
                refreshLocalChapters()
            }
            _isRefreshing.value = false
        }
    }

    fun toggleFavorite(url: String) {
        viewModelScope.launch {
            if (_isFavorite.value) {
                localMangaId?.let { mangaRepository.setFavorite(it, false) }
                _isFavorite.value = false
            } else {
                addMangaToLibrary.add(url).onSuccess { manga ->
                    localMangaId = manga.id
                    _isFavorite.value = true
                    refreshLocalChapters()
                }
            }
        }
    }

    fun setChapterRead(chapterId: Long, read: Boolean) {
        viewModelScope.launch {
            chapterRepository.setRead(chapterId, read)
            refreshLocalChapters()
            pushTrackers()
        }
    }

    /** Marks every chapter numbered before [target] as read -- not including it. */
    fun markPreviousAsRead(target: Chapter) {
        if (target.chapterNumber == UNKNOWN_CHAPTER_NUMBER) return
        viewModelScope.launch {
            _localChaptersByUrl.value.values
                .filter { it.chapterNumber != UNKNOWN_CHAPTER_NUMBER && it.chapterNumber < target.chapterNumber && !it.read }
                .forEach { chapterRepository.setRead(it.id, true) }
            refreshLocalChapters()
            pushTrackers()
        }
    }

    fun openTrackerSheet() {
        viewModelScope.launch { refreshTrackerSheet() }
    }

    fun closeTrackerSheet() {
        _trackerSheet.value = TrackerSheetState.Hidden
    }

    fun searchTracker(trackerId: String, query: String) {
        updateTrackerRow(trackerId) { it.copy(searching = true, searchError = null) }
        viewModelScope.launch {
            trackerSyncClient.search(trackerId, query)
                .onSuccess { results -> updateTrackerRow(trackerId) { it.copy(searching = false, searchResults = results) } }
                .onFailure { e -> updateTrackerRow(trackerId) { it.copy(searching = false, searchError = e.message ?: "Search failed") } }
        }
    }

    fun linkTracker(trackerId: String, result: TrackSearchResult) {
        val mangaId = localMangaId ?: return
        viewModelScope.launch {
            trackerSyncClient.link(mangaId, trackerId, result)
                .onSuccess {
                    refreshLocalChapters()
                    refreshTrackerSheet()
                }
                .onFailure { e -> updateTrackerRow(trackerId) { it.copy(searchError = e.message ?: "Link failed") } }
        }
    }

    fun unlinkTracker(trackerId: String) {
        val mangaId = localMangaId ?: return
        viewModelScope.launch {
            trackerSyncClient.unlink(mangaId, trackerId)
            refreshTrackerSheet()
        }
    }

    /** Manual chapter/status/score edit (mirrors the standard tracker edit dialog). */
    fun updateTrackDetails(track: Track, chaptersRead: Double, status: String, score: Double, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = trackerSyncClient.updateTrackDetails(track, chaptersRead, status, score)
            if (result.isSuccess) {
                refreshLocalChapters()
                refreshTrackerSheet()
            }
            onResult(result)
        }
    }

    private suspend fun refreshTrackerSheet() {
        _trackerSheet.value = TrackerSheetState.Shown(buildTrackerRows())
    }

    private suspend fun buildTrackerRows(): List<TrackerLinkRow> {
        val mangaId = localMangaId ?: return emptyList()
        val linkedByTracker = trackRepository.getForManga(mangaId).associateBy { it.trackerId }
        return trackerManager.loggedInTrackers().map { tracker ->
            TrackerLinkRow(trackerId = tracker.id, trackerName = tracker.name, linked = linkedByTracker[tracker.id])
        }
    }

    private fun updateTrackerRow(trackerId: String, transform: (TrackerLinkRow) -> TrackerLinkRow) {
        val current = _trackerSheet.value as? TrackerSheetState.Shown ?: return
        _trackerSheet.value = current.copy(rows = current.rows.map { if (it.trackerId == trackerId) transform(it) else it })
    }

    // Best-effort, fire-and-forget -- never blocks the UI, mirrors how syncClient.sync()
    // is already invoked elsewhere in this view model. Pull-based (monotonic-max
    // merge) -- only called on manga open, never after an explicit local read-state
    // edit (see pushTrackers).
    private fun syncTrackers() {
        val mangaId = localMangaId ?: return
        viewModelScope.launch {
            runCatching { trackerSyncClient.syncManga(mangaId) }
            refreshLocalChapters()
        }
    }

    // Explicit local read-state change (mark read/unread) always wins -- pushes the
    // current local count to every linked tracker exactly, in either direction.
    // Deliberately NOT syncTrackers()'s monotonic-max pull: that would silently
    // re-mark a chapter the user just marked unread back to read on the very next
    // sync (owner feedback -- unread should mean unread).
    private fun pushTrackers() {
        val mangaId = localMangaId ?: return
        viewModelScope.launch { runCatching { trackerSyncClient.pushLocalChaptersRead(mangaId) } }
    }

    fun loadSourceOptions(url: String, title: String) {
        viewModelScope.launch {
            _sourcePicker.value = SourcePickerState.Loading
            sourceApi.getMangaSources(url, title)
                .onSuccess { _sourcePicker.value = SourcePickerState.Loaded(it.sources, it.selected) }
                .onFailure { _sourcePicker.value = SourcePickerState.Error(it.message ?: "Failed to load sources") }
        }
    }

    // Empty siteKey clears the preference back to auto-pick.
    fun selectSource(url: String, siteKey: String, title: String) {
        viewModelScope.launch {
            sourceApi.setMangaSource(url, siteKey, title)
                .onSuccess {
                    loadSourceOptions(url, title)
                    sourceApi.getManga(url)
                        .onSuccess { response ->
                            _state.value = MangaDetailState.Success(response)
                            // The initial load() may have failed (e.g. a 502 from a down source)
                            // before localMangaId/isFavorite were ever resolved -- do it here too,
                            // not just once in load(), or a favorited manga looks unfavorited and
                            // its chapters look unread after recovering via a source switch.
                            if (localMangaId == null) resolveLocalFavoriteState(url)
                            // Switching site changes every chapter URL -- reconcile locally so the
                            // reader (which resolves chapters via the local DB, not this DTO) sees
                            // the new source's chapters instead of retrying the old, broken one.
                            val mangaId = localMangaId
                            if (mangaId != null) {
                                // The new site's chapter list is entirely different URLs, so
                                // SyncChaptersWithSource treats every old chapter row as "removed"
                                // and only carries read state over where chapter numbers happen
                                // to line up exactly (e.g. a source that bundles many chapters
                                // into one won't match at all) -- capture how far this manga was
                                // actually read *before* that diff runs, then re-apply it to
                                // whatever chapter numbers the new source has, so switching to a
                                // source with a different chapter count never looks like a read
                                // regression.
                                val chaptersReadBefore = chapterRepository.getForManga(mangaId)
                                    .filter { it.read }
                                    .maxOfOrNull { it.chapterNumber } ?: 0.0
                                mangaRepository.updateSiteKey(mangaId, response.served_from)
                                mangaRepository.getById(mangaId)?.let { refreshMangaChapters.refresh(it) }
                                if (chaptersReadBefore > 0) {
                                    chapterRepository.markReadUpToNumber(mangaId, chaptersReadBefore)
                                }
                                // chaptersReadBefore is only as good as the *current* source's
                                // chapter numbering -- if an earlier switch already undercounted it
                                // (e.g. bounced through a source with far fewer chapters), the local
                                // table alone can't recover the real total. The server's aggregate
                                // never regresses (monotonic-max, see kawabi-server's
                                // upsertOne), so pull the real count back down whenever logged in.
                                syncClient.sync()
                                refreshLocalChapters()
                            }
                        }
                        .onFailure {
                            // Don't leave the previous (possibly stale/wrong) source's details on
                            // screen with no indication the switch failed -- surface the error the
                            // same way an initial load failure does, source picker included so the
                            // user can immediately try yet another source.
                            _state.value = MangaDetailState.Error(it.message ?: "Failed to load")
                        }
                }
                .onFailure {
                    // PUT /manga/source itself can fail (e.g. 409 "not found on that source" when
                    // the title search comes up empty, 400 if the site got disabled meanwhile) --
                    // previously this branch didn't exist at all, so a rejected pick silently did
                    // nothing and the screen just kept showing whatever was on it before (a stale
                    // error from a different, unrelated source).
                    _state.value = MangaDetailState.Error(it.message ?: "Failed to switch source")
                }
        }
    }

    private suspend fun resolveLocalFavoriteState(url: String) {
        val existing = mangaRepository.getByUrl(url)
        localMangaId = existing?.id
        _isFavorite.value = existing?.favorite == true
        refreshLocalChapters()
        syncTrackers()
    }

    private suspend fun refreshLocalChapters() {
        val mangaId = localMangaId ?: return
        _localChaptersByUrl.value = chapterRepository.getForManga(mangaId).associateBy { it.url }
    }
}

/**
 * Where "Continue"/"Start reading" should resume: an in-progress chapter (partially
 * read, lowest-numbered if several) takes priority over just picking the next unread
 * chapter in order. Null if every numbered chapter is already read.
 */
fun resumeTarget(chapters: Collection<Chapter>): Chapter? {
    val numbered = chapters.filter { it.chapterNumber != UNKNOWN_CHAPTER_NUMBER }
    val inProgress = numbered.filter { !it.read && it.lastPageRead > 0 }.minByOrNull { it.chapterNumber }
    if (inProgress != null) return inProgress
    return numbered.filter { !it.read }.minByOrNull { it.chapterNumber }
}
