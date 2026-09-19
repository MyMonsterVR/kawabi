package com.mymonstervr.kawabi.domain.model

data class Chapter(
    val id: Long,
    val mangaId: Long,
    val url: String,
    val name: String,
    val scanlator: String?,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Int,
    val chapterNumber: Double,
    val sourceOrder: Int,
    val dateUpload: Long,
    val dateFetch: Long,
    val lastModifiedAt: Long,
    val version: Long,
    val isSyncing: Boolean,
)

/** One existing chapter's changed fields, for [ChapterRepository.applySync]. */
data class ChapterUpdate(
    val id: Long,
    val name: String,
    val scanlator: String?,
    val chapterNumber: Double,
    val sourceOrder: Int,
    val dateUpload: Long,
)
