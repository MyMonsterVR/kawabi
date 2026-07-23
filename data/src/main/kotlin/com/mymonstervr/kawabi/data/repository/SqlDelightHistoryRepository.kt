package com.mymonstervr.kawabi.data.repository

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.db.KawabiDatabase
import com.mymonstervr.kawabi.data.db.toDomain
import com.mymonstervr.kawabi.domain.model.History
import com.mymonstervr.kawabi.domain.repository.HistoryRepository
import kotlinx.coroutines.withContext

class SqlDelightHistoryRepository(
    private val db: KawabiDatabase,
    private val dispatchers: AppDispatchers,
) : HistoryRepository {

    private val queries = db.historyQueries

    override suspend fun recordRead(chapterId: Long, lastRead: Long, timeReadDeltaMs: Long) = withContext(dispatchers.io) {
        db.transaction {
            val existing = queries.selectByChapter(chapterId).executeAsOneOrNull()
            if (existing != null) {
                queries.updateHistory(lastRead, timeReadDeltaMs, chapterId)
            } else {
                queries.insertHistory(chapterId, lastRead, timeReadDeltaMs)
            }
        }
    }

    override suspend fun getForChapter(chapterId: Long): History? = withContext(dispatchers.io) {
        queries.selectByChapter(chapterId).executeAsOneOrNull()?.toDomain()
    }
}
