package com.mymonstervr.kawabi.app.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.SourceApi
import com.mymonstervr.kawabi.data.network.dto.PageDto
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.MARK_READ_THRESHOLD_DEFAULT
import com.mymonstervr.kawabi.data.settings.PageFitMode
import com.mymonstervr.kawabi.data.settings.ReadingDirection
import com.mymonstervr.kawabi.domain.model.Chapter
import com.mymonstervr.kawabi.domain.model.normalizedScanlator
import com.mymonstervr.kawabi.domain.model.versionBadgeLabel
import com.mymonstervr.kawabi.domain.repository.ChapterRepository
import com.mymonstervr.kawabi.domain.repository.MangaRepository
import com.mymonstervr.kawabi.data.usecase.SyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class ChapterSection(
    val chapterId: Long,
    val chapterLabel: String,
    val pages: List<PageDto>,
)

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Success(
        val sections: List<ChapterSection>,
        val startPage: Int,
        // Relative to the *first* loaded chapter -- paged mode stays single-chapter
        // (matches kawabi-web's paged reader) and uses these for its '‹ ›' buttons.
        // Vertical mode ignores them and auto-appends via loadNextSection() instead.
        val prevChapterId: Long?,
        val nextChapterId: Long?,
        val hasMoreToAppend: Boolean,
    ) : ReaderState
    data class Error(val message: String) : ReaderState
    // Chapter failed to load (or came back with no pages) and a same-numbered other
    // version exists -- offer it instead of leaving the user stuck on a bare error.
    data class ErrorWithAlternate(val message: String, val alternateChapterId: Long, val alternateLabel: String) : ReaderState
}

private const val UNKNOWN_CHAPTER_NUMBER = -1.0

