package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeDetailResponse
import com.mymonstervr.kawabi.data.network.toDomain
import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.repository.AnimeRepository

class AddAnimeToLibrary(
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val refreshAnimeEpisodes: RefreshAnimeEpisodes,
    private val identityMatcher: AnimeIdentityMatcher,
) {
    suspend fun add(key: String, cardCover: String? = null, malId: String? = null): Result<Anime> {
        val response = animeApi.getAnime(key).getOrElse { return Result.failure(it) }
        return addWithDetail(response, cardCover, malId)
    }

    /**
     * Same as [add], but for a detail payload the caller already fetched (e.g. a batch import).
     * [cardCover] is the cover of the search/browse/import card the caller came from: engine
     * details pages frequently carry no thumbnail at all, and an empty cover must never win
     * over one we already know (stored row first, then the card).
     *
     * When the same show is already a favorite under another source's key, that row is
     * returned untouched instead of a second favorite being created (PLAN-anime.md section
     * 18) -- the caller can tell by comparing [Anime.key] with the key it asked for, and
     * offer a source switch rather than a duplicate.
     */
    suspend fun addWithDetail(
        response: AnimeDetailResponse,
        cardCover: String? = null,
        malId: String? = null,
    ): Result<Anime> = runCatching {
        identityMatcher.findFavorite(response.title, malId, excludeKey = response.key)
            ?.let { return@runCatching it }
        val stored = persist(response, cardCover, favorite = true)
        animeRepository.setFavorite(stored.id, true)
        refreshAnimeEpisodes.applyResponse(stored, response)
        stored
    }

    /**
     * Stores the anime and its episodes without touching library membership -- what the
     * details screen needs before rendering, since playback and watch marks both need a
     * local episode row to write into even for a show the user hasn't added yet. Fails if
     * the episode list can't be stored, so the caller can say so instead of showing a list
     * where nothing is tappable.
     */
    suspend fun cache(response: AnimeDetailResponse, cardCover: String? = null): Result<Anime> = runCatching {
        val existing = animeRepository.getByKey(response.key)
        val stored = if (existing != null) {
            persist(response, cardCover, favorite = existing.favorite)
        } else {
            // No row at this exact key -- but if a non-favorite row for the same show
            // already exists under another key, reuse it instead of caching a second row
            // (PLAN-anime.md section 18 follow-up). A favorite match is left alone: that
            // case is surfaced as the library-match banner instead, which creates its own
            // temporary row for the source being previewed until the user decides.
            val identityMatch = identityMatcher.findAny(response.title, excludeKey = response.key)
            if (identityMatch != null && !identityMatch.favorite) {
                reuse(identityMatch, response, cardCover)
            } else {
                persist(response, cardCover, favorite = false)
            }
        }
        refreshAnimeEpisodes.applyResponse(stored, response).getOrThrow()
        stored
    }

    private suspend fun reuse(existing: Anime, response: AnimeDetailResponse, cardCover: String?): Anime {
        val fetched = response.toDomain()
        val cover = fetched.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: cardCover?.takeIf { it.isNotBlank() }
            ?: existing.thumbnailUrl
        animeRepository.switchSource(
            animeId = existing.id,
            newKey = response.key,
            newSource = response.source,
            newUrl = response.url,
            newTitle = response.title.takeIf { it.isNotBlank() },
            cover = cover,
        )
        return existing.copy(
            key = response.key,
            source = response.source,
            url = response.url,
            title = response.title.ifBlank { existing.title },
            thumbnailUrl = cover,
        )
    }

    private suspend fun persist(response: AnimeDetailResponse, cardCover: String?, favorite: Boolean): Anime {
        val fetched = response.toDomain()
        val anime = fetched.copy(
            favorite = favorite,
            thumbnailUrl = fetched.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?: cardCover?.takeIf { it.isNotBlank() },
        )
        val id = animeRepository.upsert(anime)
        anime.thumbnailUrl?.let { animeRepository.fillMissingThumbnail(id, it) }
        return anime.copy(id = id)
    }
}
