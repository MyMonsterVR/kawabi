package com.mymonstervr.kawabi.domain.repository

import com.mymonstervr.kawabi.domain.model.Category

interface CategoryRepository {
    suspend fun ensureDefault()
    suspend fun getAll(): List<Category>
    suspend fun create(name: String, sort: Int): Long
    suspend fun delete(id: Long)
    suspend fun getCategoryIdsForManga(mangaId: Long): List<Long>
    suspend fun setCategoriesForManga(mangaId: Long, categoryIds: List<Long>)
}
