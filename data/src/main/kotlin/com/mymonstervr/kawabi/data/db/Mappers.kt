package com.mymonstervr.kawabi.data.db

import com.mymonstervr.kawabi.domain.model.Category as DomainCategory
import com.mymonstervr.kawabi.domain.model.Chapter as DomainChapter
import com.mymonstervr.kawabi.domain.model.History as DomainHistory
import com.mymonstervr.kawabi.domain.model.Manga as DomainManga
import com.mymonstervr.kawabi.domain.model.MangaWithUnreadCount as DomainMangaWithUnreadCount
import com.mymonstervr.kawabi.domain.model.Track as DomainTrack

private fun mangaFields(
    id: Long,
    source: String,
    siteKey: String?,
    url: String,
    title: String,
    artist: String?,
    author: String?,
    description: String?,
    genre: List<String>?,
    status: String,
    thumbnailUrl: String?,
    favorite: Boolean,
    lastUpdate: Long?,
    nextUpdate: Long?,
    initialized: Boolean,
    chapterFlags: Long,
    viewer: Long,
    dateAdded: Long,
    calculateInterval: Long,
    lastModifiedAt: Long,
    version: Long,
    isSyncing: Boolean,
    totalChapters: Double,
    notes: String,
    lastReadAt: Long,
) = DomainManga(
    id = id,
    source = source,
    siteKey = siteKey,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genres = genre.orEmpty(),
    status = status,
    thumbnailUrl = thumbnailUrl,
    favorite = favorite,
    lastUpdate = lastUpdate,
    nextUpdate = nextUpdate,
    initialized = initialized,
    chapterFlags = chapterFlags.toInt(),
    viewer = viewer.toInt(),
    dateAdded = dateAdded,
    calculateInterval = calculateInterval.toInt(),
    lastModifiedAt = lastModifiedAt,
    version = version,
    isSyncing = isSyncing,
    totalChapters = totalChapters,
    notes = notes,
    lastReadAt = lastReadAt,
)

fun Mangas.toDomain() = mangaFields(
    id = _id, source = source, siteKey = site_key, url = url, title = title, artist = artist,
    author = author, description = description, genre = genre, status = status,
    thumbnailUrl = thumbnail_url, favorite = favorite, lastUpdate = last_update,
    nextUpdate = next_update, initialized = initialized, chapterFlags = chapter_flags,
    viewer = viewer, dateAdded = date_added, calculateInterval = calculate_interval,
    lastModifiedAt = last_modified_at, version = version, isSyncing = is_syncing,
    totalChapters = total_chapters, notes = notes, lastReadAt = last_read_at,
)

fun SelectFavoritesWithUnreadCount.toDomain() = DomainMangaWithUnreadCount(
    manga = mangaFields(
        id = _id, source = source, siteKey = site_key, url = url, title = title, artist = artist,
        author = author, description = description, genre = genre, status = status,
        thumbnailUrl = thumbnail_url, favorite = favorite, lastUpdate = last_update,
        nextUpdate = next_update, initialized = initialized, chapterFlags = chapter_flags,
        viewer = viewer, dateAdded = date_added, calculateInterval = calculate_interval,
        lastModifiedAt = last_modified_at, version = version, isSyncing = is_syncing,
        totalChapters = total_chapters, notes = notes, lastReadAt = last_read_at,
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
