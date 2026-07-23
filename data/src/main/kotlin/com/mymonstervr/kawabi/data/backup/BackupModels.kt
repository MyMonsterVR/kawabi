package com.mymonstervr.kawabi.data.backup

import kotlinx.serialization.Serializable

private const val BACKUP_VERSION = 2

@Serializable
data class BackupData(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long,
    val categories: List<BackupCategory>,
    val manga: List<BackupManga>,
)

@Serializable
data class BackupCategory(
    val name: String,
    val sort: Int,
)

@Serializable
data class BackupManga(
    val source: String,
    val siteKey: String?,
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genres: List<String>,
    val status: String,
    val thumbnailUrl: String?,
    val viewer: Int,
    val notes: String,
    val categoryNames: List<String>,
    val chapters: List<BackupChapter>,
    // Default so a v1 backup (no tracks field at all) still decodes.
    val tracks: List<BackupTrack> = emptyList(),
)

// Per-manga tracker *links* only -- account-level OAuth tokens never go in a
// plaintext export file, see BackupManager's class doc.
@Serializable
data class BackupTrack(
    val trackerId: String,
    val remoteId: String,
    val libraryId: String?,
    val title: String,
    val trackingUrl: String,
    val totalChapters: Double,
    val lastChapterRead: Double,
    val score: Double = 0.0,
    val status: String = "reading",
)

@Serializable
data class BackupChapter(
    val url: String,
    val name: String,
    val scanlator: String?,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Int,
    val chapterNumber: Double,
    val sourceOrder: Int,
    val dateUpload: Long,
)
