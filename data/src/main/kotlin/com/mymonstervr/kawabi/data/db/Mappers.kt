package com.mymonstervr.kawabi.data.db

import com.mymonstervr.kawabi.domain.model.Category as DomainCategory
import com.mymonstervr.kawabi.domain.model.Chapter as DomainChapter
import com.mymonstervr.kawabi.domain.model.History as DomainHistory
import com.mymonstervr.kawabi.domain.model.Manga as DomainManga
import com.mymonstervr.kawabi.domain.model.MangaWithUnreadCount as DomainMangaWithUnreadCount
import com.mymonstervr.kawabi.domain.model.Track as DomainTrack

fun Mangas.toDomain() = DomainManga(
    id = _id,
    source = source,
    siteKey = site_key,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genres = genre.orEmpty(),
    status = status,
    thumbnailUrl = thumbnail_url,
    favorite = favorite,
    lastUpdate = last_update,
    nextUpdate = next_update,
    initialized = initialized,
    chapterFlags = chapter_flags.toInt(),
    viewer = viewer.toInt(),
    dateAdded = date_added,
    calculateInterval = calculate_interval.toInt(),
    lastModifiedAt = last_modified_at,
    version = version,
    isSyncing = is_syncing,
    totalChapters = total_chapters,
    notes = notes,
    lastReadAt = last_read_at,
)

fun SelectFavoritesWithUnreadCount.toDomain() = DomainMangaWithUnreadCount(
    manga = DomainManga(
        id = _id,
        source = source,
        siteKey = site_key,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genres = genre.orEmpty(),
        status = status,
        thumbnailUrl = thumbnail_url,
        favorite = favorite,
        lastUpdate = last_update,
        nextUpdate = next_update,
        initialized = initialized,
        chapterFlags = chapter_flags.toInt(),
        viewer = viewer.toInt(),
        dateAdded = date_added,
        calculateInterval = calculate_interval.toInt(),
        lastModifiedAt = last_modified_at,
        version = version,
        isSyncing = is_syncing,
        totalChapters = total_chapters,
        notes = notes,
        lastReadAt = last_read_at,
    ),
    unreadCount = unread_count.toInt(),
    lastReadChapterNumber = last_read_chapter,
)

fun Chapters.toDomain() = DomainChapter(
    id = _id,
    mangaId = manga_id,
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = last_page_read.toInt(),
    chapterNumber = chapter_number,
    sourceOrder = source_order.toInt(),
    dateUpload = date_upload,
    dateFetch = date_fetch,
    lastModifiedAt = last_modified_at,
    version = version,
    isSyncing = is_syncing,
)

fun Categories.toDomain() = DomainCategory(
    id = _id,
    name = name,
    sort = sort.toInt(),
    flags = flags.toInt(),
)

fun History.toDomain() = DomainHistory(
    id = _id,
    chapterId = chapter_id,
    lastRead = last_read,
    timeRead = time_read,
)

fun Tracks.toDomain() = DomainTrack(
    id = _id,
    mangaId = manga_id,
    trackerId = tracker_id,
    remoteId = remote_id,
    libraryId = library_id,
    title = title,
    trackingUrl = tracking_url,
    totalChapters = total_chapters,
    lastChapterRead = last_chapter_read,
    score = score,
    status = status,
)
