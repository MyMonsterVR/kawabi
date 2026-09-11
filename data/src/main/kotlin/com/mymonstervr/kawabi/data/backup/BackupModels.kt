package com.mymonstervr.kawabi.data.backup

import kotlinx.serialization.Serializable

private const val BACKUP_VERSION = 3

@Serializable
data class BackupData(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long,
    val categories: List<BackupCategory>,
    val manga: List<BackupManga>,
    // Defaulted so a v2 (or v1) file -- which has no anime key at all -- still decodes
    // and restores its manga library exactly as before.
    val anime: List<BackupAnime> = emptyList(),
)

@Serializable
data class BackupAnime(
    val source: String,
    val key: String,
    val url: String,
    val title: String,
    val author: String?,
    val description: String?,
    val genres: List<String>,
    val status: String,
    val thumbnailUrl: String?,
    val episodes: List<BackupEpisode> = emptyList(),
    val tracks: List<BackupAnimeTrack> = emptyList(),
)

@Serializable
data class BackupEpisode(
    val key: String,
    val url: String,
    val name: String,
    val watched: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val episodeNumber: Double,
    val sourceOrder: Int,
    val dateUpload: Long,
)

@Serializable
data class BackupAnimeTrack(
    val trackerId: String,
    val remoteId: String,
    val libraryId: String?,
    val title: String,
    val trackingUrl: String,
    val totalEpisodes: Double,
    val lastEpisodeWatched: Double,
    val score: Double = 0.0,
    val status: String = "watching",
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