class ReaderViewModel(
    private val sourceApi: SourceApi,
    private val chapterRepository: ChapterRepository,
    private val mangaRepository: MangaRepository,
    private val preferences: AppPreferences,
    private val syncClient: SyncClient,
) : ViewModel() {

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _isLoadingNext = MutableStateFlow(false)
    val isLoadingNext: StateFlow<Boolean> = _isLoadingNext.asStateFlow()

    // Resolved once load() completes -- the global default folded together with this
    // manga's per-series override, if any. ReaderScreen only ever reads this once the
    // reader state is already Success (load() has finished), so there's no risk of
    // seeding the in-reader mode from a stale/unresolved value the way a synchronous
    // `.value` read at first composition would have needed to guard against.
    private val _effectiveReadingDirection = MutableStateFlow(ReadingDirection.VERTICAL)
    val effectiveReadingDirection: StateFlow<ReadingDirection> = _effectiveReadingDirection.asStateFlow()

    // Unlike reading direction (a full layout flip, deliberately not applied mid-read),
    // changing fit mode or the mark-read threshold mid-session only changes how the SAME
    // pages render/how soon a page counts as read -- not disruptive enough to warrant a
    // snapshot-at-open, so these stay reactive.
    val pageFitMode: StateFlow<PageFitMode> = preferences.pageFitMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, PageFitMode.FIT_WIDTH)

    val markReadThreshold: StateFlow<Int> = preferences.markReadThreshold
        .stateIn(viewModelScope, SharingStarted.Eagerly, MARK_READ_THRESHOLD_DEFAULT)

    val keepScreenAwake: StateFlow<Boolean> = preferences.keepScreenAwake
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Snapshot read once per chapter load, not observed reactively -- changing the
    // setting mid-read shouldn't retroactively change what "reaching the last page"
    // does for a chapter already in progress.
    private var markReadOnScroll: Boolean = true

    private var mangaSource: String = ""
    private var mangaId: Long = 0

    // Ordered by chapter number so both paged '‹ ›' and vertical auto-continue can find
    // "the next one" -- chapter_number == -1 (unknown) is excluded since there's no
    // meaningful ordering for those.
    private var siblingChapters: List<Chapter> = emptyList()
    private var loadedFor: Long? = null

    // This manga's per-manga preferred scanlator (null = no preference / show both),
    // snapshotted at load time like markReadOnScroll.
    private var preferredScanlator: String? = null

    // Set only by swapToAlternate() -- the chapter the user is reading because its
    // preferred-version twin failed to load, not because they changed their preference.
    // Transient and session-only: never written to the stored preference. nextOf/prevOf
    // check this so the chapter AFTER the swapped one returns to the real preference
    // instead of following the fallback's scanlator, which would otherwise make the
    // fallback sticky -- the mirror image of the bug this whole feature fixes.
    private var sessionOverrideChapterId: Long? = null

    /**
     * The chapter to advance to from [chapter]: the next distinct chapter NUMBER, then
     * among that number's versions (if the source has more than one) the one matching,
     * in order, the scanlator actually being read now, the manga's stored preference, or
     * failing both a deterministic tiebreak. Keeps duplicate-numbered manga (e.g.
     * MangaFire's official/unofficial pairs) advancing chapter-to-chapter instead of
     * bouncing between one number's two versions.
     */
    private fun nextOf(chapter: Chapter): Chapter? = adjacentOf(chapter, forward = true)

    private fun prevOf(chapter: Chapter): Chapter? = adjacentOf(chapter, forward = false)

    private fun adjacentOf(chapter: Chapter, forward: Boolean): Chapter? {
        val candidates = if (forward) {
            siblingChapters.filter { it.chapterNumber > chapter.chapterNumber }
        } else {
            siblingChapters.filter { it.chapterNumber < chapter.chapterNumber }
        }
        if (candidates.isEmpty()) return null
        val targetNumber = if (forward) candidates.minOf { it.chapterNumber } else candidates.maxOf { it.chapterNumber }
        val atNumber = candidates.filter { it.chapterNumber == targetNumber }
        if (atNumber.size == 1) return atNumber.single()

        val referenceScanlator = if (chapter.id == sessionOverrideChapterId) {
            preferredScanlator.normalizedScanlator()
        } else {
            chapter.scanlator.normalizedScanlator()
        }
        return atNumber.firstOrNull { it.scanlator.normalizedScanlator() == referenceScanlator }
            ?: atNumber.firstOrNull { it.scanlator.normalizedScanlator() == preferredScanlator.normalizedScanlator() }
            ?: atNumber.minByOrNull { it.sourceOrder }
    }

    // Tracks the latest position so onCleared() can flush it synchronously. A write
    // launched on viewModelScope can't be trusted to land here: leaving the reader pops
    // the nav back-stack entry, which clears viewModelScope, and any write not yet
    // launched (or launched but not yet resumed) against an already-cancelled scope
    // silently no-ops rather than throwing -- the loss is invisible. onCleared() is the
    // one place guaranteed to run exactly once as this ViewModel actually goes away, so
    // the final write happens there instead, blocking briefly since viewModelScope is no
    // longer usable by that point.
    //
    // reachedEnd is computed by the caller (ReaderScreen), not derived here from
    // index/totalPages alone -- a bare `index == totalPages - 1` is true the instant a
    // single-page chapter opens, or the instant a tall webtoon strip's TOP edge scrolls
    // into view, both of which used to mark the chapter read before it was actually
    // finished. The caller decides what "reached the end" really means for its reading
    // mode (bottom-edge visibility in vertical mode, arrival-not-start in paged mode).
    private data class ReaderProgress(val chapterId: Long, val index: Int, val totalPages: Int, val reachedEnd: Boolean)
    private var lastKnownProgress: ReaderProgress? = null

    fun load(chapterId: Long) {
        if (loadedFor == chapterId) return
        loadedFor = chapterId
        viewModelScope.launch {
            _state.value = ReaderState.Loading
            val chapter = chapterRepository.getById(chapterId)
            if (chapter == null) {
                _state.value = ReaderState.Error("Chapter not found")
                return@launch
            }
            val manga = mangaRepository.getById(chapter.mangaId)
            if (manga == null) {
                _state.value = ReaderState.Error("Manga not found")
                return@launch
            }
            mangaSource = manga.source
            mangaId = manga.id
            markReadOnScroll = preferences.markReadOnScroll.first()
            preferredScanlator = preferences.preferredScanlator(manga.url).first()
            _effectiveReadingDirection.value = preferences.readingDirectionOverride(manga.url).first()
                ?: preferences.readingDirection.first()
            siblingChapters = chapterRepository.getForManga(chapter.mangaId)
                .filter { it.chapterNumber != UNKNOWN_CHAPTER_NUMBER }
                .sortedBy { it.chapterNumber }

            val prevChapterId = prevOf(chapter)?.id
            val nextChapterId = nextOf(chapter)?.id

            sourceApi.getPages(manga.source, chapter.url)
                .onSuccess { pages ->
                    if (pages.isEmpty()) {
                        onChapterUnavailable(chapter, "No pages found for this chapter")
                        return@onSuccess
                    }
                    val start = chapter.lastPageRead.coerceIn(0, pages.size - 1)
                    val section = ChapterSection(chapter.id, chapterLabel(chapter), pages)
                    _state.value = ReaderState.Success(
                        sections = listOf(section),
                        startPage = start,
                        prevChapterId = prevChapterId,
                        nextChapterId = nextChapterId,
                        hasMoreToAppend = nextChapterId != null,
                    )
                }
                .onFailure { onChapterUnavailable(chapter, it.message ?: "Failed to load pages") }
        }
    }

    // Called when a chapter's pages fail to load or come back empty. If another version
    // of the same chapter number exists (the MangaFire-style official/unofficial case),
    // offer it as a one-tap alternative instead of leaving the user stuck.
    private fun onChapterUnavailable(chapter: Chapter, message: String) {
        val alternate = siblingChapters.firstOrNull {
            it.chapterNumber == chapter.chapterNumber && it.id != chapter.id
        }
        _state.value = if (alternate != null) {
            ReaderState.ErrorWithAlternate(message, alternate.id, alternate.versionBadgeLabel())
        } else {
            ReaderState.Error(message)
        }
    }

    // Fallback swap from ErrorWithAlternate -- transient, per-chapter, never persisted
    // as the manga's preference (see sessionOverrideChapterId's doc above).
    fun swapToAlternate(chapterId: Long) {
        sessionOverrideChapterId = chapterId
        loadedFor = null
        load(chapterId)
    }

    /**
     * Appends the next chapter's pages onto the end of the current section list --
     * true continuous scrolling (matches kawabi-web), not a screen navigation.
     */
    fun loadNextSection() {
        val current = _state.value as? ReaderState.Success ?: return
        if (_isLoadingNext.value || !current.hasMoreToAppend) return
        val lastSection = current.sections.last()
        val lastSiblingChapter = siblingChapters.firstOrNull { it.id == lastSection.chapterId } ?: return
        val next = nextOf(lastSiblingChapter)
        if (next == null) {
            _state.value = current.copy(hasMoreToAppend = false)
            return
        }
        viewModelScope.launch {
            _isLoadingNext.value = true
            sourceApi.getPages(mangaSource, next.url)
                .onSuccess { pages ->
                    val hasMore = nextOf(next) != null
                    val newSection = ChapterSection(next.id, chapterLabel(next), pages)
                    val stillCurrent = _state.value as? ReaderState.Success
                    if (stillCurrent != null) {
                        _state.value = stillCurrent.copy(
                            sections = stillCurrent.sections + newSection,
                            hasMoreToAppend = hasMore,
                        )
                    }
                }
                // Leave hasMoreToAppend as-is on failure (rate limit, source hiccup) so
                // scrolling back down to the edge retries rather than looking finished.
                .onFailure { }
            _isLoadingNext.value = false
        }
    }

    // In-memory only, called on every scroll tick so onCleared() always has the true
    // latest position even if the user leaves mid-scroll, before onPageChanged's own
    // debounce ever fires.
    fun trackPosition(chapterId: Long, index: Int, totalPages: Int, reachedEnd: Boolean) {
        if (totalPages == 0) return
        lastKnownProgress = ReaderProgress(chapterId, index, totalPages, reachedEnd)
    }

    fun onPageChanged(chapterId: Long, index: Int, totalPages: Int, reachedEnd: Boolean) {
        if (totalPages == 0) return
        lastKnownProgress = ReaderProgress(chapterId, index, totalPages, reachedEnd)
        val justMarkedRead = reachedEnd && markReadOnScroll
        viewModelScope.launch {
            chapterRepository.setProgress(chapterId, read = justMarkedRead, lastPageRead = index)
            mangaRepository.touchLastRead(mangaId, System.currentTimeMillis())
            // A long-lived reading session (tablet processes especially can stay alive for
            // days without a cold start) would otherwise only ever push progress at
            // KawabiApplication's startup sync -- meaning chapters read mid-session never
            // reach the backend until the process happens to restart. Push right when a
            // chapter is actually completed instead of waiting on that. Not on every
            // scroll tick (only justMarkedRead, debounced upstream to ~1/400ms already) --
            // a full push+pull on every tick would hammer the backend's rate limiter.
            if (justMarkedRead) syncClient.sync()
        }
    }

    override fun onCleared() {
        super.onCleared()
        val progress = lastKnownProgress ?: return
        runBlocking {
            chapterRepository.setProgress(progress.chapterId, read = progress.reachedEnd && markReadOnScroll, lastPageRead = progress.index)
            mangaRepository.touchLastRead(mangaId, System.currentTimeMillis())
        }
    }

    private fun chapterLabel(chapter: Chapter): String =
        chapter.name.ifBlank { "Chapter ${formatChapterNumber(chapter.chapterNumber)}" }
}

private fun formatChapterNumber(number: Double): String =
    if (number == number.toLong().toDouble()) number.toLong().toString() else number.toString()
