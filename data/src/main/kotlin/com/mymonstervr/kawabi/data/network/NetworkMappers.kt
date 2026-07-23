package com.mymonstervr.kawabi.data.network

import com.mymonstervr.kawabi.data.network.dto.MangaResponse
import com.mymonstervr.kawabi.domain.model.Manga

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
