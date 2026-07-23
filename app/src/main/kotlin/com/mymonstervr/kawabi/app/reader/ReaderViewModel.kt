package com.mymonstervr.kawabi.app.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.SourceApi
import com.mymonstervr.kawabi.data.network.dto.PageDto
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.ReadingDirection
import com.mymonstervr.kawabi.domain.model.Chapter
import com.mymonstervr.kawabi.domain.repository.ChapterRepository
import com.mymonstervr.kawabi.domain.repository.MangaRepository
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
}

private const val UNKNOWN_CHAPTER_NUMBER = -1.0

class ReaderViewModel(
    private val sourceApi: SourceApi,
    private val chapterRepository: ChapterRepository,
    private val mangaRepository: MangaRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _isLoadingNext = MutableStateFlow(false)
    val isLoadingNext: StateFlow<Boolean> = _isLoadingNext.asStateFlow()

    val readingDirection: StateFlow<ReadingDirection> = preferences.readingDirection
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadingDirection.VERTICAL)

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

    // Tracks the latest position so onCleared() can flush it synchronously. A write
    // launched on viewModelScope can't be trusted to land here: leaving the reader pops
    // the nav back-stack entry, which clears viewModelScope, and any write not yet
    // launched (or launched but not yet resumed) against an already-cancelled scope
    // silently no-ops rather than throwing -- the loss is invisible. onCleared() is the
    // one place guaranteed to run exactly once as this ViewModel actually goes away, so
    // the final write happens there instead, blocking briefly since viewModelScope is no
    // longer usable by that point.
    private var lastKnownProgress: Triple<Long, Int, Int>? = null

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
            siblingChapters = chapterRepository.getForManga(chapter.mangaId)
                .filter { it.chapterNumber != UNKNOWN_CHAPTER_NUMBER }
                .sortedBy { it.chapterNumber }

            val myIndex = siblingChapters.indexOfFirst { it.id == chapterId }
            val prevChapterId = siblingChapters.getOrNull(myIndex - 1)?.id
            val nextChapterId = siblingChapters.getOrNull(myIndex + 1)?.id

            sourceApi.getPages(manga.source, chapter.url)
                .onSuccess { pages ->
                    val start = if (pages.isEmpty()) 0 else chapter.lastPageRead.coerceIn(0, pages.size - 1)
                    val section = ChapterSection(chapter.id, chapterLabel(chapter), pages)
                    _state.value = ReaderState.Success(
                        sections = listOf(section),
                        startPage = start,
                        prevChapterId = prevChapterId,
                        nextChapterId = nextChapterId,
                        hasMoreToAppend = nextChapterId != null,
                    )
                }
                .onFailure { _state.value = ReaderState.Error(it.message ?: "Failed to load pages") }
        }
    }

    /**
     * Appends the next chapter's pages onto the end of the current section list --
     * true continuous scrolling (matches kawabi-web), not a screen navigation.
     */
    fun loadNextSection() {
        val current = _state.value as? ReaderState.Success ?: return
        if (_isLoadingNext.value || !current.hasMoreToAppend) return
        val lastSection = current.sections.last()
        val lastSiblingIndex = siblingChapters.indexOfFirst { it.id == lastSection.chapterId }
        val next = siblingChapters.getOrNull(lastSiblingIndex + 1)
        if (next == null) {
            _state.value = current.copy(hasMoreToAppend = false)
            return
        }
        viewModelScope.launch {
            _isLoadingNext.value = true
            sourceApi.getPages(mangaSource, next.url)
                .onSuccess { pages ->
                    val nextSiblingIndex = siblingChapters.indexOfFirst { it.id == next.id }
                    val hasMore = siblingChapters.getOrNull(nextSiblingIndex + 1) != null
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
    // last-page/debounce conditions ever fire.
    fun trackPosition(chapterId: Long, index: Int, totalPages: Int) {
        if (totalPages == 0) return
        lastKnownProgress = Triple(chapterId, index, totalPages)
    }

    fun onPageChanged(chapterId: Long, index: Int, totalPages: Int) {
        if (totalPages == 0) return
        lastKnownProgress = Triple(chapterId, index, totalPages)
        viewModelScope.launch {
            val isLastPage = index == totalPages - 1
            chapterRepository.setProgress(chapterId, read = isLastPage && markReadOnScroll, lastPageRead = index)
            mangaRepository.touchLastRead(mangaId, System.currentTimeMillis())
        }
    }

    fun markChapterFinished(chapterId: Long, totalPages: Int) {
        if (totalPages == 0) return
        lastKnownProgress = Triple(chapterId, totalPages - 1, totalPages)
        viewModelScope.launch {
            chapterRepository.setProgress(chapterId, read = true, lastPageRead = totalPages - 1)
            mangaRepository.touchLastRead(mangaId, System.currentTimeMillis())
        }
    }

    override fun onCleared() {
        super.onCleared()
        val (chapterId, index, totalPages) = lastKnownProgress ?: return
        val isLastPage = index == totalPages - 1
        runBlocking {
            chapterRepository.setProgress(chapterId, read = isLastPage && markReadOnScroll, lastPageRead = index)
            mangaRepository.touchLastRead(mangaId, System.currentTimeMillis())
        }
    }

    private fun chapterLabel(chapter: Chapter): String =
        chapter.name.ifBlank { "Chapter ${formatChapterNumber(chapter.chapterNumber)}" }
}

private fun formatChapterNumber(number: Double): String =
    if (number == number.toLong().toDouble()) number.toLong().toString() else number.toString()
