package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.AnimeTrackRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository

/**
 * Drops one anime row and everything hanging off it. Episodes and tracks are removed
 * explicitly rather than relying on the FK cascades -- Android's SQLite only enforces
 * those when `foreign_keys` is on for the connection, which isn't something this path
 * should depend on.
 */
internal suspend fun deleteAnimeRow(
    animeRepository: AnimeRepository,
    episodeRepository: EpisodeRepository,
    animeTrackRepository: AnimeTrackRepository,
    animeId: Long,
) {
    for (track in animeTrackRepository.getForAnime(animeId)) {
        animeTrackRepository.unlink(animeId, track.trackerId)
    }
    episodeRepository.deleteForAnime(animeId)
    animeRepository.delete(animeId)
}
