package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.Serializable

// Field names are the backend contract's (PLAN-anime.md section 10) snake_case verbatim --
// no @SerialName anywhere, same convention as every other dto in this package.

@Serializable
data class AnimeSourcesResponse(
    val sources: List<AnimeSourceDto> = emptyList(),
)

@Serializable
data class AnimeSourceDto(
    val key: String,
    val id: String = "",
    val name: String,
    val lang: String = "",
    val enabled: Boolean = true,
    val supports_latest: Boolean = true,
)

@Serializable
data class SetAnimeSourceToggleRequest(
    val key: String,
    val enabled: Boolean,
)

@Serializable
data class AnimeCardDto(
    val key: String,
    val source: String = "",
    val source_name: String = "",
    val url: String = "",
    val title: String,
    val cover_url: String? = null,
    val status: String = "",
)

@Serializable
data class AnimeSearchResponse(
    val results: List<AnimeCardDto> = emptyList(),
)

@Serializable
data class AnimeBrowseResponse(
    val results: List<AnimeCardDto> = emptyList(),
    val has_next_page: Boolean = false,
)

@Serializable
data class AnimeDetailResponse(
    val key: String,
    val source: String = "",
    val source_name: String = "",
    val url: String = "",
    val title: String,
    val cover_url: String? = null,
    val description: String? = null,
    val author: String? = null,
    val status: String = "",
    val genres: List<String> = emptyList(),
    val total_episodes: Double = 0.0,
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
data class EpisodeDto(
    val key: String,
    val url: String = "",
    val number: Double = -1.0,
    val title: String = "",
    val date_upload: Long? = null,
)

@Serializable
data class HosterDto(
    val index: Int,
    val name: String = "",
    val lazy: Boolean = false,
)

@Serializable
data class HosterVideosDto(
    val hoster: HosterDto,
    val videos: List<VideoDto> = emptyList(),
)

@Serializable
data class VideoDto(
    val url: String,
    val title: String = "",
    val resolution: Int = 0,
    val headers: Map<String, String> = emptyMap(),
    val preferred: Boolean = false,
    val subtitles: List<VideoTrackDto> = emptyList(),
    val audio: List<VideoTrackDto> = emptyList(),
    val timestamps: List<VideoTimestampDto> = emptyList(),
    val proxied: Boolean = false,
)

@Serializable
data class VideoTrackDto(
    val url: String,
    val lang: String = "",
)

@Serializable
data class VideoTimestampDto(
    val start: Long = 0,
    val end: Long = 0,
    val name: String = "",
    val type: String = "",
)

@Serializable
data class AnimeEntryDto(
    val key: String,
    val source: String = "",
    val url: String = "",
    val title: String = "",
    val cover_url: String? = null,
    val status: String = "",
    val total_episodes: Double = 0.0,
    val episodes_watched: Double = 0.0,
    val favorite: Boolean = false,
    val last_watched_at: Long? = null,
    val updated_at: Long = 0,
    val deleted_at: Long? = null,
)

@Serializable
data class AnimeEntriesResponse(
    val server_time_ms: Long = 0,
    val entries: List<AnimeEntryDto> = emptyList(),
)

@Serializable
data class AnimeEntriesRequest(val entries: List<AnimeEntryDto>)

@Serializable
data class AnimeProgressDto(
    val anime_key: String,
    val episode_key: String,
    val episode_number: Double = -1.0,
    val position_ms: Long = 0,
    val duration_ms: Long = 0,
    val watched: Boolean = false,
    val updated_at: Long = 0,
)

@Serializable
data class AnimeProgressResponse(val entries: List<AnimeProgressDto> = emptyList())

@Serializable
data class AnimeProgressRequest(val entries: List<AnimeProgressDto>)

@Serializable
data class AnimeOkResponse(val ok: Boolean = false)

@Serializable
data class AnimeBatchRequest(val keys: List<String>)

@Serializable
data class AnimeBatchResponse(
    val animes: List<AnimeDetailResponse> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
)

@Serializable
data class AnimeImportRequest(
    val tracker: String,
    val statuses: List<String>,
    val cursor: Int = 0,
)

@Serializable
data class AnimeImportResultDto(
    val remote_id: String,
    val title: String = "",
    val status: String = "",
    val episodes_watched: Double = 0.0,
    val total_episodes: Double = 0.0,
    val score: Double = 0.0,
    val match: AnimeCardDto? = null,
)

@Serializable
data class AnimeImportResponse(
    val results: List<AnimeImportResultDto> = emptyList(),
    val truncated: Boolean = false,
    val next_cursor: Int? = null,
    val total: Int = 0,
)
