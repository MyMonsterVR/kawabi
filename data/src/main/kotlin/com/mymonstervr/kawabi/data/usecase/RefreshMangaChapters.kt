package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.SourceApi
import com.mymonstervr.kawabi.domain.interactor.SyncChaptersWithSource
import com.mymonstervr.kawabi.domain.model.Chapter
import com.mymonstervr.kawabi.domain.model.Manga
import com.mymonstervr.kawabi.domain.model.SourceChapter
import com.mymonstervr.kawabi.domain.repository.MangaRepository

/**
 * Fetches a manga's current chapter list from the backend and reconciles it
 * against local storage via [SyncChaptersWithSource]. `total_chapters` is
 * seeded here as a best-effort local count -- once the sync layer (plan
 * step 7) is built, `GET /entries` becomes the authoritative source for it.
 */
class RefreshMangaChapters(
    private val sourceApi: SourceApi,
    private val mangaRepository: MangaRepository,
    private val syncChaptersWithSource: SyncChaptersWithSource,
) {
    suspend fun refresh(manga: Manga): Result<List<Chapter>> {
        val response = sourceApi.getManga(manga.url).getOrElse { return Result.failure(it) }

        val sourceChapters = response.chapters.map { dto ->
            SourceChapter(
                url = dto.id,
                name = dto.title,
                chapterNumber = dto.number,
                dateUpload = dto.date_upload ?: 0L,
            )
        }

        return runCatching { syncChaptersWithSource.await(manga.id, sourceChapters) }
            .onSuccess { mangaRepository.setTotalChapters(manga.id, sourceChapters.size.toDouble()) }
    }
}
