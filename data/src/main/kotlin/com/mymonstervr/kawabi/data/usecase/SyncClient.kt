package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.SyncApi
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.network.dto.EntryDto
import com.mymonstervr.kawabi.data.network.dto.ProgressDto
import com.mymonstervr.kawabi.domain.repository.ChapterRepository
import com.mymonstervr.kawabi.domain.repository.MangaRepository
import com.mymonstervr.kawabi.domain.util.nativeRemoteId

/**
 * Login-gated sync client (PLAN.md step 7): push every favorited manga's aggregate +
 * per-chapter progress, then pull the server's view and merge it back in.
 *
 * v1 simplification, documented rather than silently assumed: pushes the *full* current
 * state of every favorite on each call rather than tracking a per-manga watermark of
 * what's already been synced. The backend's upserts are idempotent, so this is correct,
 * just not bandwidth-optimal -- acceptable for a personal-scale library, revisit if it
 * ever needs trimming (step 9's background job would be the natural place to add a
 * watermark column and only push rows whose `version` advanced since last sync).
 */
class SyncClient(
    private val syncApi: SyncApi,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val addMangaToLibrary: AddMangaToLibrary,
    private val tokenStore: TokenStore,
) {
    suspend fun sync(): Result<Unit> = runCatching {
        if (!tokenStore.isLoggedIn.value) return@runCatching
        push()
        pull()
    }

    // Batched into one call each, not one call per manga -- this used to POST /entries
    // (and /progress) individually per favorite, which for a library of any real size
    // tripped the backend's rate limiter partway through. Once that happened, the
    // *following* GET /entries in pull() got 429'd too and aborted immediately via
    // getOrElse { return }, silently dropping the entire pull for that sync pass (found
    // by reading raw OkHttp logs: 28 sequential POST /entries calls, 429s starting around
    // #20, then the pull's own GET /entries also 429'd). Both endpoints already accept a
    // batch (`entries: [...]`), so there was never a reason to split them per-manga.
    private suspend fun push() {
        val favorites = mangaRepository.getFavorites()
        val entries = favorites.map { manga ->
            val chaptersRead = chapterRepository.getForManga(manga.id)
                .filter { it.read }
                .maxOfOrNull { it.chapterNumber } ?: 0.0
            EntryDto(
                remote_id = nativeRemoteId(manga.url),
                title = manga.title,
                chapters_read = chaptersRead,
                source_url = manga.url,
                last_synced_chapters = chaptersRead,
                cover_url = manga.thumbnailUrl,
                manga_key = manga.url,
                last_read_at = manga.lastReadAt.takeIf { it > 0 },
            )
        }
        if (entries.isNotEmpty()) syncApi.postEntries(entries)

        val progress = favorites.flatMap { manga ->
            chapterRepository.getForManga(manga.id)
                .filter { it.read || it.lastPageRead > 0 }
                .map { chapter ->
                    ProgressDto(
                        source_url = manga.url,
                        chapter_id = chapter.url,
                        chapter_number = chapter.chapterNumber,
                        last_page_read = chapter.lastPageRead,
                        read = chapter.read,
                    )
                }
        }
        if (progress.isNotEmpty()) syncApi.postProgress(progress)
    }

    private suspend fun pull() {
        val response = syncApi.getEntries().getOrElse { return }
        val mangaIdByUrl = mutableMapOf<String, Long>()
        for (entry in response.entries) {
            if (entry.deleted_at != null) continue
            val url = entry.source_url ?: entry.manga_key ?: continue

            val manga = mangaRepository.getByUrl(url)
                ?: addMangaToLibrary.add(url).getOrNull()
                ?: continue

            if (entry.chapters_read > 0) {
                chapterRepository.markReadUpToNumber(manga.id, entry.chapters_read)
            }
            entry.last_read_at?.let { mangaRepository.touchLastRead(manga.id, it) }
            mangaIdByUrl[url] = manga.id
        }
        applyAllProgress(mangaIdByUrl)
    }

    // Batched into one GET /progress call (no ?url=) covering every manga, instead of
    // one call per favorite -- same rate-limit-abort bug push() was already fixed for
    // (see its comment): per-manga pulls tripped the backend's limiter on any real-size
    // library.
    //
    // Asymmetric safety: never let a pulled state regress progress the device already has
    // further along. The backend's per-chapter `read_progress` table is plain
    // last-write-wins by `updated_at` (flagged in PLAN.md's Sync layer section as
    // inconsistent with the monotonic-max rule everywhere else) -- a stale/wrong device
    // clock could otherwise push a real reader backward. Only ever raise `read`/
    // `last_page_read`, never lower them.
    private suspend fun applyAllProgress(mangaIdByUrl: Map<String, Long>) {
        val response = syncApi.getAllProgress().getOrElse { return }
        for (progress in response.entries) {
            val mangaId = mangaIdByUrl[progress.source_url] ?: continue
            val local = chapterRepository.getByMangaAndUrl(mangaId, progress.chapter_id) ?: continue
            val nextRead = local.read || progress.read
            val nextLastPage = maxOf(local.lastPageRead, progress.last_page_read)
            if (nextRead != local.read || nextLastPage != local.lastPageRead) {
                chapterRepository.setProgress(local.id, nextRead, nextLastPage)
            }
        }
    }
}
