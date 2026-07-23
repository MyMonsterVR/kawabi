package com.mymonstervr.kawabi.data.repository

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.db.KawabiDatabase
import com.mymonstervr.kawabi.data.db.toDomain
import com.mymonstervr.kawabi.domain.model.Category
import com.mymonstervr.kawabi.domain.repository.CategoryRepository
import kotlinx.coroutines.withContext

class SqlDelightCategoryRepository(
    private val db: KawabiDatabase,
    private val dispatchers: AppDispatchers,
) : CategoryRepository {

    private val categoryQueries = db.categoriesQueries
    private val mangaCategoryQueries = db.mangasCategoriesQueries

    override suspend fun ensureDefault(): Unit = withContext<Unit>(dispatchers.io) {
        categoryQueries.insertDefault()
    }

    override suspend fun getAll(): List<Category> = withContext(dispatchers.io) {
        categoryQueries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun create(name: String, sort: Int): Long = withContext(dispatchers.io) {
        db.transactionWithResult {
            categoryQueries.insertCategory(name, sort.toLong(), 0L)
            categoryQueries.lastInsertCategoryRowId().executeAsOne()
        }
    }

    override suspend fun delete(id: Long): Unit = withContext<Unit>(dispatchers.io) {
        categoryQueries.deleteCategoryById(id)
    }

    override suspend fun getCategoryIdsForManga(mangaId: Long): List<Long> = withContext(dispatchers.io) {
        mangaCategoryQueries.selectCategoryIdsByManga(mangaId).executeAsList()
    }

    override suspend fun setCategoriesForManga(mangaId: Long, categoryIds: List<Long>) = withContext(dispatchers.io) {
        db.transaction {
            mangaCategoryQueries.deleteMangaCategoriesByManga(mangaId)
            categoryIds.forEach { categoryId -> mangaCategoryQueries.insertMangaCategory(mangaId, categoryId) }
        }
    }
}
