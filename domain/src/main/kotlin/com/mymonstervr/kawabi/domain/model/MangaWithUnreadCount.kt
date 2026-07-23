package com.mymonstervr.kawabi.domain.model

data class MangaWithUnreadCount(
    val manga: Manga,
    val unreadCount: Int,
    // Null when nothing's been read yet. Queried directly (MAX chapter_number where
    // read=1) rather than derived as totalChapters - unreadCount -- that arithmetic
    // silently breaks whenever the local chapter list isn't perfectly gapless/contiguous
    // (a source with skipped numbers, extras, or a source switch that changes numbering).
    val lastReadChapterNumber: Double?,
)
