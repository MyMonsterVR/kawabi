package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.SourceApi
import com.mymonstervr.kawabi.domain.model.Chapter
import com.mymonstervr.kawabi.domain.model.Manga
import java.io.IOException

// Matches the backend's maxBatchURLs cap (mihon-sync-server's internal/handler/manga.go) --
// a library bigger than this is split into sequential batch calls rather than one oversized
// request the server would reject.
private const val BATCH_CHUNK_SIZE = 50

/**
 * Refreshes many manga in one (or a few, chunked) network round trip via
 * `POST /manga/batch`, instead of one `GET /manga` per manga -- see
 * [SourceApi.getMangaBatch] for why. Each manga's result is reconciled
 * against local storage through [RefreshMangaChapters.applyResponse], the
 * same logic a single-manga refresh uses.
 */
class RefreshLibraryBatch(
    private val sourceApi: SourceApi,
    private val refreshMangaChapters: RefreshMangaChapters,
) {
    suspend fun refresh(mangas: List<Manga>): Map<Long, Result<List<Chapter>>> {
        if (mangas.isEmpty()) return emptyMap()
        val results = mutableMapOf<Long, Result<List<Chapter>>>()
        for (chunk in mangas.chunked(BATCH_CHUNK_SIZE)) {
            results += refreshChunk(chunk)
        }
        return results
    }

    private suspend fun refreshChunk(mangas: List<Manga>): Map<Long, Result<List<Chapter>>> {
        val batchResults = sourceApi.getMangaBatch(mangas.map { it.url }).getOrElse { error ->
            return mangas.associate { it.id to Result.failure(error) }
        }
        val resultByUrl = batchResults.associateBy { it.url }

        return mangas.associate { manga ->
            val entry = resultByUrl[manga.url]
            val result = when {
                entry == null -> Result.failure(IOException("missing from batch response"))
                entry.error != null -> Result.failure(IOException(entry.error))
                entry.manga != null -> refreshMangaChapters.applyResponse(manga, entry.manga)
                else -> Result.failure(IOException("empty batch result"))
            }
            manga.id to result
        }
    }
}
