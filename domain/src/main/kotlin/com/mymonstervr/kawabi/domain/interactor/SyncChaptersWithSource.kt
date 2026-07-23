package com.mymonstervr.kawabi.domain.interactor

import com.mymonstervr.kawabi.domain.model.Chapter
import com.mymonstervr.kawabi.domain.model.SourceChapter
import com.mymonstervr.kawabi.domain.repository.ChapterRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private const val UNKNOWN_CHAPTER_NUMBER = -1.0

/**
 * Diffs a manga's locally stored chapters against a fresh listing from a source.
 * Trimmed for v1: no download-folder renaming, no scanlator-exclusion filtering (the
 * backend doesn't send per-chapter scanlator yet), no fetch-interval bookkeeping (step 9).
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
                current.sourceOrder != index
            if (changed) current to (sourceChapter to index) else null
        }

        if (newEntries.isEmpty() && removedChapters.isEmpty() && updatedEntries.isEmpty()) {
            return emptyList()
        }

        // Re-upload detection: a "new" chapter whose number matches a chapter that just
        // got removed in this same sync inherits its read state instead of appearing as new.
        val carryoverByNumber = removedChapters
            .filter { it.chapterNumber != UNKNOWN_CHAPTER_NUMBER }
            .associateBy { it.chapterNumber }

        val now = System.currentTimeMillis()
        var fetchOffset = newEntries.size
        val newlyAddedChapters = mutableListOf<Chapter>()

        for ((url, entry) in newEntries) {
            val (sourceChapter, index) = entry
            val carryover = carryoverByNumber[sourceChapter.chapterNumber]

            val chapter = Chapter(
                id = 0,
                mangaId = mangaId,
                url = url,
                name = sourceChapter.name,
                scanlator = null,
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
            val id = chapterRepository.insert(chapter)
            if (carryover == null) newlyAddedChapters += chapter.copy(id = id)
        }

        for ((current, entry) in updatedEntries) {
            val (sourceChapter, index) = entry
            chapterRepository.updateDetails(
                id = current.id,
                name = sourceChapter.name,
                scanlator = current.scanlator,
                chapterNumber = sourceChapter.chapterNumber,
                sourceOrder = index,
                dateUpload = sourceChapter.dateUpload,
            )
        }

        if (removedChapters.isNotEmpty()) {
            chapterRepository.deleteByIds(removedChapters.map { it.id })
        }

        return newlyAddedChapters
    }

    private companion object {
        val mangaLocks = ConcurrentHashMap<Long, Mutex>()
    }
}
