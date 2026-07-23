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
