package com.mymonstervr.kawabi.data.track.dto

import kotlinx.serialization.Serializable

// Trimmed to what title-search + chapter-read linking needs. Kitsu's JSON:API
// attribute keys are already camelCase, so no @SerialName mapping is needed.

@Serializable
data class KitsuPosterImage(val original: String? = null)

@Serializable
data class KitsuMangaAttributes(
    val canonicalTitle: String = "",
    val chapterCount: Int? = null,
    val posterImage: KitsuPosterImage? = null,
)

@Serializable
data class KitsuMangaResource(val id: String, val attributes: KitsuMangaAttributes)

@Serializable
data class KitsuMangaSearchResult(val data: List<KitsuMangaResource> = emptyList())

@Serializable
data class KitsuLibraryEntryAttributes(
    val progress: Int = 0,
    val status: String = "current",
    val ratingTwenty: Int? = null,
)

@Serializable
data class KitsuLibraryEntryResource(val id: String, val attributes: KitsuLibraryEntryAttributes)

@Serializable
data class KitsuLibraryEntrySearchResult(
    val data: List<KitsuLibraryEntryResource> = emptyList(),
    val included: List<KitsuMangaResource> = emptyList(),
)

@Serializable
data class KitsuAddMangaData(val id: String)

@Serializable
data class KitsuAddMangaResult(val data: KitsuAddMangaData)
