package com.mymonstervr.kawabi.data.backup

import com.mymonstervr.kawabi.data.network.networkJson
import com.mymonstervr.kawabi.domain.model.Category
import com.mymonstervr.kawabi.domain.model.Chapter
import com.mymonstervr.kawabi.domain.model.Manga
import com.mymonstervr.kawabi.domain.model.Track
import com.mymonstervr.kawabi.domain.repository.CategoryRepository
import com.mymonstervr.kawabi.domain.repository.ChapterRepository
import com.mymonstervr.kawabi.domain.repository.MangaRepository
import com.mymonstervr.kawabi.domain.repository.TrackRepository
import kotlinx.serialization.encodeToString

data class ImportSummary(val mangaImported: Int, val chaptersImported: Int, val categoriesImported: Int)

/**
 * PLAN.md step 10: own JSON backup format, not a `.tachibk`-style binary. Exports the local
 * library (manga rows minus source-internal ids that don't transfer -- source/url is the
 * identity that survives a round-trip, not the local `_id`), categories + membership,
 * per-chapter read/bookmark/last_page_read state, notes, and per-manga tracker *links*
 * (remote id/title/chapter counts -- not account-level tracker auth, which never belongs
 * in a plaintext export file; re-linking on import just re-resolves against whichever
 * trackers the restoring device is already logged into).
 *
 * Import reconciles by `(source, url)`, same identity `MangaRepository.upsert` already
 * uses for the sync client and chapter-list diffing -- restoring onto a library that
 * already has some of these manga (e.g. re-installing, or merging two exports) updates
 * in place rather than duplicating.
 */
class BackupManager(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val categoryRepository: CategoryRepository,
    private val trackRepository: TrackRepository,
) {
    suspend fun export(): String {
        val categories = categoryRepository.getAll()
        val categoriesById = categories.associateBy { it.id }
        val favorites = mangaRepository.getFavorites()

        val backupManga = favorites.map { manga ->
            val chapters = chapterRepository.getForManga(manga.id)
            val categoryNames = categoryRepository.getCategoryIdsForManga(manga.id)
                .mapNotNull { categoriesById[it]?.name }
            val tracks = trackRepository.getForManga(manga.id)
            manga.toBackup(categoryNames, chapters, tracks)
        }

        val data = BackupData(
            exportedAt = System.currentTimeMillis(),
            categories = categories.filter { it.id != 0L }.map { BackupCategory(it.name, it.sort) },
            manga = backupManga,
        )
        return networkJson.encodeToString(data)
    }

    suspend fun import(json: String): Result<ImportSummary> = runCatching {
        val data = networkJson.decodeFromString(BackupData.serializer(), json)

        val existingCategories = categoryRepository.getAll().associateBy { it.name }
        val categoryIdByName = existingCategories.toMutableMap()
        var categoriesImported = 0
        for (category in data.categories) {
            if (!categoryIdByName.containsKey(category.name)) {
                val id = categoryRepository.create(category.name, category.sort)
                categoryIdByName[category.name] = Category(id, category.name, category.sort, 0)
                categoriesImported++
            }
        }

        var chaptersImported = 0
        for (backupManga in data.manga) {
            val mangaId = mangaRepository.upsert(backupManga.toDomain())
            mangaRepository.setFavorite(mangaId, true)

            val categoryIds = backupManga.categoryNames.mapNotNull { categoryIdByName[it]?.id }
            if (categoryIds.isNotEmpty()) categoryRepository.setCategoriesForManga(mangaId, categoryIds)

            for (backupChapter in backupManga.chapters) {
                val existing = chapterRepository.getByMangaAndUrl(mangaId, backupChapter.url)
                val chapterId = chapterRepository.upsert(backupChapter.toDomain(mangaId))
                chapterRepository.setProgress(chapterId, backupChapter.read, backupChapter.lastPageRead)
                if (existing == null) chaptersImported++
            }

            for (backupTrack in backupManga.tracks) {
                trackRepository.link(backupTrack.toDomain(mangaId))
            }
        }

        ImportSummary(
            mangaImported = data.manga.size,
            chaptersImported = chaptersImported,
            categoriesImported = categoriesImported,
        )
    }
}

private fun Manga.toBackup(categoryNames: List<String>, chapters: List<Chapter>, tracks: List<Track>) = BackupManga(
    source = source,
    siteKey = siteKey,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genres = genres,
    status = status,
    thumbnailUrl = thumbnailUrl,
    viewer = viewer,
    notes = notes,
    categoryNames = categoryNames,
    chapters = chapters.map {
        BackupChapter(
            url = it.url,
            name = it.name,
            scanlator = it.scanlator,
            read = it.read,
            bookmark = it.bookmark,
            lastPageRead = it.lastPageRead,
            chapterNumber = it.chapterNumber,
            sourceOrder = it.sourceOrder,
            dateUpload = it.dateUpload,
        )
    },
    tracks = tracks.map {
        BackupTrack(
            trackerId = it.trackerId,
            remoteId = it.remoteId,
            libraryId = it.libraryId,
            title = it.title,
            trackingUrl = it.trackingUrl,
            totalChapters = it.totalChapters,
            lastChapterRead = it.lastChapterRead,
            score = it.score,
            status = it.status,
        )
    },
)

private fun BackupManga.toDomain() = Manga(
    id = 0,
    source = source,
    siteKey = siteKey,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genres = genres,
    status = status,
    thumbnailUrl = thumbnailUrl,
    favorite = true,
    lastUpdate = null,
    nextUpdate = null,
    initialized = true,
    chapterFlags = 0,
    viewer = viewer,
    dateAdded = System.currentTimeMillis(),
    calculateInterval = 0,
    lastModifiedAt = 0,
    version = 0,
    isSyncing = false,
    totalChapters = chapters.size.toDouble(),
    notes = notes,
    lastReadAt = 0,
)

private fun BackupChapter.toDomain(mangaId: Long) = Chapter(
    id = 0,
    mangaId = mangaId,
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead,
    chapterNumber = chapterNumber,
    sourceOrder = sourceOrder,
    dateUpload = dateUpload,
    dateFetch = 0,
    lastModifiedAt = 0,
    version = 0,
    isSyncing = false,
)

private fun BackupTrack.toDomain(mangaId: Long) = Track(
    id = 0,
    mangaId = mangaId,
    trackerId = trackerId,
    remoteId = remoteId,
    libraryId = libraryId,
    title = title,
    trackingUrl = trackingUrl,
    totalChapters = totalChapters,
    lastChapterRead = lastChapterRead,
    score = score,
    status = status,
)
