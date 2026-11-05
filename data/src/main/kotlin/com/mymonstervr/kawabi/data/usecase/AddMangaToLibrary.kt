package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.SourceApi
import com.mymonstervr.kawabi.data.network.toDomain
import com.mymonstervr.kawabi.domain.model.Manga
import com.mymonstervr.kawabi.domain.repository.MangaRepository

class AddMangaToLibrary(
    private val sourceApi: SourceApi,
    private val mangaRepository: MangaRepository,
    private val refreshMangaChapters: RefreshMangaChapters,
    private val adultSources: AdultSources,
) {
    suspend fun add(url: String, adult: Boolean = false): Result<Manga> {
        val response = sourceApi.getManga(url).getOrElse { return Result.failure(it) }
        val isAdult = adult || adultSources.isAdultManga(response.served_from, response.source)
        val manga = response.toDomain().copy(adult = isAdult)
        val id = mangaRepository.upsert(manga)
        mangaRepository.setFavorite(id, true)
        if (isAdult) mangaRepository.setAdult(id, true)
        val stored = manga.copy(id = id)
        refreshMangaChapters.refresh(stored)
        return Result.success(stored)
    }
}
