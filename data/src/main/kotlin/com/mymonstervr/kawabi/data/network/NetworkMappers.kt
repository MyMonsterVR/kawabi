package com.mymonstervr.kawabi.data.network

import com.mymonstervr.kawabi.data.network.dto.AnimeDetailResponse
import com.mymonstervr.kawabi.data.network.dto.MangaResponse
import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.model.Manga
import com.mymonstervr.kawabi.domain.model.SourceEpisode

fun MangaResponse.toDomain(): Manga = Manga(
    id = 0,
    source = source,
    siteKey = served_from,
    url = url,
    title = title,
    artist = null,
    author = author,
    description = description,
    genres = genres,
    status = status,
    thumbnailUrl = cover_url,
    favorite = true,
    lastUpdate = null,
    nextUpdate = null,
    initialized = true,
    chapterFlags = 0,
    viewer = 0,
    dateAdded = System.currentTimeMillis(),
    calculateInterval = 0,
    lastModifiedAt = 0,
    version = 0,
    isSyncing = false,
    totalChapters = chapters.size.toDouble(),
    notes = "",
    lastReadAt = 0,
)

fun AnimeDetailResponse.toDomain(): Anime = Anime(
    id = 0,
    source = source,
    key = key,
    url = url,
    title = title,
    author = author,
    description = description,
    genres = genres,
    status = status,
    thumbnailUrl = cover_url,
    favorite = true,
    lastUpdate = null,
    nextUpdate = null,
    initialized = true,
    dateAdded = System.currentTimeMillis(),
    calculateInterval = 0,
    lastModifiedAt = 0,
    version = 0,
    isSyncing = false,
    totalEpisodes = if (total_episodes > 0) total_episodes else episodes.size.toDouble(),
    lastWatchedAt = 0,
)

fun AnimeDetailResponse.toSourceEpisodes(): List<SourceEpisode> = episodes.map { dto ->
    SourceEpisode(
        key = dto.key,
        url = dto.url.ifBlank { dto.key },
        name = dto.title,
        episodeNumber = dto.number,
        dateUpload = dto.date_upload ?: 0L,
    )
}
