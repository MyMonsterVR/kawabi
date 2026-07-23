package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.SourceApi
import com.mymonstervr.kawabi.data.network.toDomain
import com.mymonstervr.kawabi.domain.model.Manga
import com.mymonstervr.kawabi.domain.repository.MangaRepository

class AddMangaToLibrary(
    private val sourceApi: SourceApi,
    private val mangaRepository: MangaRepository,
    private val refreshMangaChapters: RefreshMangaChapters,
) {
    suspend fun add(url: String): Result<Manga> {
        val response = sourceApi.getManga(url).getOrElse { return Result.failure(it) }
        val manga = response.toDomain()
        val id = mangaRepository.upsert(manga)
        mangaRepository.setFavorite(id, true)
        val stored = manga.copy(id = id)
        refreshMangaChapters.refresh(stored)
        return Result.success(stored)
    }
}
