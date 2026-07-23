package com.mymonstervr.kawabi.domain.repository

import com.mymonstervr.kawabi.domain.model.History

interface HistoryRepository {
    suspend fun recordRead(chapterId: Long, lastRead: Long, timeReadDeltaMs: Long)
    suspend fun getForChapter(chapterId: Long): History?
}
