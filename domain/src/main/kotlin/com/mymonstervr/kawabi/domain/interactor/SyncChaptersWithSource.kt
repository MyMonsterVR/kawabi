package com.mymonstervr.kawabi.domain.interactor

import com.mymonstervr.kawabi.domain.model.Chapter
import com.mymonstervr.kawabi.domain.model.ChapterUpdate
import com.mymonstervr.kawabi.domain.model.SourceChapter
import com.mymonstervr.kawabi.domain.repository.ChapterRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private const val UNKNOWN_CHAPTER_NUMBER = -1.0

/**
 * Diffs a manga's locally stored chapters against a fresh listing from a source.
 * Trimmed for v1: no download-folder renaming, no fetch-interval bookkeeping (step 9).
 *
 * Companion-level lock map (not instance-level) because this interactor is
 * constructed fresh per call site -- a shared map is the only thing that actually
 * serializes two concurrent syncs for the same manga.
 */
class SyncChaptersWithSource(
    private val chapterRepository: ChapterRepository,
) {
    suspend fun await(mangaId: Long, sourceChapters: List<SourceChapter>): List<Chapter> {
        val mutex = mangaLocks.getOrPut(mangaId) { Mutex() }
        return mutex.withLock { awaitLocked(mangaId, sourceChapters) }
    }

    private suspend fun awaitLocked(mangaId: Long, sourceChapters: List<SourceChapter>): List<Chapter> {
        if (sourceChapters.isEmpty()) throw NoChaptersException()

        val fresh = sourceChapters.distinctBy { it.url }
            .mapIndexed { index, chapter -> chapter.url to (chapter to index) }
            .toMap()

        val existing = chapterRepository.getForManga(mangaId)
        val existingByUrl = existing.associateBy { it.url }

        val newEntries = fresh.filterKeys { it !in existingByUrl }
        val removedChapters = existing.filter { it.url !in fresh }
        val matchedUrls = fresh.keys intersect existingByUrl.keys

        val updatedEntries = matchedUrls.mapNotNull { url ->
            val (sourceChapter, index) = fresh.getValue(url)
            val current = existingByUrl.getValue(url)
            val changed = current.name != sourceChapter.name ||
                current.chapterNumber != sourceChapter.chapterNumber ||
                current.dateUpload != sourceChapter.dateUpload ||
                current.sourceOrder != index ||
                current.scanlator != sourceChapter.scanlator
            if (changed) current to (sourceChapter to index) else null
        }

        if (newEntries.isEmpty() && removedChapters.isEmpty() && updatedEntries.isEmpty()) {
            return emptyList()
        }

        // Re-upload detection: a "new" chapter whose (number, scanlator) matches a chapter
        // that just got removed in this same sync inherits its read state instead of
        // appearing as new. Keyed on the pair, not just number -- MangaFire-style manga
        // carry two versions per number (official/unofficial), and number-only keying
        // would silently hand one twin's read state to the other.
        val carryoverByNumber = removedChapters
            .filter { it.chapterNumber != UNKNOWN_CHAPTER_NUMBER }
            .associateBy { it.chapterNumber to it.scanlator }

        val now = System.currentTimeMillis()
        var fetchOffset = newEntries.size
        val toInsert = mutableListOf<Chapter>()
        val isGenuinelyNew = mutableListOf<Boolean>()

        for ((url, entry) in newEntries) {
            val (sourceChapter, index) = entry
            val carryover = carryoverByNumber[sourceChapter.chapterNumber to sourceChapter.scanlator]

            toInsert += Chapter(
                id = 0,
                mangaId = mangaId,
                url = url,
                name = sourceChapter.name,
                scanlator = sourceChapter.scanlator,
                read = carryover?.read ?: false,
                bookmark = carryover?.bookmark ?: false,
                lastPageRead = carryover?.lastPageRead ?: 0,
                chapterNumber = sourceChapter.chapterNumber,
                sourceOrder = index,
                dateUpload = sourceChapter.dateUpload,
                dateFetch = carryover?.dateFetch ?: (now + fetchOffset--),
                lastModifiedAt = 0,
                version = 0,
                isSyncing = false,
            )
            isGenuinelyNew += (carryover == null)
        }

        val toUpdate = updatedEntries.map { (current, entry) ->
            val (sourceChapter, index) = entry
            ChapterUpdate(
                id = current.id,
                name = sourceChapter.name,
                scanlator = sourceChapter.scanlator,
                chapterNumber = sourceChapter.chapterNumber,
                sourceOrder = index,
                dateUpload = sourceChapter.dateUpload,
            )
        }

        // All inserts/updates/deletes for this manga go through one DB transaction instead
        // of a separate one per row -- see ChapterRepository.applySync's doc comment.
        val insertedIds = chapterRepository.applySync(
            inserts = toInsert,
            updates = toUpdate,
            deleteIds = removedChapters.map { it.id },
        )

        val newlyAddedChapters = mutableListOf<Chapter>()
        for (i in toInsert.indices) {
            if (isGenuinelyNew[i]) newlyAddedChapters += toInsert[i].copy(id = insertedIds[i])
        }
        return newlyAddedChapters
    }

    private companion object {
        val mangaLocks = ConcurrentHashMap<Long, Mutex>()
    }
}
