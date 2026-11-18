package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.SerialName
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
    val adult: Boolean = false,
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
    @SerialName("display_title") val displayTitle: String? = null,
    @SerialName("canonical_id") val canonicalId: Long? = null,
    val cover_url: String? = null,
    val status: String = "",
    @SerialName("full_title") val fullTitle: String? = null,
    @SerialName("cover_fallback_url") val coverFallbackUrl: String? = null,
    // "sub", "dub" or "". Only trustworthy when audioKnown: an empty value is then a real
    // answer (checked, the source has no tag) rather than "not looked up yet".
    val audio: String? = null,
    @SerialName("audio_known") val audioKnown: Boolean = false,
    @SerialName("dub_since") val dubSince: Long? = null,
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
    @SerialName("display_title") val displayTitle: String? = null,
    @SerialName("full_title") val fullTitle: String? = null,
    @SerialName("canonical_id") val canonicalId: Long? = null,
    @SerialName("cover_fallback_url") val coverFallbackUrl: String? = null,
    val cover_url: String? = null,
    val description: String? = null,
    val author: String? = null,
    val status: String = "",
    val genres: List<String> = emptyList(),
    val total_episodes: Double = 0.0,
    val episodes: List<EpisodeDto> = emptyList(),
    @SerialName("next_episode") val nextEpisode: NextEpisodeDto? = null,
)

@Serializable
data class NextEpisodeDto(
    val episode: Int,
    @SerialName("airs_at") val airsAt: Long,
)

@Serializable
data class EpisodeDto(
    val key: String,
    val url: String = "",
    val number: Double = -1.0,
    val title: String = "",
    val date_upload: Long? = null,
    val scanlator: String? = null,
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
    val proxy_url: String? = null,
)

@Serializable
data class VideoTrackDto(
    val url: String,
    val lang: String = "",
    val proxy_url: String? = null,
)

@Serializable
data class VideoTimestampDto(
    val start: Double = 0.0,
    val end: Double = 0.0,
    val name: String = "",
    val type: String = "",
)

@Serializable
data class AnimeEntryDto(
    val key: String,
    val source: String = "",
    val url: String = "",
    val title: String = "",
    @SerialName("display_title") val displayTitle: String? = null,
    val cover_url: String? = null,
    val status: String = "",
    val total_episodes: Double = 0.0,
    val episodes_watched: Double = 0.0,
    val favorite: Boolean = false,
    val last_watched_at: Long? = null,
    val updated_at: Long = 0,
    val deleted_at: Long? = null,
    val tracks: List<AnimeTrackDto> = emptyList(),
    val adult: Boolean = false,
)

@Serializable
data class AnimeTrackDto(
    val tracker: String,
    val remote_id: String = "",
    val status: String = "",
    val progress: Double = 0.0,
    val total: Double = 0.0,
    val score: Double = 0.0,
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
    val tracker_updated_at: Long = 0,
    val match: AnimeCardDto? = null,
)

@Serializable
data class AnimeImportResponse(
    val results: List<AnimeImportResultDto> = emptyList(),
    val truncated: Boolean = false,
    val next_cursor: Int? = null,
    val total: Int = 0,
)

@Serializable
data class AnimeAudioResponse(val audio: String = "")

@Serializable
data class AnimeSeasonDto(
    val id: Long = 0,
    val title: String = "",
    val english: String? = null,
    val romaji: String? = null,
    val alt_titles: List<String> = emptyList(),
    val cover_url: String? = null,
    val year: Int? = null,
    val format: String? = null,
    val current: Boolean = false,
)

@Serializable
data class AnimeSeasonsResponse(val seasons: List<AnimeSeasonDto> = emptyList())

// Episode numbers, not keys.
@Serializable
data class AnimeFillersResponse(
    val filler: List<Double> = emptyList(),
    val mixed: List<Double> = emptyList(),
)

@Serializable
data class AnimeFeedEpisodeDto(
    val anime_key: String,
    val anime_title: String = "",
    @SerialName("display_title") val displayTitle: String? = null,
    val cover_url: String? = null,
    @SerialName("cover_fallback_url") val coverFallbackUrl: String? = null,
    val source: String = "",
    val episode_key: String,
    val number: Double = -1.0,
    val title: String = "",
    val date_upload: Long = 0,
)

@Serializable
data class AnimeFeedResponse(
    val episodes: List<AnimeFeedEpisodeDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class AnimeLinkCandidateDto(
    val anilist_id: Long = 0,
    val mal_id: Long = 0,
    val title: String = "",
    val format: String = "",
    val year: Int = 0,
    val episodes: Int = 0,
    val cover_url: String? = null,
)

@Serializable
data class AnimeLinkSuggestionsResponse(val candidates: List<AnimeLinkCandidateDto> = emptyList())

@Serializable
data class AnimeLinkResolveRequest(val key: String, val anilist_id: Long)
